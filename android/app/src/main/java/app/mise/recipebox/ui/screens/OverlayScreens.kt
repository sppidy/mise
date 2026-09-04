// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mise.recipebox.model.Difficulty
import app.mise.recipebox.model.Recipe
import app.mise.recipebox.ui.MiseUiState
import app.mise.recipebox.ui.MiseViewModel
import app.mise.recipebox.ui.components.MiseButton
import app.mise.recipebox.ui.components.MiseChip
import app.mise.recipebox.ui.components.MiseCheck
import app.mise.recipebox.ui.components.MiseDivider
import app.mise.recipebox.ui.components.MiseIcon
import app.mise.recipebox.ui.components.MiseIconButton
import app.mise.recipebox.ui.components.MiseSheet
import app.mise.recipebox.ui.components.MiseText
import app.mise.recipebox.ui.components.MiseTextField
import app.mise.recipebox.ui.components.MiseIcon.*
import app.mise.recipebox.ui.components.RecipeImage
import app.mise.recipebox.ui.components.RecipeImageUrl
import app.mise.recipebox.ui.design.DmSerifItalic
import app.mise.recipebox.ui.design.LocalMiseColors
import app.mise.recipebox.ui.design.LocalMiseTypography
import app.mise.recipebox.ui.design.MiseShapes

@Composable
fun Overlay(ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    when (ui.sheet) {
        "detail" -> ui.selectedRecipe?.let { DetailSheet(it, ui, model, onDismiss) }
        "import", "preview" -> ImportSheet(ui, model, onDismiss)
        "settings" -> SettingsSheet(ui, model, onDismiss)
        "collectionEditor" -> CollectionEditorSheet(ui, model, onDismiss)
        "deleteCollection" -> DeleteCollectionSheet(ui, model, onDismiss)
        "clearGrocery" -> ClearGrocerySheet(ui, model, onDismiss)
        "cook" -> ui.selectedRecipe?.let { CookScreen(it, ui, model, onDismiss) }
    }
}

@Composable
private fun ClearGrocerySheet(ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    val itemCount = ui.appState.grocery.size
    MiseSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxWidth().fillMaxHeight(.42f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 22.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiseText("Clear grocery list?", style = LocalMiseTypography.current.section)
                Spacer(Modifier.weight(1f))
                MiseIconButton(X, onDismiss, Modifier.size(34.dp), description = "Close")
            }
            Spacer(Modifier.height(13.dp))
            MiseText(
                "Remove all $itemCount ${if (itemCount == 1) "item" else "items"} from your grocery list? This can’t be undone.",
                style = LocalMiseTypography.current.bodySmall,
                color = LocalMiseColors.current.muted,
            )
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MiseButton("Cancel", onDismiss, Modifier.weight(1f), primary = false)
                MiseButton("Clear all", model::clearAllGrocery, Modifier.weight(1f), icon = Trash)
            }
        }
    }
}

@Composable
private fun CollectionEditorSheet(ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    val editing = ui.editingCollection != null
    MiseSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxWidth().fillMaxHeight(.48f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 22.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiseText(if (editing) "Rename list" else "Create a list", style = LocalMiseTypography.current.section)
                Spacer(Modifier.weight(1f))
                MiseIconButton(X, onDismiss, Modifier.size(34.dp), description = "Close")
            }
            Spacer(Modifier.height(18.dp))
            MiseText("List name", style = LocalMiseTypography.current.label)
            Spacer(Modifier.height(7.dp))
            MiseTextField(ui.collectionName, model::setCollectionName, Modifier.fillMaxWidth(), "e.g. Dinner favorites")
            ui.collectionError?.let { error ->
                Spacer(Modifier.height(10.dp))
                MiseText(error, style = LocalMiseTypography.current.bodySmall, color = Color(0xFF9B4727))
            }
            Spacer(Modifier.height(18.dp))
            MiseButton(if (editing) "Save name" else "Create list", model::saveCollection, Modifier.fillMaxWidth(), icon = Check, enabled = ui.collectionName.isNotBlank())
        }
    }
}

@Composable
private fun DeleteCollectionSheet(ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    val name = ui.editingCollection ?: return
    val recipeCount = ui.appState.recipes.count { it.collection == name }
    val destination = ui.appState.collections.firstOrNull { it != name } ?: "My recipes"
    MiseSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxWidth().fillMaxHeight(.5f)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 25.dp, vertical = 22.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiseText("Remove list?", style = LocalMiseTypography.current.section)
                Spacer(Modifier.weight(1f))
                MiseIconButton(X, onDismiss, Modifier.size(34.dp), description = "Close")
            }
            Spacer(Modifier.height(13.dp))
            MiseText("Remove “$name”?", style = LocalMiseTypography.current.cardTitle)
            Spacer(Modifier.height(7.dp))
            MiseText(
                if (recipeCount == 0) "The empty list will be removed."
                else "$recipeCount saved ${if (recipeCount == 1) "recipe" else "recipes"} will move to “$destination”. No recipes will be deleted.",
                style = LocalMiseTypography.current.bodySmall,
                color = LocalMiseColors.current.muted,
            )
            ui.collectionError?.let { error -> Spacer(Modifier.height(10.dp)); MiseText(error, style = LocalMiseTypography.current.bodySmall, color = Color(0xFF9B4727)) }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                MiseButton("Cancel", onDismiss, Modifier.weight(1f), primary = false)
                MiseButton("Remove list", model::deleteCollection, Modifier.weight(1f), icon = Trash)
            }
        }
    }
}

@Composable
private fun DetailSheet(recipe: Recipe, ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    val colors = LocalMiseColors.current
    MiseSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxWidth().fillMaxHeight(.94f)) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxWidth().fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 86.dp)) {
            Box(Modifier.fillMaxWidth().height(255.dp)) {
                RecipeImage(recipe, Modifier.fillMaxSize())
                MiseIconButton(X, onDismiss, Modifier.align(Alignment.TopStart).padding(18.dp).size(39.dp), filled = Color.White.copy(alpha = .93f), iconTint = Color(0xFF17382F), description = "Close recipe")
                Row(Modifier.align(Alignment.TopEnd).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { MiseIconButton(Share, { model.shareRecipe(recipe) }, Modifier.size(39.dp), filled = Color.White.copy(alpha = .93f), iconTint = Color(0xFF17382F), description = "Share recipe"); MiseIconButton(Heart, { model.toggleFavorite(recipe.id) }, Modifier.size(39.dp), filled = if (recipe.favorite) colors.orange else Color.White.copy(alpha = .93f), iconTint = if (recipe.favorite) Color.White else Color(0xFF17382F), description = "Favorite recipe") }
            }
            Column(Modifier.padding(horizontal = 28.dp, vertical = 26.dp)) {
                MiseText(recipe.collection.uppercase(), style = LocalMiseTypography.current.eyebrow, color = colors.orange)
                Spacer(Modifier.height(7.dp)); MiseText(recipe.title, style = LocalMiseTypography.current.title.copy(fontSize = 37.sp, lineHeight = 39.sp)); Spacer(Modifier.height(10.dp)); MiseText(recipe.description, style = LocalMiseTypography.current.body, color = colors.ink2)
                Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth().height(56.dp).border(1.dp, colors.line), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) { Meta("TIME", "${recipe.time} min", Clock); ServingsMeta(recipe.servings, { model.changeServings(recipe.id, -1) }, { model.changeServings(recipe.id, 1) }); Meta("LEVEL", recipe.difficulty.name.lowercase().replaceFirstChar { it.uppercase() }, null) }
                Spacer(Modifier.height(11.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { MiseText("From ${recipe.source}", style = LocalMiseTypography.current.bodySmall, color = colors.muted); Spacer(Modifier.weight(1f)); recipe.sourceUrl?.let { sourceUrl -> MiseButton("Open original", { model.openSource(sourceUrl) }, primary = false, icon = Link, modifier = Modifier.height(33.dp)) } }
                Spacer(Modifier.height(30.dp)); Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { MiseText("Ingredients", style = LocalMiseTypography.current.section); Spacer(Modifier.weight(1f)); MiseButton("Add to grocery", { model.addGroceries(recipe) }, primary = false, icon = Grocery, modifier = Modifier.height(33.dp)) }
                Spacer(Modifier.height(12.dp)); MiseDivider()
                recipe.ingredients.groupBy { it.group }.forEach { (group, ingredients) ->
                    if (!group.isNullOrBlank()) { Spacer(Modifier.height(15.dp)); MiseText(group, style = LocalMiseTypography.current.section.copy(fontSize = 19.sp), color = colors.ink) }
                    ingredients.forEach { ingredient ->
                        Row(Modifier.fillMaxWidth().height(45.dp).clickable { model.toggleIngredient(recipe.id, ingredient.id) }, verticalAlignment = Alignment.CenterVertically) { MiseCheck(ingredient.name.startsWith("✓ "), { model.toggleIngredient(recipe.id, ingredient.id) }); Spacer(Modifier.width(10.dp)); MiseText(ingredient.quantity, style = LocalMiseTypography.current.bodySmall.copy(fontWeight = FontWeight.Bold), color = colors.ink); Spacer(Modifier.width(10.dp)); MiseText(ingredient.name.removePrefix("✓ "), style = LocalMiseTypography.current.bodySmall, color = if (ingredient.name.startsWith("✓ ")) colors.muted else colors.ink) }
                        MiseDivider()
                    }
                }
                Spacer(Modifier.height(30.dp)); MiseText("Directions", style = LocalMiseTypography.current.section); Spacer(Modifier.height(12.dp)); recipe.steps.forEachIndexed { index, step -> Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) { Box(Modifier.size(26.dp).clip(CircleShape).background(colors.paleGreen), contentAlignment = Alignment.Center) { MiseText((index + 1).toString(), style = LocalMiseTypography.current.label, color = colors.ink) }; Spacer(Modifier.width(12.dp)); MiseText(step, style = LocalMiseTypography.current.body, color = colors.ink2) } }
            }
            }
            Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(colors.paper).border(1.dp, colors.line).padding(horizontal = 28.dp, vertical = 15.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) { MiseButton("Cook mode", { model.startCook(recipe) }, Modifier.weight(1f), icon = ChefHat); MiseButton("Delete", { model.deleteRecipe(recipe.id) }, Modifier.weight(1f), primary = false, icon = Trash) }
        }
    }
}

@Composable
private fun Meta(label: String, value: String, icon: MiseIcon?) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Row(verticalAlignment = Alignment.CenterVertically) { if (icon != null) { MiseIcon(icon, Modifier.size(13.dp), LocalMiseColors.current.muted); Spacer(Modifier.width(4.dp)) }; MiseText(value, style = LocalMiseTypography.current.bodySmall.copy(fontWeight = FontWeight.Bold)) }; Spacer(Modifier.height(2.dp)); MiseText(label, style = LocalMiseTypography.current.eyebrow.copy(fontSize = 8.sp), color = LocalMiseColors.current.muted) } }

@Composable
private fun ServingsMeta(servings: Int, onMinus: () -> Unit, onPlus: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            MiseIconButton(Minus, onMinus, Modifier.size(19.dp), description = "Decrease servings")
            MiseText(servings.toString(), style = LocalMiseTypography.current.bodySmall.copy(fontWeight = FontWeight.Bold))
            MiseIconButton(Plus, onPlus, Modifier.size(19.dp), description = "Increase servings")
        }
        Spacer(Modifier.height(2.dp)); MiseText("SERVES", style = LocalMiseTypography.current.eyebrow.copy(fontSize = 8.sp), color = LocalMiseColors.current.muted)
    }
}

@Composable
private fun ImportSheet(ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    val colors = LocalMiseColors.current
    var method by remember { mutableStateOf("Link") }
    var pasted by remember { mutableStateOf("") }
    var manualTitle by remember { mutableStateOf("") }
    var manualIngredients by remember { mutableStateOf("") }
    var manualDirections by remember { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { model.analyzePhotoUri(it.toString()) } }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { model.analyzeDocument(it.toString()) } }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { model.updateDraftImage(it.toString()) } }
    MiseSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxWidth().fillMaxHeight(.92f)) {
        if (ui.draft != null) {
            PreviewContent(ui, model, onDismiss, onChooseImage = { coverPicker.launch("image/*") })
        } else {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp, vertical = 22.dp).navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        MiseText("STEP 1 OF 2", style = LocalMiseTypography.current.eyebrow, color = colors.orange)
                        Spacer(Modifier.height(4.dp))
                        MiseText("Import a recipe", style = LocalMiseTypography.current.section)
                    }
                    Spacer(Modifier.weight(1f))
                    MiseIconButton(X, onDismiss, Modifier.size(34.dp), description = "Close import")
                }
                Spacer(Modifier.height(7.dp))
                MiseText("Choose where your recipe is coming from.", style = LocalMiseTypography.current.bodySmall, color = colors.muted)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    listOf(
                        Triple("Link", Link, "Link"),
                        Triple("Text", Clipboard, "Paste"),
                        Triple("Photo", Camera, "Photo"),
                        Triple("File", Upload, "Document"),
                        Triple("Manual", Edit, "Manual"),
                    ).forEach { (label, icon, value) ->
                        ImportMethodTab(label, icon, method == value, !ui.loading, { method = value }, Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(20.dp))
                if (ui.loading) {
                    ImportProgress(ui.importStatus ?: "Finding the recipe", model::cancelImport)
                } else {
                    when (method) {
                        "Link" -> {
                            MiseText("Recipe link", style = LocalMiseTypography.current.label)
                            Spacer(Modifier.height(7.dp))
                            MiseTextField(ui.importUrl, model::setImportUrl, Modifier.fillMaxWidth(), "https://...")
                            Spacer(Modifier.height(12.dp))
                            MiseButton("Find recipe", model::importLink, Modifier.fillMaxWidth(), icon = Search, enabled = ui.importUrl.isNotBlank())
                        }
                        "Paste" -> {
                            MiseText("Recipe text or a pinned comment", style = LocalMiseTypography.current.label)
                            Spacer(Modifier.height(7.dp))
                            MiseTextField(pasted, { pasted = it }, Modifier.fillMaxWidth().height(150.dp), "Paste ingredients and directions", singleLine = false)
                            Spacer(Modifier.height(12.dp))
                            MiseButton("Read recipe", { model.parsePastedText(pasted) }, Modifier.fillMaxWidth(), icon = Clipboard, enabled = pasted.isNotBlank())
                        }
                        "Photo" -> ImportSourceCard(
                            icon = Camera,
                            title = "Scan a recipe photo",
                            description = "Choose a clear photo or screenshot. Text recognition stays on this device; images can be up to 15 MB.",
                            action = "Choose photo",
                            actionIcon = Image,
                            onClick = { photoPicker.launch("image/*") },
                        )
                        "Document" -> ImportSourceCard(
                            icon = Upload,
                            title = "Import a recipe file",
                            description = "PDF, DOCX, TXT, Markdown, or CSV · up to 15 MB. Multi-page PDFs are organized into one recipe book.",
                            action = "Choose file",
                            actionIcon = Upload,
                            onClick = { documentPicker.launch(arrayOf("application/pdf", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "text/markdown", "text/csv")) },
                        )
                        "Manual" -> {
                            MiseText("Recipe title", style = LocalMiseTypography.current.label)
                            Spacer(Modifier.height(7.dp))
                            MiseTextField(manualTitle, { manualTitle = it }, Modifier.fillMaxWidth(), "Untitled recipe")
                            Spacer(Modifier.height(15.dp))
                            MiseText("Ingredients (one per line)", style = LocalMiseTypography.current.label)
                            Spacer(Modifier.height(7.dp))
                            MiseTextField(manualIngredients, { manualIngredients = it }, Modifier.fillMaxWidth().height(150.dp), "2 cups rice\n1 lime", singleLine = false)
                            Spacer(Modifier.height(15.dp))
                            MiseText("Directions (one step per line)", style = LocalMiseTypography.current.label)
                            Spacer(Modifier.height(7.dp))
                            MiseTextField(manualDirections, { manualDirections = it }, Modifier.fillMaxWidth().height(150.dp), "Mix the ingredients\nCook until golden", singleLine = false)
                            Spacer(Modifier.height(12.dp))
                            MiseButton("Review recipe", { model.createManual(manualTitle, manualIngredients, manualDirections) }, Modifier.fillMaxWidth(), icon = ArrowRight, enabled = manualIngredients.isNotBlank() || manualDirections.isNotBlank())
                        }
                    }
                }
                if (!ui.importError.isNullOrBlank()) {
                    Spacer(Modifier.height(13.dp))
                    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(9.dp)).background(Color(0xFFFAE5DC)).padding(12.dp)) {
                        MiseText(ui.importError, style = LocalMiseTypography.current.bodySmall, color = Color(0xFF9B4727))
                    }
                }
            }
        }
    }
}

@Composable
private fun ImportMethodTab(label: String, icon: MiseIcon, selected: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalMiseColors.current
    val shape = RoundedCornerShape(11.dp)
    Column(
        modifier.height(62.dp).clip(shape).background(if (selected) colors.deep else colors.cream)
            .border(1.dp, if (selected) colors.deep else colors.line, shape).clickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        MiseIcon(icon, Modifier.size(18.dp), if (selected) Color.White else colors.ink2)
        Spacer(Modifier.height(5.dp))
        MiseText(label, style = LocalMiseTypography.current.label.copy(fontSize = 9.sp), color = if (selected) Color.White else colors.ink2)
    }
}

@Composable
private fun ImportSourceCard(icon: MiseIcon, title: String, description: String, action: String, actionIcon: MiseIcon, onClick: () -> Unit) {
    val colors = LocalMiseColors.current
    Column(Modifier.fillMaxWidth().clip(MiseShapes.card).background(colors.cream).border(1.dp, colors.line, MiseShapes.card).padding(25.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp).clip(CircleShape).background(colors.paleGreen), contentAlignment = Alignment.Center) {
            MiseIcon(icon, Modifier.size(27.dp), colors.onPaleGreen)
        }
        Spacer(Modifier.height(13.dp))
        MiseText(title, style = LocalMiseTypography.current.section.copy(fontSize = 21.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        MiseText(description, style = LocalMiseTypography.current.bodySmall, color = colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(17.dp))
        MiseButton(action, onClick, Modifier.fillMaxWidth(), icon = actionIcon)
    }
}

@Composable
private fun ImportProgress(status: String, onCancel: () -> Unit) {
    val colors = LocalMiseColors.current
    Column(Modifier.fillMaxWidth().clip(MiseShapes.card).background(colors.cream).border(1.dp, colors.line, MiseShapes.card).padding(horizontal = 25.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(62.dp).clip(CircleShape).background(colors.paleGreen), contentAlignment = Alignment.Center) {
            MiseIcon(Sparkles, Modifier.size(29.dp), colors.onPaleGreen)
        }
        Spacer(Modifier.height(15.dp))
        MiseText(status, style = LocalMiseTypography.current.section.copy(fontSize = 21.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(7.dp))
        MiseText("Mise is extracting ingredients and turning the instructions into clear steps. Large files can take a moment.", style = LocalMiseTypography.current.bodySmall, color = colors.muted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        MiseButton("Cancel", onCancel, primary = false)
    }
}

@Composable
private fun PreviewContent(ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit, onChooseImage: () -> Unit) {
    val draft = ui.draft ?: return
    val collections = ui.appState.collections.ifEmpty { listOf("My recipes") }
    var collection by remember(draft) { mutableStateOf(collections.first()) }
    val previewImage = draft.imageUrl ?: draft.postImageUrls.firstOrNull()
    val colors = LocalMiseColors.current
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 25.dp, end = 25.dp, top = 22.dp, bottom = 116.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiseIconButton(ChevronLeft, model::discardDraft, Modifier.size(34.dp), description = "Choose a different source")
                Spacer(Modifier.width(9.dp))
                Column {
                    MiseText("STEP 2 OF 2", style = LocalMiseTypography.current.eyebrow, color = colors.orange)
                    Spacer(Modifier.height(3.dp))
                    MiseText("Review recipe", style = LocalMiseTypography.current.section)
                }
                Spacer(Modifier.weight(1f))
                MiseIconButton(X, onDismiss, Modifier.size(34.dp), description = "Close import")
            }
            Spacer(Modifier.height(7.dp))
            MiseText("Check what Mise found, then choose where to save it.", style = LocalMiseTypography.current.bodySmall, color = colors.muted)
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                MiseText("Recipe title", style = LocalMiseTypography.current.label)
                Spacer(Modifier.width(7.dp))
                MiseIcon(Edit, Modifier.size(14.dp), colors.muted)
            }
            Spacer(Modifier.height(7.dp))
            MiseTextField(draft.title, model::updateDraftTitle, Modifier.fillMaxWidth(), "Untitled recipe")
            Spacer(Modifier.height(19.dp))
            MiseText("Dish photo", style = LocalMiseTypography.current.label)
            Spacer(Modifier.height(7.dp))
            if (previewImage != null) {
                RecipeImageUrl(previewImage, modifier = Modifier.fillMaxWidth().height(180.dp).clip(MiseShapes.card))
                Spacer(Modifier.height(9.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiseButton(if (ui.coverLoading) "Adding photo…" else "Change photo", onChooseImage, Modifier.weight(1f), primary = false, icon = Image, enabled = !ui.coverLoading)
                    MiseButton("Remove", model::removeDraftImage, primary = false, icon = Trash, enabled = !ui.coverLoading)
                }
            } else {
                Column(Modifier.fillMaxWidth().clip(MiseShapes.card).background(colors.cream).border(1.dp, colors.line, MiseShapes.card).padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(48.dp).clip(CircleShape).background(colors.paleGreen), contentAlignment = Alignment.Center) {
                        MiseIcon(Image, Modifier.size(23.dp), colors.onPaleGreen)
                    }
                    Spacer(Modifier.height(9.dp))
                    MiseText("No dish photo found", style = LocalMiseTypography.current.cardTitle)
                    Spacer(Modifier.height(4.dp))
                    MiseText("Add one now to make this recipe easier to spot.", style = LocalMiseTypography.current.bodySmall, color = colors.muted, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(12.dp))
                    MiseButton(if (ui.coverLoading) "Adding photo…" else "Add photo", onChooseImage, primary = false, icon = Image, enabled = !ui.coverLoading)
                }
            }
            Spacer(Modifier.height(17.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                ImportStat(draft.ingredients.size, "INGREDIENTS", ListChecks, Modifier.weight(1f))
                ImportStat(draft.steps.size, "DIRECTIONS", ChefHat, Modifier.weight(1f))
            }
            draft.sourceUrl?.let { sourceUrl ->
                Spacer(Modifier.height(11.dp))
                MiseButton("Open original source", { model.openSource(sourceUrl) }, Modifier.fillMaxWidth(), primary = false, icon = Link)
            }
            if (draft.ingredients.isNotEmpty()) {
                Spacer(Modifier.height(25.dp))
                MiseText("Ingredients", style = LocalMiseTypography.current.cardTitle)
                Spacer(Modifier.height(10.dp))
                draft.ingredients.take(4).forEach { ingredient ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(colors.orange))
                        Spacer(Modifier.width(10.dp))
                        MiseText(listOf(ingredient.quantity, ingredient.name).filter(String::isNotBlank).joinToString(" "), style = LocalMiseTypography.current.bodySmall, color = colors.ink2)
                    }
                }
                if (draft.ingredients.size > 4) MiseText("+ ${draft.ingredients.size - 4} more ingredients", style = LocalMiseTypography.current.label, color = colors.orange)
            }
            if (draft.steps.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                MiseText("Directions", style = LocalMiseTypography.current.cardTitle)
                Spacer(Modifier.height(10.dp))
                draft.steps.take(2).forEachIndexed { index, step ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
                        Box(Modifier.size(23.dp).clip(CircleShape).background(colors.paleGreen), contentAlignment = Alignment.Center) {
                            MiseText((index + 1).toString(), style = LocalMiseTypography.current.label, color = colors.onPaleGreen)
                        }
                        Spacer(Modifier.width(10.dp))
                        MiseText(step, modifier = Modifier.weight(1f), style = LocalMiseTypography.current.bodySmall, color = colors.ink2, maxLines = 3, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
                if (draft.steps.size > 2) MiseText("+ ${draft.steps.size - 2} more directions", style = LocalMiseTypography.current.label, color = colors.orange)
            }
            Spacer(Modifier.height(25.dp))
            MiseDivider()
            Spacer(Modifier.height(21.dp))
            MiseText("Save to collection", style = LocalMiseTypography.current.cardTitle)
            Spacer(Modifier.height(5.dp))
            MiseText("You can move it later.", style = LocalMiseTypography.current.bodySmall, color = colors.muted)
            Spacer(Modifier.height(11.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                collections.forEach { value -> MiseChip(value, collection == value, { collection = value }) }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(colors.paper).border(1.dp, colors.line)
                .navigationBarsPadding().padding(horizontal = 25.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                MiseText("SAVING TO", style = LocalMiseTypography.current.eyebrow.copy(fontSize = 8.sp), color = colors.muted)
                Spacer(Modifier.height(2.dp))
                MiseText(collection, style = LocalMiseTypography.current.bodySmall.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            MiseButton("Save recipe", { model.saveDraft(collection) }, icon = Check, enabled = draft.title.isNotBlank() && !ui.coverLoading)
        }
    }
}

@Composable
private fun ImportStat(value: Int, label: String, icon: MiseIcon, modifier: Modifier = Modifier) {
    val colors = LocalMiseColors.current
    Row(modifier.clip(RoundedCornerShape(12.dp)).background(colors.cream).border(1.dp, colors.line, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(colors.paleGreen), contentAlignment = Alignment.Center) { MiseIcon(icon, Modifier.size(17.dp), colors.onPaleGreen) }
        Spacer(Modifier.width(10.dp))
        Column {
            MiseText(value.toString(), style = LocalMiseTypography.current.cardTitle)
            MiseText(label, style = LocalMiseTypography.current.eyebrow.copy(fontSize = 7.sp), color = colors.muted)
        }
    }
}

@Composable
private fun SettingsSheet(ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    var server by remember(ui.serverUrl) { mutableStateOf(ui.serverUrl) }
    MiseSheet(onDismiss = onDismiss, modifier = Modifier.fillMaxWidth().fillMaxHeight(.72f)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 25.dp, vertical = 22.dp).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { MiseText("Server settings", style = LocalMiseTypography.current.section); Spacer(Modifier.weight(1f)); MiseIconButton(X, onDismiss, Modifier.size(34.dp)) }
            Spacer(Modifier.height(8.dp)); MiseText("Connect to your private Mise server. The app remains usable offline.", style = LocalMiseTypography.current.bodySmall, color = LocalMiseColors.current.muted); Spacer(Modifier.height(19.dp)); MiseText("Server URL", style = LocalMiseTypography.current.label); Spacer(Modifier.height(7.dp)); MiseTextField(server, { server = it }, Modifier.fillMaxWidth(), "https://mise.example.com"); Spacer(Modifier.height(12.dp)); MiseButton("Connect and sync", { model.connectServer(server) }, Modifier.fillMaxWidth(), icon = Server, enabled = !ui.loading); Spacer(Modifier.height(22.dp)); MiseDivider(); Spacer(Modifier.height(18.dp)); MiseText("Appearance", style = LocalMiseTypography.current.label); Spacer(Modifier.height(9.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("system" to Monitor, "light" to Sun, "dark" to Moon).forEach { (value, icon) -> MiseChip(value.replaceFirstChar { it.uppercase() }, ui.theme == value, { model.setTheme(value) }) } }; Spacer(Modifier.height(22.dp)); MiseDivider(); Spacer(Modifier.height(18.dp)); MiseText("About", style = LocalMiseTypography.current.label); Spacer(Modifier.height(7.dp)); MiseText("Created by Ramshouriesh (sppidy). Copyright © 2026 Mise contributors. Mise is free software under AGPL-3.0-or-later and comes without a warranty.", style = LocalMiseTypography.current.bodySmall, color = LocalMiseColors.current.muted); Spacer(Modifier.height(11.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiseButton("Source", model::openProjectSource, Modifier.weight(1f), primary = false, icon = Link); MiseButton("License", model::openLicense, Modifier.weight(1f), primary = false, icon = Link) }; Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { MiseButton("Privacy", model::openPrivacyPolicy, Modifier.weight(1f), primary = false, icon = ArrowRight); MiseButton("Notices", model::openThirdPartyNotices, Modifier.weight(1f), primary = false, icon = ArrowRight) }
        }
    }
}

@Composable
private fun CookScreen(recipe: Recipe, ui: MiseUiState, model: MiseViewModel, onDismiss: () -> Unit) {
    val colors = LocalMiseColors.current
    val step = recipe.steps.getOrNull(ui.cookStep).orEmpty()
    Box(Modifier.fillMaxSize().background(colors.deep).navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().height(69.dp).border(1.dp, Color.White.copy(alpha = .12f)).padding(horizontal = 25.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) { Box(Modifier.width(14.dp).height(5.dp).clip(RoundedCornerShape(4.dp)).background(colors.orange)) }; Spacer(Modifier.width(9.dp)); MiseText("mise", style = LocalMiseTypography.current.section.copy(fontSize = 24.sp), color = Color.White); Spacer(Modifier.weight(1f)); Column(Modifier.width(170.dp), horizontalAlignment = Alignment.CenterHorizontally) { MiseText("STEP ${ui.cookStep + 1} OF ${recipe.steps.size}", style = LocalMiseTypography.current.eyebrow.copy(fontSize = 9.sp), color = Color(0xFFB9CCC4)); Spacer(Modifier.height(7.dp)); Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(5.dp)).background(Color.White.copy(alpha = .14f))) { Box(Modifier.fillMaxWidth((ui.cookStep + 1f) / recipe.steps.size.coerceAtLeast(1)).height(4.dp).clip(RoundedCornerShape(5.dp)).background(colors.yellow)) } }; Spacer(Modifier.weight(1f)); MiseIconButton(X, onDismiss, Modifier.size(35.dp), description = "Exit cook mode") }
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(260.dp).fillMaxHeight().padding(25.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { RecipeImage(recipe, Modifier.fillMaxWidth().height(145.dp).clip(MiseShapes.card)); MiseText(recipe.title, style = LocalMiseTypography.current.section.copy(fontSize = 25.sp, lineHeight = 28.sp), color = Color.White); MiseText("${recipe.time} min · serves ${recipe.servings}", style = LocalMiseTypography.current.bodySmall, color = Color(0xFFB9CCC4)); Spacer(Modifier.weight(1f)); TimerCard(ui, model) }
                Column(Modifier.weight(1f).fillMaxHeight().padding(horizontal = 34.dp, vertical = 45.dp), verticalArrangement = Arrangement.Center) { MiseText("${ui.cookStep + 1}", style = DmSerifItalic.let { LocalMiseTypography.current.section.copy(fontFamily = it, fontSize = 31.sp) }, color = colors.yellow); Spacer(Modifier.height(18.dp)); MiseText(step, style = LocalMiseTypography.current.title.copy(fontSize = 42.sp, lineHeight = 51.sp), color = Color.White); Spacer(Modifier.height(40.dp)); Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) { MiseIconButton(ChevronLeft, model::previousStep, Modifier.size(48.dp), description = "Previous step"); MiseButton(if (ui.cookStep + 1 >= recipe.steps.size) "Finish" else "Next step", { if (ui.cookStep + 1 >= recipe.steps.size) onDismiss() else model.nextStep() }, icon = ArrowRight) } }
            }
        }
    }
}

@Composable
private fun TimerCard(ui: MiseUiState, model: MiseViewModel) {
    val total = ui.timerSeconds
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.dp, Color.White.copy(alpha = .13f)).padding(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiseIcon(Clock, Modifier.size(18.dp), Color(0xFFB9CCC4))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                MiseText("Timer", style = LocalMiseTypography.current.bodySmall, color = Color(0xFFB9CCC4))
                MiseText("${total / 60}:${(total % 60).toString().padStart(2, '0')}", style = LocalMiseTypography.current.cardTitle.copy(fontSize = 20.sp), color = Color.White)
            }
            MiseButton(if (ui.timerRunning) "Pause" else "Start", model::toggleTimer, primary = true, modifier = Modifier.height(30.dp))
        }
    }
}
