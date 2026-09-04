// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.GroceryItem
import app.mise.recipebox.model.MiseView
import app.mise.recipebox.model.Recipe
import app.mise.recipebox.ui.MiseUiState
import app.mise.recipebox.ui.MiseViewModel
import app.mise.recipebox.ui.components.MiseButton
import app.mise.recipebox.ui.components.MiseChip
import app.mise.recipebox.ui.components.MiseDivider
import app.mise.recipebox.ui.components.MiseIcon
import app.mise.recipebox.ui.components.MiseIconButton
import app.mise.recipebox.ui.components.MiseText
import app.mise.recipebox.ui.components.MiseToast
import app.mise.recipebox.ui.components.MiseIcon.*
import app.mise.recipebox.ui.components.RecipeImage
import app.mise.recipebox.ui.design.LocalMiseColors
import app.mise.recipebox.ui.design.LocalMiseTypography
import app.mise.recipebox.ui.design.MiseShapes

@Composable
fun MainShell(ui: MiseUiState, model: MiseViewModel, dark: Boolean) {
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalMiseColors.current.cream)) {
        val phone = maxWidth < 680.dp
        if (phone) {
            Column(Modifier.fillMaxSize()) {
                TopBar(ui, model, phone = true, dark = dark)
                Box(Modifier.weight(1f)) { MainContent(ui, model, phone) }
                MobileNav(ui.view, model::setView)
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                Sidebar(ui.view, model::setView, ui.appState.recipes.size)
                Column(Modifier.weight(1f)) {
                    TopBar(ui, model, phone = false, dark = dark)
                    MainContent(ui, model, phone = false)
                }
            }
        }
    }
}

@Composable
private fun Sidebar(view: MiseView, setView: (MiseView) -> Unit, count: Int) {
    val colors = LocalMiseColors.current
    Column(Modifier.width(230.dp).fillMaxHeight().background(colors.paper).padding(horizontal = 24.dp, vertical = 28.dp)) {
        Brand()
        Spacer(Modifier.height(57.dp))
        MiseText("YOUR KITCHEN", style = LocalMiseTypography.current.eyebrow, color = colors.muted)
        Spacer(Modifier.height(12.dp))
        listOf(MiseView.HOME to "Home" to Home, MiseView.RECIPES to "All recipes" to BookOpen, MiseView.FAVORITES to "Favorites" to Heart, MiseView.COLLECTIONS to "Collections" to Folder, MiseView.GROCERY to "Grocery list" to Grocery).forEach { item ->
            val selected = view == item.first.first
            Row(Modifier.fillMaxWidth().height(43.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) colors.deep else Color.Transparent).clickable { setView(item.first.first) }.padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                MiseIcon(item.second, Modifier.size(18.dp), if (selected) Color.White else colors.ink2)
                Spacer(Modifier.width(11.dp)); MiseText(item.first.second, style = LocalMiseTypography.current.bodySmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal), color = if (selected) Color.White else colors.ink2)
                if (item.first.first == MiseView.RECIPES) { Spacer(Modifier.weight(1f)); Box(Modifier.clip(RoundedCornerShape(5.dp)).background(if (selected) Color(0x335D897A) else colors.cream).padding(horizontal = 6.dp, vertical = 3.dp)) { MiseText(count.toString(), style = LocalMiseTypography.current.label.copy(fontSize = 9.sp), color = if (selected) Color.White else colors.muted) } }
            }
            Spacer(Modifier.height(4.dp))
        }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) { MiseIcon(Server, Modifier.size(16.dp), colors.muted); Spacer(Modifier.width(8.dp)); MiseText("Saved on your server", style = LocalMiseTypography.current.bodySmall, color = colors.muted); Spacer(Modifier.width(7.dp)); Box(Modifier.size(7.dp).clip(CircleShape).background(colors.orange)) }
        Spacer(Modifier.height(12.dp)); MiseText("Self-hosted.\nNo ads. No subscription.", style = LocalMiseTypography.current.bodySmall.copy(lineHeight = 15.sp), color = colors.muted)
    }
}

@Composable
private fun Brand() {
    val colors = LocalMiseColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(24.dp).clip(RoundedCornerShape(7.dp)).background(colors.deep), contentAlignment = Alignment.Center) { Box(Modifier.width(15.dp).height(5.dp).clip(RoundedCornerShape(4.dp)).background(colors.orange)) }
        Spacer(Modifier.width(9.dp)); MiseText("mise", style = LocalMiseTypography.current.title.copy(fontSize = 27.sp, lineHeight = 27.sp), color = colors.ink)
    }
}

@Composable
private fun TopBar(ui: MiseUiState, model: MiseViewModel, phone: Boolean, dark: Boolean) {
    val colors = LocalMiseColors.current
    Row(Modifier.fillMaxWidth().background(colors.paper).statusBarsPadding().height(69.dp).padding(horizontal = if (phone) 18.dp else 36.dp), verticalAlignment = Alignment.CenterVertically) {
        if (phone) {
            Brand()
            Spacer(Modifier.width(14.dp))
        } else {
            MiseText(viewTitle(ui.view), style = LocalMiseTypography.current.body.copy(fontWeight = FontWeight.Bold), color = colors.ink)
            Spacer(Modifier.width(36.dp))
        }
        SearchBox(ui.search, model::setSearch, Modifier.weight(1f), phone)
        Spacer(Modifier.width(12.dp))
        if (!phone) MiseButton("Import recipe", model::openImport, primary = true, icon = Plus, modifier = Modifier.height(39.dp))
        MiseIconButton(if (dark) Sun else Moon, { model.setTheme(if (dark) "light" else "dark") }, Modifier.size(38.dp), description = "Toggle theme")
        Spacer(Modifier.width(3.dp)); MiseIconButton(Server, model::openSettings, Modifier.size(38.dp), description = "Server settings")
    }
}

@Composable
private fun SearchBox(value: String, onChange: (String) -> Unit, modifier: Modifier, phone: Boolean) {
    val colors = LocalMiseColors.current
    val shape = RoundedCornerShape(10.dp)
    val containerModifier = modifier.height(39.dp).clip(shape).background(colors.cream).then(if (phone) Modifier.border(1.dp, colors.line, shape) else Modifier).padding(horizontal = 11.dp)
    Row(containerModifier, verticalAlignment = Alignment.CenterVertically) {
        MiseIcon(Search, Modifier.size(17.dp), colors.muted)
        Spacer(Modifier.width(8.dp)); app.mise.recipebox.ui.components.MiseTextField(value, onChange, Modifier.weight(1f).height(39.dp), placeholder = if (phone) "Search" else "Search your recipes", embedded = phone)
    }
}

@Composable
private fun MobileNav(view: MiseView, setView: (MiseView) -> Unit) {
    val colors = LocalMiseColors.current
    Row(Modifier.fillMaxWidth().height(77.dp).navigationBarsPadding().background(colors.paper).border(1.dp, colors.line), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        listOf(MiseView.HOME to "Home" to Home, MiseView.RECIPES to "Recipes" to BookOpen, MiseView.COLLECTIONS to "Lists" to Folder, MiseView.GROCERY to "Grocery" to Grocery).forEach { item ->
            val active = view == item.first.first
            Column(Modifier.width(67.dp).clickable { setView(item.first.first) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                MiseIcon(item.second, Modifier.size(21.dp), if (active) colors.orange else colors.muted)
                Spacer(Modifier.height(4.dp)); MiseText(item.first.second, style = LocalMiseTypography.current.label.copy(fontSize = 9.sp), color = if (active) colors.orange else colors.muted)
            }
        }
    }
}

@Composable
private fun MainContent(ui: MiseUiState, model: MiseViewModel, phone: Boolean) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = if (phone) 18.dp else 36.dp, end = if (phone) 18.dp else 36.dp, top = 26.dp, bottom = if (phone) 100.dp else 36.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                when (ui.view) {
                    MiseView.HOME -> HomeScreen(ui, model, phone)
                    MiseView.RECIPES, MiseView.FAVORITES -> RecipesScreen(ui, model, phone)
                    MiseView.COLLECTIONS -> CollectionsScreen(ui, model, phone)
                    MiseView.GROCERY -> GroceryScreen(ui, model)
                }
            }
        }
        if (phone) {
            MiseIconButton(Plus, model::openImport, Modifier.align(Alignment.BottomEnd).padding(end = 18.dp, bottom = 91.dp).size(51.dp), filled = LocalMiseColors.current.orange, description = "Import recipe")
        }
    }
}

@Composable
private fun HomeScreen(ui: MiseUiState, model: MiseViewModel, phone: Boolean) {
    val colors = LocalMiseColors.current
    val recipes = filteredRecipes(ui)
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                MiseText("Good evening, cook.", style = LocalMiseTypography.current.title.copy(fontSize = if (phone) 31.sp else 42.sp, lineHeight = if (phone) 34.sp else 44.sp))
                Spacer(Modifier.height(8.dp)); MiseText("Your kitchen, all in one place.", color = colors.muted)
            }
            if (!phone) MiseButton("Import a recipe", model::openImport, icon = Plus)
        }
        if (recipes.isNotEmpty()) {
            val hero = recipes.first()
            Row(Modifier.fillMaxWidth().height(if (phone) 177.dp else 220.dp).clip(MiseShapes.card).background(colors.deep).clickable { model.openRecipe(hero) }) {
                Column(Modifier.weight(1f).padding(if (phone) 18.dp else 25.dp), verticalArrangement = Arrangement.Center) {
                    MiseText("FEATURED RECIPE", style = LocalMiseTypography.current.eyebrow.copy(fontSize = 9.sp), color = colors.yellow)
                    Spacer(Modifier.height(7.dp)); MiseText(hero.title, style = LocalMiseTypography.current.section.copy(fontSize = if (phone) 24.sp else 30.sp, lineHeight = if (phone) 27.sp else 33.sp), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(11.dp)); Row(verticalAlignment = Alignment.CenterVertically) { MiseIcon(Clock, Modifier.size(14.dp), Color(0xFFB9CCC4)); Spacer(Modifier.width(5.dp)); MiseText("${hero.time} min", style = LocalMiseTypography.current.bodySmall, color = Color(0xFFB9CCC4)); Spacer(Modifier.width(13.dp)); MiseText("Open recipe", style = LocalMiseTypography.current.label, color = Color.White); Spacer(Modifier.width(5.dp)); MiseIcon(ArrowRight, Modifier.size(14.dp), Color.White) }
                }
                Box(Modifier.width(if (phone) 112.dp else 190.dp).fillMaxHeight()) { RecipeImage(hero, Modifier.fillMaxSize()) }
            }
        }
        SectionHeader("Recent recipes", "See all", { model.setView(MiseView.RECIPES) })
        RecipeGrid(recipes.take(4), model::openRecipe, model::toggleFavorite, phone)
    }
}

@Composable
private fun RecipesScreen(ui: MiseUiState, model: MiseViewModel, phone: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            MiseText(if (ui.view == MiseView.FAVORITES) "Favorites" else "All recipes", style = LocalMiseTypography.current.title.copy(fontSize = if (phone) 31.sp else 42.sp, lineHeight = if (phone) 34.sp else 45.sp))
            Spacer(Modifier.height(7.dp)); MiseText("Everything you have saved, ready for your next meal.", color = LocalMiseColors.current.muted)
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("All", "Quick", "Vegetarian", "Baking", "Favorites").forEach { filter -> MiseChip(filter, ui.filter == filter, { model.setFilter(filter) }) } }
        RecipeGrid(filteredRecipes(ui), model::openRecipe, model::toggleFavorite, phone, emptyCopy = if (ui.view == MiseView.FAVORITES) "No favorites yet." else "No recipes found.")
    }
}

@Composable
private fun CollectionsScreen(ui: MiseUiState, model: MiseViewModel, phone: Boolean) {
    val state = ui.appState
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) { MiseText("Lists", style = LocalMiseTypography.current.title.copy(fontSize = if (phone) 31.sp else 42.sp, lineHeight = if (phone) 34.sp else 45.sp)); Spacer(Modifier.height(7.dp)); MiseText("Organize your recipes your way.", color = LocalMiseColors.current.muted) }
            MiseButton("New list", model::openCreateCollection, icon = Plus)
        }
        if (state.collections.isEmpty()) EmptyPanel("No lists yet.", "Create one to organize your saved recipes.")
        state.collections.forEach { collection ->
            val recipes = state.recipes.filter { it.collection == collection }
            Row(Modifier.fillMaxWidth().height(if (phone) 95.dp else 110.dp).clip(MiseShapes.card).background(LocalMiseColors.current.paper).border(1.dp, LocalMiseColors.current.line, MiseShapes.card).clickable { model.setView(MiseView.RECIPES); model.setSearch(collection) }, verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(LocalMiseColors.current.paleGreen), contentAlignment = Alignment.Center) { MiseIcon(Folder, Modifier.size(21.dp), LocalMiseColors.current.onPaleGreen) }; Spacer(Modifier.width(14.dp)); Column { MiseText(collection, style = LocalMiseTypography.current.cardTitle); Spacer(Modifier.height(4.dp)); MiseText("${recipes.size} recipes", style = LocalMiseTypography.current.bodySmall, color = LocalMiseColors.current.muted) } }
                if (!phone) recipes.take(2).forEach { recipe -> RecipeImage(recipe, Modifier.size(81.dp).padding(end = 6.dp).clip(RoundedCornerShape(10.dp))) }
                MiseIconButton(Edit, { model.openRenameCollection(collection) }, Modifier.size(42.dp), description = "Rename $collection")
                MiseIconButton(Trash, { model.openDeleteCollection(collection) }, Modifier.padding(end = 10.dp).size(42.dp), description = "Remove $collection")
            }
        }
    }
}

@Composable
private fun GroceryScreen(ui: MiseUiState, model: MiseViewModel) {
    val groups = ui.appState.grocery.groupBy { it.category }
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column {
            MiseText("Grocery list", style = LocalMiseTypography.current.title.copy(fontSize = 38.sp))
            Spacer(Modifier.height(7.dp))
            MiseText("A little prep makes the week easier.", color = LocalMiseColors.current.muted)
            if (ui.appState.grocery.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                    if (ui.appState.grocery.any(GroceryItem::checked)) MiseButton("Clear checked", model::clearCheckedGrocery, primary = false, icon = Check)
                    MiseButton("Clear all", model::openClearGrocery, primary = false, icon = Trash)
                }
            }
        }
        if (groups.isEmpty()) EmptyPanel("Your grocery list is empty.", "Add ingredients from a recipe to get started.")
        groups.forEach { (category, items) ->
            Column(Modifier.fillMaxWidth().clip(MiseShapes.card).background(LocalMiseColors.current.paper).border(1.dp, LocalMiseColors.current.line, MiseShapes.card).padding(horizontal = 18.dp, vertical = 14.dp)) {
                MiseText(category.uppercase(), style = LocalMiseTypography.current.eyebrow, color = LocalMiseColors.current.muted); Spacer(Modifier.height(8.dp)); items.forEach { item -> GroceryRow(item, model::toggleGrocery) }
            }
        }
    }
}

@Composable
private fun GroceryRow(item: GroceryItem, toggle: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(45.dp).clickable { toggle(item.id) }, verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(20.dp).clip(CircleShape).background(if (item.checked) LocalMiseColors.current.deep else Color.Transparent).border(1.dp, if (item.checked) LocalMiseColors.current.deep else LocalMiseColors.current.line, CircleShape), contentAlignment = Alignment.Center) { if (item.checked) MiseIcon(Check, Modifier.size(14.dp), Color.White, stroke = 2.2f) }; Spacer(Modifier.width(11.dp)); MiseText(item.name, style = LocalMiseTypography.current.body.copy(fontWeight = FontWeight.SemiBold), color = if (item.checked) LocalMiseColors.current.muted else LocalMiseColors.current.ink); Spacer(Modifier.weight(1f)); MiseText(item.amount, style = LocalMiseTypography.current.bodySmall, color = LocalMiseColors.current.muted) }
}

@Composable
private fun RecipeGrid(recipes: List<Recipe>, onOpen: (Recipe) -> Unit, onFavorite: (String) -> Unit, phone: Boolean, emptyCopy: String = "No recipes found.") {
    if (recipes.isEmpty()) { EmptyPanel(emptyCopy, "Try another search or import something delicious."); return }
    val columns = if (phone) 2 else 3
    Column(verticalArrangement = Arrangement.spacedBy(if (phone) 11.dp else 17.dp)) {
        recipes.chunked(columns).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (phone) 11.dp else 17.dp)) {
                row.forEach { recipe -> RecipeCard(recipe, onOpen, onFavorite, Modifier.weight(1f), phone) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun RecipeCard(recipe: Recipe, onOpen: (Recipe) -> Unit, onFavorite: (String) -> Unit, modifier: Modifier, phone: Boolean) {
    val colors = LocalMiseColors.current
    Column(modifier.clip(MiseShapes.card).background(colors.card).border(1.dp, colors.line, MiseShapes.card).clickable { onOpen(recipe) }) {
        Box(Modifier.fillMaxWidth().height(if (phone) 138.dp else 175.dp)) {
            RecipeImage(recipe, Modifier.fillMaxSize().clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)))
            MiseIconButton(Heart, { onFavorite(recipe.id) }, Modifier.align(Alignment.TopEnd).padding(10.dp).size(32.dp), description = if (recipe.favorite) "Remove from favorites" else "Add to favorites", filled = if (recipe.favorite) colors.orange else Color.White.copy(alpha = .9f), iconTint = if (recipe.favorite) Color.White else Color(0xFF17382F))
            Row(Modifier.align(Alignment.BottomStart).padding(10.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = .92f)).padding(horizontal = 7.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { MiseIcon(Clock, Modifier.size(13.dp), Color(0xFF17382F)); Spacer(Modifier.width(4.dp)); MiseText("${recipe.time} min", style = LocalMiseTypography.current.label.copy(fontSize = 9.sp), color = Color(0xFF17382F)) }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 13.dp)) {
            MiseText(recipe.collection.uppercase(), style = LocalMiseTypography.current.eyebrow.copy(fontSize = 8.sp), color = colors.orange, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            MiseText(recipe.title, style = LocalMiseTypography.current.cardTitle.copy(fontSize = if (phone) 14.sp else 16.sp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                MiseText(
                    recipe.source,
                    modifier = Modifier.weight(1f),
                    style = LocalMiseTypography.current.bodySmall.copy(fontSize = 9.sp),
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(7.dp))
                Box(Modifier.size(3.dp).clip(CircleShape).background(colors.muted))
                Spacer(Modifier.width(7.dp))
                MiseText(
                    recipe.difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = LocalMiseTypography.current.bodySmall.copy(fontSize = 9.sp),
                    color = colors.muted,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onClick: () -> Unit) { Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { MiseText(title, style = LocalMiseTypography.current.section); Spacer(Modifier.weight(1f)); MiseText(action, style = LocalMiseTypography.current.label, color = LocalMiseColors.current.orange, modifier = Modifier.clickable(onClick = onClick)); Spacer(Modifier.width(4.dp)); MiseIcon(ChevronRight, Modifier.size(15.dp), LocalMiseColors.current.orange) } }

@Composable
private fun EmptyPanel(title: String, subtitle: String) { Column(Modifier.fillMaxWidth().clip(MiseShapes.card).border(1.dp, LocalMiseColors.current.line, MiseShapes.card).padding(vertical = 47.dp, horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) { MiseIcon(Utensils, Modifier.size(30.dp), LocalMiseColors.current.orange); Spacer(Modifier.height(14.dp)); MiseText(title, style = LocalMiseTypography.current.section, maxLines = 2); Spacer(Modifier.height(7.dp)); MiseText(subtitle, style = LocalMiseTypography.current.bodySmall, color = LocalMiseColors.current.muted) } }

private fun filteredRecipes(ui: MiseUiState): List<Recipe> {
    val query = ui.search.trim().lowercase()
    return ui.appState.recipes.filter { recipe ->
        val searchMatch = query.isBlank() || "${recipe.title} ${recipe.tags.joinToString(" ")} ${recipe.collection}".lowercase().contains(query)
        val viewMatch = ui.view != MiseView.FAVORITES || recipe.favorite
        val filterMatch = when (ui.filter) {
            "Quick" -> recipe.time <= 30
            "Vegetarian" -> recipe.tags.any { it.contains("vegetarian", true) || it.contains("vegan", true) }
            "Baking" -> recipe.tags.any { it.contains("baking", true) } || recipe.collection.contains("baking", true)
            "Favorites" -> recipe.favorite
            else -> true
        }
        searchMatch && viewMatch && filterMatch
    }
}

private fun viewTitle(view: MiseView) = when (view) { MiseView.HOME -> "Home"; MiseView.RECIPES -> "All recipes"; MiseView.FAVORITES -> "Favorites"; MiseView.COLLECTIONS -> "Collections"; MiseView.GROCERY -> "Grocery list" }
