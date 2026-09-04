// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollSafetyTest {
    @Test
    fun recipeFiltersAreNotVerticallyScrollable() {
        val candidates = listOf(
            File("src/main/java/app/mise/recipebox/ui/screens/MainScreens.kt"),
            File("app/src/main/java/app/mise/recipebox/ui/screens/MainScreens.kt"),
        )
        val source = candidates.firstOrNull(File::isFile)?.readText()
            ?: error("MainScreens.kt is not available from the test working directory")
        val recipesScreen = source.substringAfter("private fun RecipesScreen")
            .substringBefore("private fun CollectionsScreen")

        assertTrue(recipesScreen.contains("horizontalScroll(rememberScrollState())"))
        assertFalse(recipesScreen.contains("verticalScroll"))
    }
}
