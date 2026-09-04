// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.share

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.IOException
import java.util.UUID

data class SharePayload(val text: String = "", val imagePath: String? = null)

/** Captures ACTION_SEND and ACTION_SEND_MULTIPLE once, preserving text, HTML, URI and images. */
class ShareReceiver(private val context: Context) {
    companion object { const val maxImageBytes = 15_000_000L }

    fun capture(intent: Intent?): SharePayload? {
        if (intent == null || intent.action !in setOf(Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE)) return null
        val text = linkedSetOf<String>()
        fun append(value: CharSequence?) { value?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(text::add) }
        append(intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT))
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            intent.getCharSequenceArrayListExtra(Intent.EXTRA_TEXT)?.forEach(::append)
        } else append(intent.getCharSequenceExtra(Intent.EXTRA_TEXT))
        append(intent.getStringExtra(Intent.EXTRA_HTML_TEXT))
        intent.dataString?.let(::append)
        val imageUris = mutableListOf<Uri>()
        if (intent.action == Intent.ACTION_SEND_MULTIPLE) {
            val streams = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
            streams?.let(imageUris::addAll)
        } else {
            val stream = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
            stream?.let(imageUris::add)
        }
        val clip = intent.clipData
        if (clip != null) for (index in 0 until clip.itemCount) {
            val item = clip.getItemAt(index)
            append(item.text)
            item.uri?.let(imageUris::add)
        }
        val imagePath = imageUris.distinct().firstNotNullOfOrNull(::cacheImage)
        if (text.isEmpty() && imagePath == null) return null
        return SharePayload(text.joinToString("\n"), imagePath)
    }

    private fun cacheImage(source: Uri): String? {
        val directory = File(context.cacheDir, "shared-images")
        if (!directory.exists() && !directory.mkdirs()) return null
        val target = File(directory, "${UUID.randomUUID()}.img")
        return try {
            var total = 0L
            context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > maxImageBytes) throw IOException("Shared image is too large")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: return null
            target.absolutePath
        } catch (_: Exception) {
            target.delete()
            null
        }
    }
}
