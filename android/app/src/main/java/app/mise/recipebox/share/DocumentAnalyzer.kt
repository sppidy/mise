// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

data class AnalyzedDocument(val text: String, val titleHint: String, val previewImageUrl: String? = null)
internal data class PositionedDocumentText(val text: String, val top: Float, val left: Float)
private data class PdfDocumentContent(val text: String, val previewImageUrl: String?)
private data class DishRegion(val left: Int, val top: Int, val width: Int, val height: Int, val score: Double)

class DocumentAnalyzer(private val context: Context) {
    suspend fun analyze(uri: Uri): AnalyzedDocument = withContext(Dispatchers.IO) {
        val metadata = documentMetadata(uri)
        require(metadata.size <= maxDocumentBytes || metadata.size < 0) { "Choose a document smaller than 15 MB." }
        val kind = documentKind(metadata.mimeType, metadata.name)
        val content = when (kind) {
            DocumentKind.TEXT -> PdfDocumentContent(
                context.contentResolver.openInputStream(uri)?.use { input -> readBounded(input, maxTextBytes).toString(Charsets.UTF_8) }
                    ?: error("Mise could not open that document."),
                null,
            )
            DocumentKind.DOCX -> PdfDocumentContent(
                context.contentResolver.openInputStream(uri)?.use(::readDocx) ?: error("Mise could not open that document."),
                null,
            )
            DocumentKind.PDF -> readPdf(uri)
        }
        val text = content.text.replace('\u0000', ' ').trim()
        if (text.length < 20) {
            deletePreviewImage(content.previewImageUrl)
            error("Not enough recipe text was found in that document.")
        }
        AnalyzedDocument(text, titleHint(metadata.name), content.previewImageUrl)
    }

    private fun documentMetadata(uri: Uri): DocumentMetadata {
        var name = ""
        var size = -1L
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                name = cursor.getString(0).orEmpty()
                if (!cursor.isNull(1)) size = cursor.getLong(1)
            }
        }
        return DocumentMetadata(name, context.contentResolver.getType(uri).orEmpty(), size)
    }

    private fun readDocx(input: InputStream): String {
        ZipInputStream(input.buffered()).use { zip ->
            repeat(maxZipEntries) {
                val entry = zip.nextEntry ?: return@repeat
                if (entry.name == "word/document.xml") {
                    require(entry.size <= maxTextBytes || entry.size < 0) { "That Word document contains too much text." }
                    return docxXmlToText(readBounded(zip, maxTextBytes).toString(Charsets.UTF_8))
                }
                zip.closeEntry()
            }
        }
        error("That Word document does not contain readable text.")
    }

    private suspend fun readPdf(uri: Uri): PdfDocumentContent {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Mise could not open that PDF.")
        descriptor.use { file ->
            require(file.statSize <= maxDocumentBytes || file.statSize < 0) { "Choose a document smaller than 15 MB." }
            PdfRenderer(file).use { renderer ->
                require(renderer.pageCount > 0) { "That PDF has no pages." }
                var recognizer: com.google.mlkit.vision.text.TextRecognizer? = null
                var bestPreview: Bitmap? = null
                try {
                    val text = StringBuilder()
                    var bestPreviewScore = minimumDishScore
                    val pageLimit = min(renderer.pageCount, maxPdfPages)
                    for (index in 0 until pageLimit) {
                        renderer.openPage(index).use { page ->
                            val embeddedText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                                runCatching {
                                    orderedPageText(page.textContents.map { content ->
                                        val top = content.bounds.minOfOrNull { it.top } ?: Float.MAX_VALUE
                                        val left = content.bounds.filter { abs(it.top - top) < sameLineTolerance }
                                            .minOfOrNull { it.left } ?: Float.MAX_VALUE
                                        PositionedDocumentText(content.text, top, left)
                                    })
                                }.getOrDefault("")
                            } else ""
                            val needsOcr = embeddedText.count(Char::isLetter) < minEmbeddedTextLetters
                            val targetWidth = if (needsOcr) maxPdfRenderWidth else maxPreviewRenderWidth
                            val scale = min(2f, targetWidth.toFloat() / page.width.coerceAtLeast(1)).coerceAtLeast(1f)
                            val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
                            try {
                                bitmap.eraseColor(android.graphics.Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val pageText = if (needsOcr) {
                                    recognize(
                                        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it },
                                        InputImage.fromBitmap(bitmap, 0),
                                    ).trim()
                                } else embeddedText.trim()
                                if (pageText.isNotBlank()) text.append(pageText).append('\n').append(pdfPageBreak).append('\n')
                                bestDishRegion(bitmap)?.let { region ->
                                    val recipeBonus = when {
                                        pageText.contains(Regex("(?i)\\bingredients?\\b")) && pageText.contains(Regex("(?i)\\bdirections?|method|instructions?\\b")) -> .55
                                        pageText.contains(Regex("(?i)\\bingredients?\\b")) -> .3
                                        else -> 0.0
                                    }
                                    val candidateScore = region.score + recipeBonus
                                    if (candidateScore > bestPreviewScore) {
                                        bestPreview?.recycle()
                                        bestPreview = Bitmap.createBitmap(bitmap, region.left, region.top, region.width, region.height)
                                        bestPreviewScore = candidateScore
                                    }
                                }
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    val selectedPreview = bestPreview
                    bestPreview = null
                    val previewImageUrl = selectedPreview?.let { preview ->
                        try {
                            savePreviewImage(preview, uri)
                        } finally {
                            preview.recycle()
                        }
                    }
                    return PdfDocumentContent(text.toString(), previewImageUrl)
                } finally {
                    bestPreview?.recycle()
                    recognizer?.close()
                }
            }
        }
    }

    private suspend fun recognize(
        recognizer: com.google.mlkit.vision.text.TextRecognizer,
        image: InputImage,
    ): String = suspendCancellableCoroutine { continuation ->
        recognizer.process(image)
            .addOnSuccessListener { result -> if (continuation.isActive) continuation.resume(result.text) }
            .addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    }

    private fun bestDishRegion(bitmap: Bitmap): DishRegion? {
        if (bitmap.width < 80 || bitmap.height < 80) return null
        val cropWidth = (bitmap.width * previewCropWidth).toInt().coerceAtLeast(1)
        val cropHeight = min(bitmap.height, (cropWidth / previewAspectRatio).toInt()).coerceAtLeast(1)
        val left = ((bitmap.width - cropWidth) / 2).coerceAtLeast(0)
        return previewCropCenters.map { center ->
            val top = (bitmap.height * center - cropHeight / 2f).toInt().coerceIn(0, (bitmap.height - cropHeight).coerceAtLeast(0))
            DishRegion(left, top, cropWidth, cropHeight, scoreImageRegion(bitmap, left, top, cropWidth, cropHeight))
        }.maxByOrNull(DishRegion::score)
    }

    private fun scoreImageRegion(bitmap: Bitmap, left: Int, top: Int, width: Int, height: Int): Double {
        val stepX = (width / previewSampleColumns).coerceAtLeast(1)
        val stepY = (height / previewSampleRows).coerceAtLeast(1)
        var total = 0
        var colorful = 0
        var nearWhite = 0
        var luminanceSum = 0.0
        var luminanceSquaredSum = 0.0
        var variationSum = 0.0
        var variationCount = 0
        var y = top
        while (y < top + height) {
            var previousRed = -1
            var previousGreen = -1
            var previousBlue = -1
            var x = left
            while (x < left + width) {
                val color = bitmap.getPixel(x.coerceAtMost(bitmap.width - 1), y.coerceAtMost(bitmap.height - 1))
                val red = android.graphics.Color.red(color)
                val green = android.graphics.Color.green(color)
                val blue = android.graphics.Color.blue(color)
                val maximum = maxOf(red, green, blue)
                val minimum = minOf(red, green, blue)
                val luminance = (red * 299 + green * 587 + blue * 114) / 1000.0
                val saturation = if (maximum == 0) 0.0 else (maximum - minimum).toDouble() / maximum
                if (saturation > colorfulSaturation && luminance in 18.0..244.0) colorful++
                if (luminance > 242 && saturation < .08) nearWhite++
                luminanceSum += luminance
                luminanceSquaredSum += luminance * luminance
                if (previousRed >= 0) {
                    variationSum += (abs(red - previousRed) + abs(green - previousGreen) + abs(blue - previousBlue)) / 3.0
                    variationCount++
                }
                previousRed = red
                previousGreen = green
                previousBlue = blue
                total++
                x += stepX
            }
            y += stepY
        }
        if (total == 0) return 0.0
        val mean = luminanceSum / total
        val deviation = sqrt((luminanceSquaredSum / total - mean * mean).coerceAtLeast(0.0))
        return colorful.toDouble() / total * 1.2 + deviation / 128.0 * .8 +
            variationSum / variationCount.coerceAtLeast(1) / 255.0 * .8 - nearWhite.toDouble() / total * .35
    }

    private fun savePreviewImage(bitmap: Bitmap, uri: Uri): String? {
        val directory = File(context.cacheDir, "recipe-previews").apply { mkdirs() }
        val file = File(directory, "pdf-${System.currentTimeMillis()}-${uri.hashCode().toUInt()}.jpg")
        return runCatching {
            file.outputStream().buffered().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, previewJpegQuality, output)) { "Could not create a PDF preview image." }
            }
            file.toURI().toString()
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun deletePreviewImage(value: String?) {
        val uri = value?.let(Uri::parse) ?: return
        if (uri.scheme != "file") return
        val previewDirectory = File(context.cacheDir, "recipe-previews").canonicalFile
        val candidate = runCatching { File(uri.path.orEmpty()).canonicalFile }.getOrNull() ?: return
        if (candidate.parentFile == previewDirectory) candidate.delete()
    }

    companion object {
        const val maxDocumentBytes = 15_000_000L
        private const val maxTextBytes = 2_000_000
        private const val maxZipEntries = 100
        private const val maxPdfPages = 20
        private const val maxPdfRenderWidth = 1600
        private const val maxPreviewRenderWidth = 1000
        private const val minEmbeddedTextLetters = 20
        private const val sameLineTolerance = 4f
        private const val previewAspectRatio = 2f
        private const val previewCropWidth = .86f
        private val previewCropCenters = listOf(.32f, .44f, .57f)
        private const val previewSampleColumns = 36
        private const val previewSampleRows = 20
        private const val colorfulSaturation = .14
        private const val minimumDishScore = .5
        private const val previewJpegQuality = 88
        internal const val pdfPageBreak = "[[MISE_PDF_PAGE_BREAK]]"

        internal fun orderedPageText(chunks: List<PositionedDocumentText>): String = chunks
            .filter { it.text.isNotBlank() }
            .sortedWith { left, right ->
                if (abs(left.top - right.top) < sameLineTolerance) left.left.compareTo(right.left)
                else left.top.compareTo(right.top)
            }
            .joinToString("\n") { it.text }

        internal fun titleHint(name: String): String = name.substringBeforeLast('.', name)
            .replace(Regex("[_-]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        internal fun documentKind(mimeType: String, name: String): DocumentKind {
            val extension = name.substringAfterLast('.', "").lowercase()
            return when {
                mimeType == "application/pdf" || extension == "pdf" -> DocumentKind.PDF
                mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" -> DocumentKind.DOCX
                mimeType.startsWith("text/") || extension in setOf("txt", "md", "markdown", "csv") -> DocumentKind.TEXT
                else -> error("Choose a PDF, DOCX, TXT, Markdown, or CSV document.")
            }
        }

        internal fun docxXmlToText(xml: String): String = xml
            .replace(Regex("<w:tab\\b[^>]*/>"), "\t")
            .replace(Regex("<w:br\\b[^>]*/>"), "\n")
            .replace(Regex("</w:p>"), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace(Regex("[ \\t]+\n"), "\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()

        private fun readBounded(input: InputStream, limit: Int): ByteArray {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                require(total <= limit) { "That document contains too much text." }
                output.write(buffer, 0, count)
            }
            return output.toByteArray()
        }
    }
}

internal enum class DocumentKind { TEXT, DOCX, PDF }
private data class DocumentMetadata(val name: String, val mimeType: String, val size: Long)
