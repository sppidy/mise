// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.data

import android.content.Context
import app.mise.recipebox.importer.RecipeParser
import app.mise.recipebox.importer.SharedRecipeImporter
import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.RecipeDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MiseRepository(context: Context) {
    private val local = LocalStore(context)
    private val api = MiseApi()
    private val preferences = MiseDataStore(context)

    suspend fun readLocal(): AppState = withContext(Dispatchers.IO) { local.read() }

    suspend fun load(): AppState = withContext(Dispatchers.IO) {
        val settings = preferences.read()
        if (settings.serverUrl.isBlank()) return@withContext local.read()
        runCatching { api.loadState(settings.serverUrl).normalized().also(local::write) }
            .getOrElse { local.read() }
    }

    suspend fun save(state: AppState) = withContext(Dispatchers.IO) {
        local.write(state)
        val settings = preferences.read()
        if (settings.serverUrl.isNotBlank()) runCatching { api.saveState(settings.serverUrl, state) }
    }

    suspend fun connect(value: String): Result<AppState> = withContext(Dispatchers.IO) {
        val normalized = value.trim().trimEnd('/')
        if (normalized.isBlank()) {
            preferences.setServerUrl("")
            return@withContext Result.success(local.read())
        }
        runCatching {
            val state = api.loadState(normalized).normalized()
            preferences.setServerUrl(normalized)
            local.write(state)
            state
        }
    }

    suspend fun serverUrl(): String = preferences.read().serverUrl
    suspend fun theme(): String = preferences.read().theme
    suspend fun setTheme(value: String) = preferences.setTheme(value)

    suspend fun importRecipe(url: String): Result<RecipeDraft> = withContext(Dispatchers.IO) {
        val settings = preferences.read()
        importRecipe(url, settings.serverUrl)
    }

    suspend fun parseSharedText(value: String, sourceUrl: String = ""): Result<RecipeDraft> = withContext(Dispatchers.Default) {
        runCatching { RecipeParser.fromText(value, sourceUrl) }
    }

    suspend fun importShared(value: String): Result<RecipeDraft> = withContext(Dispatchers.IO) {
        val settings = preferences.read()
        SharedRecipeImporter.resolve(
            text = value,
            parseText = { text, sourceUrl -> runCatching { RecipeParser.fromText(text, sourceUrl) } },
            importUrl = { url -> importRecipe(url, settings.serverUrl) },
        )
    }

    private fun importRecipe(url: String, serverUrl: String): Result<RecipeDraft> =
        if (serverUrl.isNotBlank()) {
            runCatching { api.importRecipe(serverUrl, url) }
        } else {
            runCatching { RecipeParser.fromUrl(url) }
        }
}
