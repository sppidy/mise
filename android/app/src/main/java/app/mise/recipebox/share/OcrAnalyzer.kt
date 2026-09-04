// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.share

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OcrAnalyzer(private val context: Context) {
    suspend fun analyze(path: String, deleteAfter: Boolean = false): String = suspendCancellableCoroutine { continuation ->
        val file = java.io.File(path)
        if (!file.isFile || file.length() > ShareReceiver.maxImageBytes) {
            continuation.resumeWithException(IllegalArgumentException("Choose a photo smaller than 15 MB."))
            return@suspendCancellableCoroutine
        }
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val image = runCatching { InputImage.fromFilePath(context, Uri.fromFile(file)) }
            .getOrElse {
                recognizer.close()
                if (deleteAfter) file.delete()
                continuation.resumeWithException(it)
                return@suspendCancellableCoroutine
            }
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (result.text.trim().length < 20) continuation.resumeWithException(IllegalArgumentException("Not enough text was found in that photo. Try a clearer, well-lit image."))
                else continuation.resume(result.text)
            }
            .addOnFailureListener { continuation.resumeWithException(IllegalArgumentException("Mise could not read text from that photo.", it)) }
            .addOnCompleteListener {
                recognizer.close()
                if (deleteAfter) file.delete()
            }
        continuation.invokeOnCancellation { recognizer.close(); if (deleteAfter) file.delete() }
    }
}
