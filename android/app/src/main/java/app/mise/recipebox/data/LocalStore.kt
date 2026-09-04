// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.data

import android.content.Context
import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.StateNormalizer
import java.io.File

/** Local-first atomic JSON cache for native state. */
class LocalStore(context: Context) {
    private val file = File(context.filesDir, "mise-state-v1.json")
    private val lock = Any()

    fun read(): AppState = synchronized(lock) {
        runCatching { StateCodec.decode(file.readText()) }
            .getOrElse { SeedData.state() }
            .normalized()
    }

    fun write(state: AppState) = synchronized(lock) {
        val normalized = StateNormalizer.normalize(state)
        val temporary = File(file.parentFile, "${file.name}.tmp")
        temporary.writeText(StateCodec.encode(normalized))
        if (!temporary.renameTo(file)) {
            file.delete()
            check(temporary.renameTo(file)) { "Could not persist Mise state" }
        }
    }
}
