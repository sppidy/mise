// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.importer

import app.mise.recipebox.model.RecipeDraft

/** Coordinates shared captions and links without depending on Android framework types. */
object SharedRecipeImporter {
    fun resolve(
        text: String,
        parseText: (value: String, sourceUrl: String) -> Result<RecipeDraft>,
        importUrl: (url: String) -> Result<RecipeDraft>,
    ): Result<RecipeDraft> {
        val sourceUrl = RecipeParser.firstUrl(text).orEmpty()
        if (sourceUrl.isBlank()) return parseText(text, "")

        val importedLink = importUrl(sourceUrl)
        if (importedLink.isSuccess || !RecipeParser.hasRecipeSignals(text)) return importedLink

        return parseText(text, sourceUrl).fold(
            onSuccess = { Result.success(it) },
            onFailure = { importedLink },
        )
    }
}
