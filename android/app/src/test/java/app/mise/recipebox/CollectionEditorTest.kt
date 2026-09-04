// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.CollectionEditor
import app.mise.recipebox.model.Difficulty
import app.mise.recipebox.model.ImagePosition
import app.mise.recipebox.model.Recipe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionEditorTest {
    @Test fun createsAndRenamesListsWithoutLosingRecipes() {
        val original = AppState(
            recipes = listOf(recipe("one", "Weeknight")),
            collections = listOf("Weeknight"),
        )

        val created = CollectionEditor.create(original, "  Baking  ")
        val renamed = CollectionEditor.rename(created, "Weeknight", "Quick dinners")

        assertEquals(listOf("Quick dinners", "Baking"), renamed.collections)
        assertEquals("Quick dinners", renamed.recipes.single().collection)
    }

    @Test fun removingAListMovesItsRecipesToAnExistingList() {
        val state = AppState(
            recipes = listOf(recipe("one", "Weeknight"), recipe("two", "Baking")),
            collections = listOf("Weeknight", "Baking"),
        )

        val updated = CollectionEditor.delete(state, "Weeknight")

        assertEquals(listOf("Baking"), updated.collections)
        assertEquals(2, updated.recipes.size)
        assertTrue(updated.recipes.all { it.collection == "Baking" })
    }

    @Test fun removingTheOnlyListCreatesASafeDestination() {
        val updated = CollectionEditor.delete(
            AppState(recipes = listOf(recipe("one", "Only list")), collections = listOf("Only list")),
            "Only list",
        )

        assertEquals(listOf("My recipes"), updated.collections)
        assertEquals("My recipes", updated.recipes.single().collection)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCaseInsensitiveDuplicateNames() {
        CollectionEditor.create(AppState(collections = listOf("Baking")), "baking")
    }

    private fun recipe(id: String, collection: String) = Recipe(
        id = id,
        title = "Recipe $id",
        description = "Description",
        source = "Test",
        time = 30,
        servings = 2,
        difficulty = Difficulty.EASY,
        collection = collection,
        imagePosition = ImagePosition.TL,
        createdAt = "now",
    )
}
