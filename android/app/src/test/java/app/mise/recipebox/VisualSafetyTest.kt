// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualSafetyTest {
    @Test
    fun activityAndThemesKeepComposeInControlOfSystemChrome() {
        val styles = source("src/main/res/values/styles.xml")
        val stylesV27 = source("src/main/res/values-v27/styles.xml")
        val activity = source("src/main/java/app/mise/recipebox/MainActivity.kt")

        assertTrue(styles.contains("<item name=\"android:windowActionBar\">false</item>"))
        assertTrue(styles.contains("<item name=\"android:windowNoTitle\">true</item>"))
        assertTrue(stylesV27.contains("name=\"AppTheme.NoActionBarLaunch\" parent=\"AppTheme\""))
        assertTrue(stylesV27.contains("<item name=\"android:windowActionBar\">false</item>"))
        assertTrue(activity.contains("WindowCompat.setDecorFitsSystemWindows(window, false)"))
        assertTrue(activity.contains("onThemeChanged = { dark -> applySystemBars(dark) }"))
    }

    @Test
    fun whiteRecipeOverlaysUseExplicitDarkIconsAndText() {
        val mainScreens = source("src/main/java/app/mise/recipebox/ui/screens/MainScreens.kt")
        val components = source("src/main/java/app/mise/recipebox/ui/components/MiseComponents.kt")
        val overlays = source("src/main/java/app/mise/recipebox/ui/screens/OverlayScreens.kt")

        assertTrue(mainScreens.contains("iconTint = if (recipe.favorite) Color.White else Color(0xFF17382F)"))
        assertTrue(mainScreens.contains("MiseIcon(Clock, Modifier.size(13.dp), Color(0xFF17382F))"))
        assertTrue(components.contains("iconTint: Color? = null"))
        assertTrue(overlays.contains("color = colors.ink) }; Spacer(Modifier.width(12.dp)); MiseText(step"))
        assertTrue(overlays.contains("Modifier.align(Alignment.BottomCenter)"))
        assertTrue(overlays.contains("colors.onPaleGreen"))
        assertTrue(mainScreens.contains("LocalMiseColors.current.onPaleGreen"))
    }

    @Test
    fun topBarsReserveAFullContentRowBelowStatusInsets() {
        val mainScreens = source("src/main/java/app/mise/recipebox/ui/screens/MainScreens.kt")
        val overlays = source("src/main/java/app/mise/recipebox/ui/screens/OverlayScreens.kt")

        assertTrue(mainScreens.contains("background(colors.paper).statusBarsPadding().height(69.dp)"))
        assertTrue(!mainScreens.contains("height(69.dp).statusBarsPadding()"))
        assertTrue(overlays.contains("fillMaxWidth().statusBarsPadding().height(69.dp).border"))
    }

    @Test
    fun recipeCardMetadataKeepsLongSourcesAwayFromDifficulty() {
        val mainScreens = source("src/main/java/app/mise/recipebox/ui/screens/MainScreens.kt")
        val recipeCard = mainScreens.substringAfter("private fun RecipeCard").substringBefore("@Composable\nprivate fun SectionHeader")

        assertTrue(recipeCard.contains("Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp))"))
        assertTrue(recipeCard.contains("Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically)"))
        assertTrue(recipeCard.contains("recipe.source,\n                    modifier = Modifier.weight(1f),"))
        assertTrue(recipeCard.contains("recipe.source,\n                    modifier = Modifier.weight(1f),\n                    style = LocalMiseTypography.current.bodySmall.copy(fontSize = 9.sp),\n                    color = colors.muted,\n                    maxLines = 1,\n                    overflow = TextOverflow.Ellipsis"))
        assertTrue(recipeCard.contains("recipe.difficulty.name.lowercase().replaceFirstChar { it.uppercase() },\n                    style = LocalMiseTypography.current.bodySmall.copy(fontSize = 9.sp),\n                    color = colors.muted,\n                    maxLines = 1"))
    }

    @Test
    fun importedRecipesKeepAnActionBackToTheirOriginalSource() {
        val overlays = source("src/main/java/app/mise/recipebox/ui/screens/OverlayScreens.kt")
        val viewModel = source("src/main/java/app/mise/recipebox/ui/MiseViewModel.kt")

        assertTrue(overlays.contains("Open original source"))
        assertTrue(overlays.contains("Open original"))
        assertTrue(viewModel.contains("Intent.ACTION_VIEW"))
        assertTrue(viewModel.contains("Intent.FLAG_ACTIVITY_NEW_TASK"))
    }

    @Test
    fun importedRecipesCanEditAndPersistTheirCoverDetails() {
        val overlays = source("src/main/java/app/mise/recipebox/ui/screens/OverlayScreens.kt")
        val viewModel = source("src/main/java/app/mise/recipebox/ui/MiseViewModel.kt")
        val analyzer = source("src/main/java/app/mise/recipebox/share/DocumentAnalyzer.kt")

        assertTrue(overlays.contains("model::updateDraftTitle"))
        assertTrue(overlays.contains("Change photo"))
        assertTrue(overlays.contains("model::removeDraftImage"))
        assertTrue(overlays.contains("draft.title.isNotBlank() && !ui.coverLoading"))
        assertTrue(viewModel.contains("persistDraftImage(draft.imageUrl)"))
        assertTrue(analyzer.contains("bestDishRegion(bitmap)"))
        assertTrue(analyzer.contains("previewImageUrl"))
    }

    @Test
    fun groceryListClearAllRequiresConfirmation() {
        val mainScreens = source("src/main/java/app/mise/recipebox/ui/screens/MainScreens.kt")
        val overlays = source("src/main/java/app/mise/recipebox/ui/screens/OverlayScreens.kt")
        val viewModel = source("src/main/java/app/mise/recipebox/ui/MiseViewModel.kt")

        assertTrue(mainScreens.contains("MiseButton(\"Clear all\", model::openClearGrocery"))
        assertTrue(overlays.contains("\"clearGrocery\" -> ClearGrocerySheet"))
        assertTrue(overlays.contains("MiseButton(\"Clear all\", model::clearAllGrocery"))
        assertTrue(viewModel.contains("state.copy(grocery = emptyList())"))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(File(relativePath), File("app/$relativePath"))
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("$relativePath is not available from the test working directory")
    }
}
