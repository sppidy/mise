// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.miseSettingsDataStore by preferencesDataStore(name = "mise-settings")

data class MiseSettings(
    val serverUrl: String = "",
    val theme: String = "system",
)

/** Preferences DataStore for server endpoint and system/light/dark selection. */
class MiseDataStore(private val context: Context) {
    private object Keys {
        val serverUrl: Preferences.Key<String> = stringPreferencesKey("server-url")
        val theme: Preferences.Key<String> = stringPreferencesKey("theme")
    }

    val settings: Flow<MiseSettings> = context.miseSettingsDataStore.data.map { values ->
        MiseSettings(values[Keys.serverUrl].orEmpty(), values[Keys.theme] ?: "system")
    }

    suspend fun read(): MiseSettings = settings.first()

    suspend fun setServerUrl(value: String) {
        val normalized = value.trim().trimEnd('/')
        context.miseSettingsDataStore.edit { values ->
            if (normalized.isBlank()) values.remove(Keys.serverUrl) else values[Keys.serverUrl] = normalized
        }
    }

    suspend fun setTheme(value: String) {
        context.miseSettingsDataStore.edit { values -> values[Keys.theme] = value.lowercase() }
    }
}
