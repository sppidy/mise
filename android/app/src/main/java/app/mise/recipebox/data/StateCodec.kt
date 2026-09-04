// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.data

import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.Difficulty
import app.mise.recipebox.model.GroceryItem
import app.mise.recipebox.model.ImagePosition
import app.mise.recipebox.model.Ingredient
import app.mise.recipebox.model.Recipe
import org.json.JSONArray
import org.json.JSONObject

/** Stable JSON codec for the server's unchanged AppState contract. */
object StateCodec {
    fun decode(raw: String): AppState = decode(JSONObject(raw)).normalized()

    fun decode(root: JSONObject): AppState {
        val collections = root.optJSONArray("collections").strings()
        val recipes = root.optJSONArray("recipes").objects().map { recipe(it, collections.firstOrNull() ?: "My recipes") }
        val grocery = root.optJSONArray("grocery").objects().map { item(it) }
        return AppState(recipes = recipes, grocery = grocery, collections = collections)
    }

    fun encode(state: AppState): String = encodeObject(state.normalized()).toString()

    fun encodeObject(state: AppState): JSONObject = JSONObject().apply {
        put("collections", JSONArray(state.collections))
        put("recipes", JSONArray(state.recipes.map(::recipeObject)))
        put("grocery", JSONArray(state.grocery.map(::groceryObject)))
    }

    private fun recipe(value: JSONObject, defaultCollection: String): Recipe {
        val ingredients = value.optJSONArray("ingredients").objects().mapIndexed { index, ingredient ->
            Ingredient(
                id = ingredient.optString("id", "ingredient-$index"),
                quantity = ingredient.optString("quantity", ""),
                name = ingredient.optString("name", "").ifBlank { "Ingredient" },
                group = ingredient.optStringOrNull("group"),
            )
        }
        val difficulty = when (value.optString("difficulty", "Easy").lowercase()) {
            "medium" -> Difficulty.MEDIUM
            "project" -> Difficulty.PROJECT
            else -> Difficulty.EASY
        }
        val position = when (value.optString("imagePosition", "tl").lowercase()) {
            "tr" -> ImagePosition.TR
            "bl" -> ImagePosition.BL
            "br" -> ImagePosition.BR
            else -> ImagePosition.TL
        }
        return Recipe(
            id = value.optString("id", "recipe-${value.optString("title", "untitled").hashCode()}"),
            title = value.optString("title", "Untitled recipe"),
            description = value.optString("description", ""),
            source = value.optString("source", "Added by you"),
            sourceUrl = value.optStringOrNull("sourceUrl"),
            imageUrl = value.optStringOrNull("imageUrl"),
            postImageUrls = value.optJSONArray("postImageUrls").strings(),
            postMedia = value.optStringOrNull("postMedia"),
            time = value.optInt("time", 30),
            servings = value.optInt("servings", 4),
            difficulty = difficulty,
            collection = value.optString("collection", defaultCollection),
            favorite = value.optBoolean("favorite", false),
            imagePosition = position,
            tags = value.optJSONArray("tags").strings(),
            ingredients = ingredients,
            steps = value.optJSONArray("steps").strings(),
            createdAt = value.optString("createdAt", ""),
        )
    }

    private fun recipeObject(value: Recipe): JSONObject = JSONObject().apply {
        put("id", value.id)
        put("title", value.title)
        put("description", value.description)
        put("source", value.source)
        putNullable("sourceUrl", value.sourceUrl)
        putNullable("imageUrl", value.imageUrl)
        put("postImageUrls", JSONArray(value.postImageUrls))
        putNullable("postMedia", value.postMedia)
        put("time", value.time)
        put("servings", value.servings)
        put("difficulty", value.difficulty.name.lowercase().replaceFirstChar { it.uppercase() })
        put("collection", value.collection)
        put("favorite", value.favorite)
        put("imagePosition", value.imagePosition.name.lowercase())
        put("tags", JSONArray(value.tags))
        put("ingredients", JSONArray(value.ingredients.map {
            JSONObject().apply {
                put("id", it.id)
                put("quantity", it.quantity)
                put("name", it.name)
                putNullable("group", it.group)
            }
        }))
        put("steps", JSONArray(value.steps))
        put("createdAt", value.createdAt)
    }

    private fun item(value: JSONObject): GroceryItem = GroceryItem(
        id = value.optString("id", "grocery-${value.optString("name", "item").hashCode()}"),
        name = value.optString("name", "Item"),
        amount = value.optString("amount", ""),
        checked = value.optBoolean("checked", false),
        category = value.optString("category", "Other"),
    )

    private fun groceryObject(value: GroceryItem): JSONObject = JSONObject().apply {
        put("id", value.id)
        put("name", value.name)
        put("amount", value.amount)
        put("checked", value.checked)
        put("category", value.category)
    }

    private fun JSONObject.putNullable(key: String, value: String?) {
        if (value == null) put(key, JSONObject.NULL) else put(key, value)
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (isNull(key)) null else optString(key, "").takeIf(String::isNotBlank)

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else {
        buildList(length()) { for (index in 0 until length()) optString(index, "").takeIf(String::isNotBlank)?.let(::add) }
    }

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else {
        buildList(length()) { for (index in 0 until length()) optJSONObject(index)?.let(::add) }
    }
}
