// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.model

enum class MiseView { HOME, RECIPES, FAVORITES, COLLECTIONS, GROCERY }
enum class ThemePreference { SYSTEM, LIGHT, DARK }
enum class Difficulty { EASY, MEDIUM, PROJECT }
enum class ImagePosition { TL, TR, BL, BR }

data class Ingredient(
    val id: String,
    val quantity: String = "",
    val name: String,
    val group: String? = null,
)

data class Recipe(
    val id: String,
    val title: String,
    val description: String,
    val source: String,
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val postImageUrls: List<String> = emptyList(),
    val postMedia: String? = null,
    val time: Int = 30,
    val servings: Int = 4,
    val difficulty: Difficulty = Difficulty.EASY,
    val collection: String,
    val favorite: Boolean = false,
    val imagePosition: ImagePosition = ImagePosition.TL,
    val tags: List<String> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
    val createdAt: String,
)

data class GroceryItem(
    val id: String,
    val name: String,
    val amount: String = "",
    val checked: Boolean = false,
    val category: String = "Other",
)

data class AppState(
    val recipes: List<Recipe> = emptyList(),
    val grocery: List<GroceryItem> = emptyList(),
    val collections: List<String> = emptyList(),
) {
    fun normalized(): AppState = StateNormalizer.normalize(this)
}

object CollectionEditor {
    private fun cleanName(value: String): String {
        val name = value.trim().replace(Regex("\\s+"), " ")
        require(name.isNotBlank()) { "Enter a list name." }
        require(name.length <= 60) { "Keep list names under 60 characters." }
        return name
    }

    fun create(state: AppState, value: String): AppState {
        val name = cleanName(value)
        require(state.collections.none { it.equals(name, ignoreCase = true) }) { "A list with that name already exists." }
        return state.copy(collections = state.collections + name).normalized()
    }

    fun rename(state: AppState, original: String, value: String): AppState {
        require(state.collections.contains(original)) { "That list no longer exists." }
        val name = cleanName(value)
        require(state.collections.none { it != original && it.equals(name, ignoreCase = true) }) { "A list with that name already exists." }
        return state.copy(
            collections = state.collections.map { if (it == original) name else it },
            recipes = state.recipes.map { if (it.collection == original) it.copy(collection = name) else it },
        ).normalized()
    }

    fun delete(state: AppState, value: String): AppState {
        require(state.collections.contains(value)) { "That list no longer exists." }
        val remaining = state.collections.filterNot { it == value }
        val destination = remaining.firstOrNull() ?: "My recipes"
        return state.copy(
            collections = (remaining + destination).distinct(),
            recipes = state.recipes.map { if (it.collection == value) it.copy(collection = destination) else it },
        ).normalized()
    }
}

data class RecipeDraft(
    val title: String = "",
    val description: String = "",
    val source: String = "",
    val sourceUrl: String? = null,
    val imageUrl: String? = null,
    val postImageUrls: List<String> = emptyList(),
    val postMedia: String? = null,
    val time: Int = 30,
    val servings: Int = 4,
    val difficulty: Difficulty = Difficulty.EASY,
    val tags: List<String> = emptyList(),
    val ingredients: List<Ingredient> = emptyList(),
    val steps: List<String> = emptyList(),
)

object Ids {
    fun next(prefix: String = "mise"): String = "$prefix-${java.util.UUID.randomUUID()}"
}

object StateNormalizer {
    private fun score(recipe: Recipe): Int = recipe.ingredients.size * 4 + recipe.steps.size * 4 +
        (if (!recipe.imageUrl.isNullOrBlank()) 3 else 0) + (if (recipe.title.length < 140) 1 else 0)

    fun normalize(state: AppState): AppState {
        val output = mutableListOf<Recipe>()
        val indexes = mutableMapOf<String, Int>()
        state.recipes.forEach { original ->
            val recipe = original.copy(
                title = original.title.ifBlank { "Untitled recipe" },
                description = original.description.ifBlank { "A recipe saved to your recipe box." },
                source = original.source.ifBlank { "Added by you" },
                collection = original.collection.ifBlank { state.collections.firstOrNull() ?: "My recipes" },
                time = original.time.coerceAtLeast(1),
                servings = original.servings.coerceAtLeast(1),
            )
            val source = recipe.sourceUrl?.trim()?.trimEnd('/')
            if (source.isNullOrBlank()) {
                output += recipe
                return@forEach
            }
            val existingIndex = indexes[source]
            if (existingIndex == null) {
                indexes[source] = output.size
                output += recipe
            } else {
                val existing = output[existingIndex]
                if (score(recipe) > score(existing)) {
                    output[existingIndex] = recipe.copy(favorite = recipe.favorite || existing.favorite)
                } else if (recipe.favorite && !existing.favorite) {
                    output[existingIndex] = existing.copy(favorite = true)
                }
            }
        }
        val collections = (state.collections + output.map { it.collection })
            .map(String::trim).filter(String::isNotBlank).distinct()
        return state.copy(recipes = output, collections = collections)
    }
}

fun materializeRecipe(draft: RecipeDraft, collection: String): Recipe {
    val positions = ImagePosition.values()
    val now = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date())
    return Recipe(
        id = Ids.next("recipe"),
        title = draft.title.ifBlank { "Untitled recipe" },
        description = draft.description.ifBlank { "A recipe saved to your recipe box." },
        source = draft.source.ifBlank { "Added by you" },
        sourceUrl = draft.sourceUrl,
        imageUrl = draft.imageUrl,
        postImageUrls = draft.postImageUrls,
        postMedia = draft.postMedia,
        time = draft.time.coerceAtLeast(1),
        servings = draft.servings.coerceAtLeast(1),
        difficulty = draft.difficulty,
        collection = collection.ifBlank { "My recipes" },
        imagePosition = positions[(draft.title.hashCode().ushr(1) % positions.size)],
        tags = (draft.tags.ifEmpty { listOf("Homemade") }).distinct().take(4),
        ingredients = draft.ingredients,
        steps = draft.steps,
        createdAt = now,
    )
}
