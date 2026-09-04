// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import app.mise.recipebox.importer.SharedRecipeImporter
import app.mise.recipebox.model.RecipeDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SharedRecipeImporterTest {
    @Test fun completeCaptionFallsBackToTextWithSourceUrlAfterLinkFailure() {
        var importedLink = false
        val result = SharedRecipeImporter.resolve(
            text = "Pasta\nIngredients:\n2 cups flour\n1 tsp salt\nhttps://example.com/pasta",
            parseText = { _, sourceUrl -> Result.success(RecipeDraft(title = "Pasta", sourceUrl = sourceUrl, steps = listOf("Cook pasta"))) },
            importUrl = { importedLink = true; Result.failure(IllegalStateException("No recipe found at link")) },
        )

        assertEquals("Pasta", result.getOrThrow().title)
        assertEquals("https://example.com/pasta", result.getOrThrow().sourceUrl)
        assertTrue(importedLink)
    }

    @Test fun linkOnlyShareImportsExtractedUrlAfterCaptionParseFails() {
        var importedUrl = ""
        val result = SharedRecipeImporter.resolve(
            text = "Try this https://example.com/recipe!",
            parseText = { _, _ -> Result.failure(IllegalArgumentException("No ingredients")) },
            importUrl = { url -> importedUrl = url; Result.success(RecipeDraft(title = "Imported", steps = listOf("Cook"))) },
        )

        assertEquals("https://example.com/recipe", importedUrl)
        assertEquals("Imported", result.getOrThrow().title)
    }

    @Test fun linkFailureSurvivesWhenCaptionAndLinkBothFail() {
        var parsedCaption = false
        val result = SharedRecipeImporter.resolve(
            text = "Look https://example.com/not-a-recipe",
            parseText = { _, _ -> parsedCaption = true; Result.failure(IllegalArgumentException("No ingredients")) },
            importUrl = { Result.failure(IllegalStateException("No recipe was found in the shared post")) },
        )

        assertTrue(result.isFailure)
        assertEquals("No recipe was found in the shared post", result.exceptionOrNull()?.message)
        assertFalse(parsedCaption)
    }
}
