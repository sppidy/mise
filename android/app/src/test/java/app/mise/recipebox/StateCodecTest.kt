// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import app.mise.recipebox.data.SeedData
import app.mise.recipebox.data.StateCodec
import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.GroceryItem
import app.mise.recipebox.model.Ingredient
import app.mise.recipebox.model.Recipe
import app.mise.recipebox.model.Difficulty
import app.mise.recipebox.model.ImagePosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StateCodecTest {
    @Test fun seedStateHasExpectedServerContract() {
        val source = SeedData.state()
        assertTrue(source.recipes.isNotEmpty())
        assertEquals(4, source.grocery.size)
        assertTrue(source.collections.contains("Weeknight wins"))
        assertEquals(5, source.recipes.first().ingredients.size)
    }

    @Test fun sourceUrlDedupKeepsBestRecipeAndFavorite() {
        val weak = recipe("https://example.test/r", favorite = true, ingredients = listOf(Ingredient("one", "1", "salt")))
        val strong = recipe("https://example.test/r", favorite = false, ingredients = listOf(Ingredient("one", "1", "salt"), Ingredient("two", "2", "pepper")))
        val normalized = AppState(recipes = listOf(weak, strong), collections = listOf("List")).normalized()
        assertEquals(1, normalized.recipes.size)
        assertTrue(normalized.recipes.single().favorite)
        assertEquals(2, normalized.recipes.single().ingredients.size)
    }

    private fun recipe(sourceUrl: String, favorite: Boolean, ingredients: List<Ingredient>) = Recipe(
        id = sourceUrl + favorite + ingredients.size, title = "Recipe", description = "Description", source = "Test",
        sourceUrl = sourceUrl, time = 30, servings = 2, difficulty = Difficulty.EASY, collection = "List",
        favorite = favorite, imagePosition = ImagePosition.TL, ingredients = ingredients, createdAt = "now",
    )
}
