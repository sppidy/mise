// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.mise.recipebox.importer.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.runner.RunWith
import org.junit.Test

@RunWith(AndroidJUnit4::class)
class RecipeImportJsonTest {
    @Test fun decodesNormalizedServerIngredientsAndImageUrls() {
        val draft = RecipeParser.fromJsonImport(
            raw = """
                {
                  "title": "Tomato pasta",
                  "description": "A quick dinner",
                  "source": "Example Cook",
                  "sourceUrl": "https://social.example/post/1",
                  "imageUrl": "/api/images/cover.jpg",
                  "postImageUrls": ["/api/images/cover.jpg", "https://cdn.example/step.jpg"],
                  "postMedia": "photo",
                  "time": 25,
                  "servings": 2,
                  "difficulty": "Medium",
                  "ingredients": [
                    {"id":"tomato","quantity":"2 cups","name":"chopped tomatoes","group":"sauce"},
                    {"id":"basil","quantity":"","name":"fresh basil"}
                  ],
                  "steps": ["Cook the pasta.", "Finish with the sauce."],
                  "tags": ["Instagram", "Weeknight"]
                }
            """.trimIndent(),
            sourceUrl = "https://social.example/post/1",
            assetBaseUrl = "http://192.0.2.10:8787",
        )

        assertEquals(2, draft.ingredients.size)
        assertEquals("2 cups", draft.ingredients[0].quantity)
        assertEquals("chopped tomatoes", draft.ingredients[0].name)
        assertEquals("sauce", draft.ingredients[0].group)
        assertEquals("fresh basil", draft.ingredients[1].name)
        assertEquals("http://192.0.2.10:8787/api/images/cover.jpg", draft.imageUrl)
        assertEquals(
            listOf("http://192.0.2.10:8787/api/images/cover.jpg", "https://cdn.example/step.jpg"),
            draft.postImageUrls,
        )
        assertEquals("photo", draft.postMedia)
        assertEquals(25, draft.time)
        assertEquals(2, draft.servings)
        assertEquals(listOf("Instagram", "Weeknight"), draft.tags)
    }
}
