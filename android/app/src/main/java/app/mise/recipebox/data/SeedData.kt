// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.data

import app.mise.recipebox.model.AppState
import app.mise.recipebox.model.Difficulty
import app.mise.recipebox.model.ImagePosition
import app.mise.recipebox.model.Ids
import app.mise.recipebox.model.Ingredient
import app.mise.recipebox.model.Recipe
import app.mise.recipebox.model.GroceryItem

/** Deterministic demo state used only for a new local install. */
object SeedData {
    private fun recipe(
        title: String,
        description: String,
        source: String,
        time: Int,
        servings: Int,
        difficulty: Difficulty,
        collection: String,
        favorite: Boolean,
        position: ImagePosition,
        tags: List<String>,
        ingredients: List<Pair<String, String>>,
        steps: List<String>,
    ) = Recipe(
        id = Ids.next("seed"), title = title, description = description, source = source,
        time = time, servings = servings, difficulty = difficulty, collection = collection,
        favorite = favorite, imagePosition = position, tags = tags,
        ingredients = ingredients.mapIndexed { index, (quantity, name) -> Ingredient("seed-$index-$title", quantity, name) },
        steps = steps,
        createdAt = "2025-01-01T00:00:00.000Z",
    )

    fun state(): AppState = AppState(
        collections = listOf("Weeknight wins", "Slow Sundays", "Baking shelf"),
        grocery = listOf(
            GroceryItem(Ids.next("grocery"), "Baby spinach", "200 g", false, "Produce"),
            GroceryItem(Ids.next("grocery"), "Cherry tomatoes", "2 punnets", false, "Produce"),
            GroceryItem(Ids.next("grocery"), "Coconut milk", "1 can", true, "Pantry"),
            GroceryItem(Ids.next("grocery"), "Salmon fillets", "4", false, "Protein"),
        ),
        recipes = listOf(
            recipe(
                "Miso glazed salmon bowls",
                "Sticky, savory salmon with steamed rice and crisp greens. A genuinely fast dinner that still feels special.",
                "Mise kitchen", 28, 4, Difficulty.EASY, "Weeknight wins", true, ImagePosition.TL,
                listOf("Dinner", "High protein"),
                listOf("4" to "salmon fillets", "3 tbsp" to "white miso", "2 tbsp" to "soy sauce", "2 cups" to "cooked jasmine rice", "2 heads" to "baby bok choy"),
                listOf("Whisk miso, soy sauce, a spoonful of warm water, and a pinch of sugar until glossy.", "Brush the salmon generously and roast at 220°C for 10–12 minutes.", "Sear the bok choy cut-side down until charred, then add a splash of water and cover for 2 minutes.", "Divide rice between bowls, add salmon and greens, then spoon over the remaining glaze."),
            ),
            recipe(
                "Roasted tomato rigatoni",
                "Blistered tomatoes folded into a silky sauce with basil and a little cream.",
                "Imported from a blog", 35, 4, Difficulty.EASY, "Weeknight wins", false, ImagePosition.TR,
                listOf("Vegetarian", "Pasta"),
                listOf("400 g" to "rigatoni", "500 g" to "cherry tomatoes", "4 cloves" to "garlic", "120 ml" to "double cream", "1 handful" to "fresh basil"),
                listOf("Roast tomatoes and garlic with olive oil at 220°C until blistered.", "Boil rigatoni in well-salted water until just shy of al dente.", "Crush the roasted tomatoes, stir in cream, and simmer for 3 minutes.", "Toss pasta through the sauce with basil and enough pasta water to make it glossy."),
            ),
            recipe(
                "Brown butter banana bread",
                "Deeply nutty, not too sweet, with a crisp demerara sugar top.",
                "Family recipe", 70, 8, Difficulty.MEDIUM, "Baking shelf", true, ImagePosition.BL,
                listOf("Baking", "Make ahead"),
                listOf("3" to "very ripe bananas", "125 g" to "unsalted butter", "180 g" to "plain flour", "2" to "eggs", "100 g" to "brown sugar"),
                listOf("Brown the butter over medium heat and cool for 10 minutes.", "Mash bananas, then whisk in sugar, eggs, and brown butter.", "Fold in flour, baking soda, and salt only until no dry streaks remain.", "Bake at 175°C for 50–55 minutes and cool completely before slicing."),
            ),
            recipe(
                "Coconut chickpea curry",
                "A pantry-friendly curry with coconut, sweet potato, and lime.",
                "Mise kitchen", 30, 4, Difficulty.EASY, "Slow Sundays", false, ImagePosition.BR,
                listOf("Vegan", "One pot"),
                listOf("2 cans" to "chickpeas", "1 can" to "coconut milk", "1 large" to "sweet potato", "2 tbsp" to "curry paste", "1" to "lime"),
                listOf("Fry curry paste in a little oil until fragrant.", "Add diced sweet potato, chickpeas, coconut milk, and 200 ml water.", "Simmer uncovered for 20 minutes until the potato is tender and the sauce thickens.", "Season with lime juice and salt, then serve with rice or flatbread."),
            ),
        ),
    )
}
