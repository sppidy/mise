// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.data

import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.RecipeDraft
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.HttpURLConnection
import java.net.URL

class ImportFailure(override val message: String, val preview: RecipeDraft? = null) : Exception(message)

/** Small dependency-free HTTP client. Paths and JSON bodies mirror the Node API. */
class MiseApi {
    fun loadState(serverUrl: String): AppState {
        val body = request(serverUrl, "/api/state", "GET")
        return StateCodec.decode(body)
    }

    fun saveState(serverUrl: String, state: AppState) {
        request(serverUrl, "/api/state", "PUT", StateCodec.encode(state), "application/json")
    }

    fun importRecipe(serverUrl: String, url: String): RecipeDraft {
        val body = request(serverUrl, "/api/import", "POST", "{\"url\":${jsonString(url)}}", "application/json")
        return app.mise.recipebox.importer.RecipeParser.fromJsonImport(body, url, serverUrl)
    }

    private fun request(serverUrl: String, path: String, method: String, payload: String? = null, contentType: String? = null): String {
        val base = serverUrl.trim().trimEnd('/')
        val connection = (URL("$base$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 120_000
            useCaches = false
            setRequestProperty("Accept", "application/json")
            if (payload != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType ?: "application/json")
                setFixedLengthStreamingMode(payload.toByteArray(Charsets.UTF_8).size)
            }
        }
        return try {
            if (payload != null) BufferedOutputStream(connection.outputStream).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.let { BufferedInputStream(it).bufferedReader().use { reader -> reader.readText() } }.orEmpty()
            if (code !in 200..299) throw ImportFailure(importFailureMessage(code, response), app.mise.recipebox.importer.RecipeParser.previewFromJson(response, null))
            response
        } finally {
            connection.disconnect()
        }
    }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
        append('"')
    }
}

internal fun importFailureMessage(httpCode: Int, response: String): String {
    val fallback = "Request failed with HTTP $httpCode"
    if (response.isBlank() || response.length > 16_384) return fallback
    val json = response.trim()
    if (!json.startsWith('{') || !json.endsWith('}')) return fallback
    val encoded = Regex("\"error\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)
        ?: return fallback
    val message = decodeJsonString(encoded)
        .replace(Regex("[\\p{Cc}\\p{Cf}\\s]+"), " ")
        .trim()
    if (message.isBlank()) return fallback
    return if (message.length <= 300) message else message.take(299).trimEnd() + "…"
}

private fun decodeJsonString(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        val char = value[index++]
        if (char != '\\' || index >= value.length) {
            append(char)
            continue
        }
        when (val escaped = value[index++]) {
            '"', '\\', '/' -> append(escaped)
            'b' -> append('\b')
            'f' -> append('\u000c')
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            'u' -> {
                val end = index + 4
                val decoded = value.substring(index, end.coerceAtMost(value.length))
                    .takeIf { end <= value.length && it.all { digit -> digit.isDigit() || digit.lowercaseChar() in 'a'..'f' } }
                    ?.toIntOrNull(16)
                if (decoded != null) {
                    append(decoded.toChar())
                    index = end
                }
            }
        }
    }
}
