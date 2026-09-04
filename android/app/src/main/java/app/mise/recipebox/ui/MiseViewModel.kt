// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.content.Intent
import android.net.Uri
import app.mise.recipebox.BuildConfig
import kotlinx.coroutines.Dispatchers
import app.mise.recipebox.data.MiseRepository
import app.mise.recipebox.importer.RecipeParser
import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.CollectionEditor
import app.mise.recipebox.model.GroceryItem
import app.mise.recipebox.model.Ingredient
import app.mise.recipebox.model.MiseView
import app.mise.recipebox.model.Recipe
import app.mise.recipebox.model.RecipeDraft
import app.mise.recipebox.model.materializeRecipe
import app.mise.recipebox.share.OcrAnalyzer
import app.mise.recipebox.share.DocumentAnalyzer
import app.mise.recipebox.share.SharePayload
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MiseUiState(
    val appState: AppState = AppState(),
    val view: MiseView = MiseView.HOME,
    val search: String = "",
    val filter: String = "All",
    val selectedRecipe: Recipe? = null,
    val sheet: String? = null,
    val draft: RecipeDraft? = null,
    val importUrl: String = "",
    val importError: String? = null,
    val collectionName: String = "",
    val editingCollection: String? = null,
    val collectionError: String? = null,
    val serverUrl: String = "",
    val theme: String = "system",
    val initializing: Boolean = true,
    val loading: Boolean = false,
    val importStatus: String? = null,
    val coverLoading: Boolean = false,
    val toast: String? = null,
    val cookStep: Int = 0,
    val timerSeconds: Int = 0,
    val timerRunning: Boolean = false,
)

class MiseViewModel(private val repository: MiseRepository, context: Context) : ViewModel() {
    private val context = context.applicationContext
    private val _state = MutableStateFlow(MiseUiState())
    val state: StateFlow<MiseUiState> = _state.asStateFlow()
    private var toastJob: Job? = null
    private var timerJob: Job? = null
    private var importJob: Job? = null
    private var coverJob: Job? = null

    init {
        viewModelScope.launch {
            val loaded = repository.load()
            _state.update { it.copy(appState = loaded, serverUrl = repository.serverUrl(), theme = repository.theme(), initializing = false) }
        }
    }

    fun setView(view: MiseView) { _state.update { it.copy(view = view, search = "", filter = "All") } }
    fun setSearch(value: String) { _state.update { it.copy(search = value) } }
    fun setFilter(value: String) { _state.update { it.copy(filter = value) } }
    fun openRecipe(recipe: Recipe) { _state.update { it.copy(selectedRecipe = recipe, sheet = "detail") } }
    fun closeSheet() {
        importJob?.cancel()
        coverJob?.cancel()
        importJob = null
        coverJob = null
        deleteCachedPreview(_state.value.draft?.imageUrl)
        _state.update { it.copy(sheet = null, selectedRecipe = null, draft = null, importError = null, importStatus = null, loading = false, coverLoading = false, collectionName = "", editingCollection = null, collectionError = null) }
    }

    fun toggleFavorite(id: String) = mutate { state -> state.copy(recipes = state.recipes.map { if (it.id == id) it.copy(favorite = !it.favorite) else it }) }
    fun changeServings(id: String, delta: Int) {
        mutate { state -> state.copy(recipes = state.recipes.map { if (it.id == id) it.copy(servings = (it.servings + delta).coerceIn(1, 99)) else it }) }
        _state.update { current -> current.copy(selectedRecipe = current.selectedRecipe?.takeIf { it.id == id }?.copy(servings = ((current.selectedRecipe?.servings ?: 1) + delta).coerceIn(1, 99)) ?: current.selectedRecipe) }
    }

    fun shareRecipe(recipe: Recipe) {
        val body = buildString {
            append(recipe.title).append("\n\n")
            if (recipe.description.isNotBlank()) append(recipe.description).append("\n\n")
            recipe.ingredients.forEach { ingredient -> append("• ").append(ingredient.quantity).append(if (ingredient.quantity.isBlank()) "" else " ").append(ingredient.name.removePrefix("✓ ")).append('\n') }
            if (recipe.steps.isNotEmpty()) append("\nDirections:\n")
            recipe.steps.forEachIndexed { index, step -> append(index + 1).append(". ").append(step).append('\n') }
            recipe.sourceUrl?.let { append("\n").append(it) }
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, body) }, "Share recipe"))
    }
    fun openSource(url: String) {
        if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { showToast("Could not open the original source") }
    }
    fun openProjectSource() = openSource(projectSourceUrl)
    fun openPrivacyPolicy() = openSource(privacyPolicyUrl)
    fun openLicense() = openSource(licenseUrl)
    fun openThirdPartyNotices() = openSource(thirdPartyNoticesUrl)
    fun deleteRecipe(id: String) {
        val recipe = _state.value.appState.recipes.firstOrNull { it.id == id }
        mutate { state -> state.copy(recipes = state.recipes.filterNot { it.id == id }) }
        recipe?.let(::deleteRecipeImages)
        closeSheet(); showToast("Recipe deleted")
    }

    fun toggleIngredient(recipeId: String, ingredientId: String) {
        val recipe = _state.value.appState.recipes.firstOrNull { it.id == recipeId } ?: return
        val updated = recipe.ingredients.map { ingredient -> if (ingredient.id == ingredientId) ingredient.copy(name = if (ingredient.name.startsWith("✓ ")) ingredient.name.removePrefix("✓ ") else "✓ ${ingredient.name}") else ingredient }
        mutate { state -> state.copy(recipes = state.recipes.map { if (it.id == recipeId) it.copy(ingredients = updated) else it }) }
        _state.update { current -> current.copy(selectedRecipe = updatedRecipe(current.selectedRecipe, recipeId, updated)) }
    }

    fun addGroceries(recipe: Recipe) {
        mutate { state ->
            val existing = state.grocery.map { it.name.lowercase() }.toMutableSet()
            val added = recipe.ingredients.filter { existing.add(it.name.lowercase()) }.map { ingredient ->
                GroceryItem(id = "grocery-${ingredient.id}", name = ingredient.name.removePrefix("✓ "), amount = ingredient.quantity, category = groceryCategory(ingredient.name))
            }
            state.copy(grocery = state.grocery + added)
        }
        showToast("Ingredients added to grocery list")
    }

    fun toggleGrocery(id: String) { mutate { state -> state.copy(grocery = state.grocery.map { if (it.id == id) it.copy(checked = !it.checked) else it }) } }
    fun clearCheckedGrocery() { mutate { state -> state.copy(grocery = state.grocery.filterNot(GroceryItem::checked)) }; showToast("Checked items cleared") }
    fun openClearGrocery() {
        if (_state.value.appState.grocery.isNotEmpty()) _state.update { it.copy(sheet = "clearGrocery") }
    }
    fun clearAllGrocery() {
        if (_state.value.appState.grocery.isEmpty()) {
            _state.update { it.copy(sheet = null) }
            return
        }
        mutate { state -> state.copy(grocery = emptyList()) }
        _state.update { it.copy(sheet = null) }
        showToast("Grocery list cleared")
    }

    fun openCreateCollection() { _state.update { it.copy(sheet = "collectionEditor", collectionName = "", editingCollection = null, collectionError = null) } }
    fun openRenameCollection(value: String) { _state.update { it.copy(sheet = "collectionEditor", collectionName = value, editingCollection = value, collectionError = null) } }
    fun setCollectionName(value: String) { _state.update { it.copy(collectionName = value.take(80), collectionError = null) } }
    fun saveCollection() {
        val current = _state.value
        val result = runCatching {
            current.editingCollection?.let { CollectionEditor.rename(current.appState, it, current.collectionName) }
                ?: CollectionEditor.create(current.appState, current.collectionName)
        }
        result.onSuccess { updated ->
            _state.update { it.copy(appState = updated, sheet = null, collectionName = "", editingCollection = null, collectionError = null) }
            viewModelScope.launch { repository.save(updated) }
            showToast(if (current.editingCollection == null) "List created" else "List renamed")
        }.onFailure { error -> _state.update { it.copy(collectionError = error.message ?: "Could not save that list.") } }
    }
    fun openDeleteCollection(value: String) { _state.update { it.copy(sheet = "deleteCollection", editingCollection = value, collectionError = null) } }
    fun deleteCollection() {
        val current = _state.value
        val name = current.editingCollection ?: return
        runCatching { CollectionEditor.delete(current.appState, name) }
            .onSuccess { updated ->
                _state.update { it.copy(appState = updated, sheet = null, editingCollection = null, collectionName = "", collectionError = null) }
                viewModelScope.launch { repository.save(updated) }
                showToast("List removed; recipes were kept")
            }.onFailure { error -> _state.update { it.copy(collectionError = error.message ?: "Could not remove that list.") } }
    }

    fun openImport() {
        importJob?.cancel()
        coverJob?.cancel()
        importJob = null
        coverJob = null
        deleteCachedPreview(_state.value.draft?.imageUrl)
        _state.update { it.copy(sheet = "import", importError = null, importStatus = null, draft = null, loading = false, coverLoading = false) }
    }
    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _state.update { it.copy(loading = false, importStatus = null, importError = null) }
    }
    fun discardDraft() {
        coverJob?.cancel()
        coverJob = null
        deleteCachedPreview(_state.value.draft?.imageUrl)
        _state.update { it.copy(sheet = "import", draft = null, importError = null, importStatus = null, loading = false, coverLoading = false) }
    }
    fun setImportUrl(value: String) { _state.update { it.copy(importUrl = value, importError = null) } }
    fun importLink() {
        val url = _state.value.importUrl.trim()
        if (url.isBlank()) { _state.update { it.copy(importError = "Enter a complete recipe link.") }; return }
        _state.update { it.copy(loading = true, importStatus = "Reading the recipe page", importError = null) }
        importJob = viewModelScope.launch {
            repository.importRecipe(url).onSuccess { draft -> _state.update { it.copy(loading = false, importStatus = null, draft = draft, sheet = "preview") } }
                .onFailure { error -> finishImportFailure(error, "Could not import this page.") }
        }
    }

    fun parsePastedText(text: String) {
        _state.update { it.copy(loading = true, importStatus = "Organizing the recipe text", importError = null) }
        importJob = viewModelScope.launch {
            runCatching { RecipeParser.fromText(text, _state.value.importUrl) }
                .onSuccess { draft -> _state.update { it.copy(loading = false, importStatus = null, draft = draft, sheet = "preview", importError = null) } }
                .onFailure { error -> finishImportFailure(error, "No recipe details found.") }
        }
    }

    fun createManual(title: String, ingredientsText: String, directionsText: String) {
        val ingredients = ingredientsText.lines().mapIndexedNotNull { index, line ->
            val clean = line.trim()
            if (clean.isBlank()) null else app.mise.recipebox.model.Ingredient("manual-${index}-${clean.hashCode()}", "", clean)
        }
        val steps = directionsText.lines().mapNotNull { line ->
            line.trim().replace(Regex("^\\d+[.)]\\s*"), "").takeIf(String::isNotBlank)
        }
        _state.update { it.copy(draft = RecipeDraft(title = title.ifBlank { "Untitled recipe" }, source = "Added by you", tags = listOf("Homemade"), ingredients = ingredients, steps = steps), importError = null) }
    }

    fun updateDraftTitle(value: String) {
        val clean = value.replace(Regex("[\\r\\n]+"), " ").take(120)
        _state.update { state -> state.copy(draft = state.draft?.copy(title = clean)) }
    }

    fun updateDraftImage(value: String) {
        if (_state.value.draft == null) return
        coverJob?.cancel()
        _state.update { it.copy(coverLoading = true) }
        coverJob = viewModelScope.launch(Dispatchers.IO) {
            runCatching { copyImageToPreview(Uri.parse(value)) }
                .onSuccess { imageUrl ->
                    if (_state.value.draft == null) {
                        deleteCachedPreview(imageUrl)
                        _state.update { it.copy(coverLoading = false) }
                        return@onSuccess
                    }
                    val oldImageUrl = _state.value.draft?.imageUrl
                    if (oldImageUrl != imageUrl) deleteCachedPreview(oldImageUrl)
                    _state.update { state -> state.copy(draft = state.draft?.copy(imageUrl = imageUrl), coverLoading = false) }
                }
                .onFailure { error ->
                    if (error !is CancellationException) {
                        _state.update { it.copy(coverLoading = false) }
                        showToast(error.message ?: "Could not use that image")
                    }
                }
        }
    }

    fun removeDraftImage() {
        coverJob?.cancel()
        coverJob = null
        deleteCachedPreview(_state.value.draft?.imageUrl)
        _state.update { state -> state.copy(draft = state.draft?.copy(imageUrl = null, postImageUrls = emptyList()), coverLoading = false) }
    }

    fun analyzePhoto(path: String) {
        importJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, importStatus = "Reading the recipe photo", importError = null) }
            runCatching {
                val text = app.mise.recipebox.share.OcrAnalyzer(context).analyze(path, deleteAfter = true)
                RecipeParser.fromPhotoText(listOf(text))
            }.onSuccess { draft -> _state.update { it.copy(loading = false, importStatus = null, draft = draft, sheet = "preview") } }
                .onFailure { error -> finishImportFailure(error, "Could not read that photo.") }
        }
    }

    fun analyzePhotoUri(value: String) {
        importJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, importStatus = "Reading the recipe photo", importError = null) }
            val file = java.io.File(context.cacheDir, "photo-${System.currentTimeMillis()}.img")
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(value))?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: error("Mise could not open that photo.")
                OcrAnalyzer(context).analyze(file.absolutePath, deleteAfter = true)
            }.mapCatching { RecipeParser.fromPhotoText(listOf(it)) }
                .onSuccess { draft -> _state.update { it.copy(loading = false, importStatus = null, draft = draft, sheet = "preview") } }
                .onFailure { error -> file.delete(); finishImportFailure(error, "Could not read that photo.") }
        }
    }

    fun analyzeDocument(value: String) {
        importJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, importStatus = "Reading and organizing the document", importError = null) }
            runCatching { DocumentAnalyzer(context).analyze(Uri.parse(value)) }
                .mapCatching { document ->
                    try {
                        RecipeParser.fromDocumentText(document.text, document.titleHint).let { draft ->
                            if (draft.imageUrl.isNullOrBlank()) draft.copy(imageUrl = document.previewImageUrl) else draft
                        }
                    } catch (error: Throwable) {
                        deleteCachedPreview(document.previewImageUrl)
                        throw error
                    }
                }
                .onSuccess { draft -> _state.update { it.copy(loading = false, importStatus = null, draft = draft, sheet = "preview") } }
                .onFailure { error -> finishImportFailure(error, "Could not read that document.") }
        }
    }

    fun saveDraft(collection: String) {
        val draft = _state.value.draft ?: return
        val savedDraft = draft.copy(imageUrl = persistDraftImage(draft.imageUrl))
        val recipe = materializeRecipe(savedDraft, collection)
        mutate { state -> state.copy(recipes = listOf(recipe) + state.recipes, collections = (state.collections + collection).distinct()) }
        _state.update { it.copy(sheet = "detail", selectedRecipe = recipe, draft = null, importError = null, importStatus = null) }
        showToast("Saved to $collection")
    }

    fun setTheme(value: String) {
        _state.update { it.copy(theme = value) }
        viewModelScope.launch { repository.setTheme(value) }
    }

    fun connectServer(value: String) {
        _state.update { it.copy(loading = true, importError = null) }
        viewModelScope.launch {
            repository.connect(value).onSuccess { state -> _state.update { it.copy(appState = state, serverUrl = value.trim().trimEnd('/'), loading = false, sheet = null) }; showToast("Server connected") }
                .onFailure { error -> _state.update { it.copy(loading = false, importError = error.message ?: "Could not connect to server.") } }
        }
    }

    fun openSettings() { _state.update { it.copy(sheet = "settings", importError = null) } }
    fun startCook(recipe: Recipe) { _state.update { it.copy(selectedRecipe = recipe, sheet = "cook", cookStep = 0, timerSeconds = 0, timerRunning = false) } }
    fun nextStep() { _state.update { it.copy(cookStep = (it.cookStep + 1).coerceAtMost((it.selectedRecipe?.steps?.size ?: 1) - 1)) } }
    fun previousStep() { _state.update { it.copy(cookStep = (it.cookStep - 1).coerceAtLeast(0)) } }
    fun setTimer(minutes: Int) { _state.update { it.copy(timerSeconds = minutes * 60, timerRunning = false) } }
    fun toggleTimer() {
        val running = !_state.value.timerRunning
        _state.update { it.copy(timerRunning = running) }
        timerJob?.cancel()
        if (running) timerJob = viewModelScope.launch {
            while (_state.value.timerRunning && _state.value.timerSeconds > 0) { delay(1000); _state.update { it.copy(timerSeconds = (it.timerSeconds - 1).coerceAtLeast(0), timerRunning = it.timerSeconds > 1) } }
        }
    }

    fun consumeShare(payload: SharePayload, analyzer: OcrAnalyzer, onConsumed: () -> Unit) {
        onConsumed()
        importJob = viewModelScope.launch {
            _state.update { it.copy(sheet = "import", loading = true, importStatus = "Reading the shared recipe", importError = null) }
            val imageText = payload.imagePath?.let { path -> runCatching { analyzer.analyze(path, deleteAfter = true) }.getOrElse { "" } }.orEmpty()
            val text = imageText.ifBlank { payload.text }
            if (text.isBlank()) {
                _state.update { it.copy(loading = false, importStatus = null, importError = "Mise could not read that shared item.") }
            } else if (imageText.isNotBlank()) {
                runCatching { RecipeParser.fromPhotoText(listOf(imageText)) }
                    .onSuccess { draft -> _state.update { it.copy(loading = false, importStatus = null, draft = draft, sheet = "preview") } }
                    .onFailure { error -> finishImportFailure(error, "No recipe details found.") }
            } else {
                repository.importShared(text).onSuccess { draft -> _state.update { it.copy(loading = false, importStatus = null, draft = draft, sheet = "preview") } }
                    .onFailure { error -> finishImportFailure(error, "No recipe details found.") }
            }
        }
    }

    private fun finishImportFailure(error: Throwable, fallback: String) {
        if (error is CancellationException) return
        _state.update { it.copy(loading = false, importStatus = null, importError = error.message ?: fallback) }
    }

    private suspend fun copyImageToPreview(uri: Uri): String {
        val directory = java.io.File(context.cacheDir, "recipe-previews").apply { mkdirs() }
        val target = java.io.File(directory, "chosen-${System.currentTimeMillis()}-${uri.hashCode().toUInt()}.img")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        require(total <= maxCoverImageBytes) { "Choose an image smaller than 15 MB." }
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Mise could not open that image.")
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(target.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Choose a valid image file." }
            return target.toURI().toString()
        } catch (error: Throwable) {
            target.delete()
            throw error
        }
    }

    private fun persistDraftImage(value: String?): String? {
        val source = cachedPreviewFile(value) ?: return value
        return runCatching {
            val directory = java.io.File(context.filesDir, "recipe-images").apply { mkdirs() }
            val target = java.io.File(directory, "recipe-${System.currentTimeMillis()}-${source.name}")
            if (!source.renameTo(target)) {
                source.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                source.delete()
            }
            target.toURI().toString()
        }.getOrDefault(value)
    }

    private fun deleteCachedPreview(value: String?) {
        cachedPreviewFile(value)?.delete()
    }

    private fun deleteRecipeImages(recipe: Recipe) {
        (listOfNotNull(recipe.imageUrl) + recipe.postImageUrls)
            .distinct()
            .mapNotNull(::persistedRecipeImage)
            .forEach(java.io.File::delete)
    }

    private fun cachedPreviewFile(value: String?): java.io.File? {
        val uri = value?.let(Uri::parse) ?: return null
        if (uri.scheme != "file") return null
        val file = uri.path?.let { java.io.File(it) } ?: return null
        val previewDirectory = java.io.File(context.cacheDir, "recipe-previews").canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.parentFile == previewDirectory }
    }

    private fun persistedRecipeImage(value: String?): java.io.File? {
        val uri = value?.let(Uri::parse) ?: return null
        if (uri.scheme != "file") return null
        val file = uri.path?.let { java.io.File(it) } ?: return null
        val imageDirectory = java.io.File(context.filesDir, "recipe-images").canonicalFile
        val candidate = runCatching { file.canonicalFile }.getOrNull() ?: return null
        return candidate.takeIf { it.parentFile == imageDirectory }
    }

    private companion object {
        const val maxCoverImageBytes = 15_000_000L
        val projectSourceUrl = BuildConfig.MISE_SOURCE_URL
        val privacyPolicyUrl = "$projectSourceUrl/blob/main/PRIVACY.md"
        val licenseUrl = "$projectSourceUrl/blob/main/LICENSE"
        val thirdPartyNoticesUrl = "$projectSourceUrl/blob/main/THIRD_PARTY_NOTICES.md"
    }

    private fun mutate(change: (AppState) -> AppState) {
        _state.update { current -> current.copy(appState = change(current.appState).normalized()) }
        viewModelScope.launch { repository.save(_state.value.appState) }
    }

    private fun showToast(message: String) {
        toastJob?.cancel()
        _state.update { it.copy(toast = message) }
        toastJob = viewModelScope.launch { delay(2200); _state.update { it.copy(toast = null) } }
    }

    private fun updatedRecipe(recipe: Recipe?, id: String, ingredients: List<Ingredient>): Recipe? = recipe?.takeIf { it.id == id }?.copy(ingredients = ingredients) ?: recipe
    private fun groceryCategory(name: String): String = when {
        name.contains(Regex("(?i)chicken|salmon|beef|fish|egg")) -> "Protein"
        name.contains(Regex("(?i)spinach|tomato|lime|lemon|basil|bok choy|potato")) -> "Produce"
        else -> "Pantry"
    }

}
