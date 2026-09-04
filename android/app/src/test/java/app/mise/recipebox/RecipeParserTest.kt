// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox

import app.mise.recipebox.importer.RecipeParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeParserTest {
    @Test fun parsesBulletsAndNumberedDirections() {
        val draft = RecipeParser.fromText("""
            Lemon rice
            Ingredients:
            - 2 cups cooked rice
            - 1 lime
            - 2 tbsp olive oil
            Directions:
            1. Warm the oil in a pan.
            2. Fold in the rice and finish with lime.
        """.trimIndent(), "https://instagram.com/p/test")
        assertEquals("Lemon rice", draft.title)
        assertEquals(3, draft.ingredients.size)
        assertEquals(2, draft.steps.size)
        assertEquals("Instagram", draft.source)
    }

    @Test fun rejectsEmptyText() {
        val failure = runCatching { RecipeParser.fromText("just words") }.exceptionOrNull()
        assertTrue(failure != null)
    }

    @Test fun extractsFirstSharedUrlAndTrimsSentencePunctuation() {
        assertEquals(
            "https://example.com/recipes/pasta?from=share",
            RecipeParser.firstUrl("Try this: https://example.com/recipes/pasta?from=share). Another https://example.org"),
        )
        assertNull(RecipeParser.firstUrl("No link in this caption"))
    }

    @Test fun distinguishesRecipeCaptionFromLongLinkOnlyShare() {
        assertTrue(RecipeParser.hasRecipeSignals("Ingredients:\n2 cups rice\n1 tsp salt\nhttps://example.com/rice"))
        assertTrue(RecipeParser.hasRecipeSignals("1. Mix everything together.\n2. Bake until golden brown."))
        assertTrue(!RecipeParser.hasRecipeSignals("Try this very good recipe https://example.com/a/very/long/social/share/link"))
    }

    @Test fun parsesHandwrittenPhotoOcrIntoGroupedIngredients() {
        val draft = RecipeParser.fromPhotoText(listOf("""
            Crowd favorites
            Honey Grarlic
            •2 tbsp honey
            •| tbsp Soy Sauce
            •| tsp garlie
            • Splash lemon juice
            Sweet Chili
            •2 tbsp Sweet chili Sauce.
            •| tbsp Soy Sauce
            •| tsp garlic
            •| tsp Sesame oil
            Garlic Parmesan
            •2 tbsp olive oil
            • 2 tbsp grated parmesan
            •| tsp garlic
            • Black pepper
            Teriyaki
            •2 tbsp Soy Sauce
            •| tbsp brown Suqar
            •l tsp ginger
            •| tsp garic
        """.trimIndent()))

        assertEquals("Crowd Favorites", draft.title)
        assertEquals(14, draft.ingredients.size)
        assertEquals(0, draft.steps.size)
        assertEquals(listOf("Honey Garlic", "Sweet Chili", "Garlic Parmesan", "Teriyaki"), draft.ingredients.mapNotNull { it.group }.distinct())
        assertTrue(draft.ingredients.any { it.quantity == "1 TSP" && it.name == "sesame oil" })
        assertTrue(draft.ingredients.any { it.quantity == "1 TBSP" && it.name == "brown sugar" })
    }

    @Test fun parsesStructuredRecipeDocumentWithoutTreatingProseAsIngredients() {
        val draft = RecipeParser.fromDocumentText("""
            HEALTH & FITNESS
            Healthy Biryani for Weight Loss
            A lighter take on a beloved classic - this high-protein, lower-fat biryani built for health-conscious home cooks.
            What You Will Need
            Every ingredient here is chosen with purpose - lean protein, anti-inflammatory spices, and fresh herbs that build bold flavour without heavy oil or cream.
            Chicken Breast (Diced)
            Turmeric powder
            Salt & lemon juice
            Garlic, Ginger, Green Chilli
            Onion & tomato
            Mint & coriander leaves
            Olive oil, Bay leaves, Cloves
            Pre-soaked Basmati rice
            Jeera, Chilli & Garam masala
            Chicken Marination Green Masala Paste Cooking Essentials
            Step-by-Step Cooking Method
            Follow these seven straightforward steps to bring your healthy biryani together.
            Heat a pan on medium heat and lightly brush with olive oil.
            Add bay leaves, cloves, and sliced onions. Stir-fry until the onions soften.
            In a glass bowl, combine diced chicken breast with turmeric powder,
            salt, and freshly squeezed lemon juice. Mix thoroughly.
            Add the marinated chicken to the pan and stir until opaque.
            Add the pre-soaked rice and mix gently.
            Add garlic, ginger, green chilli, chopped onion, chopped tomato,
            fresh mint leaves, and coriander leaves into a mixer jar. Blend until smooth.
            3
            1
            4
            2
            Sauté Aromatics
            Marinate the Chicken
            Cook Chicken & Add Rice
            Blend the Green Masala Paste
            Simmer, Steam & Serve
            Add Spices & Simmer
            Pour the prepared green masala paste into the pan. Add water and season with salt, jeera powder, chilli powder, and garam masala.
            Cover & Steam to Perfection
            Place a tight-fitting lid on the pan and reduce heat. Cook until the rice is tender and water is absorbed.
            Garnish & Serve
            Finish with fresh coriander leaves and a lemon wedge. Serve hot.
            Nutrition at a Glance
            450 KCAL
            45g protein
            40g carbohydrates
            8g Total Fat
        """.trimIndent(), "HEALTHY BIRYANI RECIPE")

        assertEquals("Healthy Biryani for Weight Loss", draft.title)
        assertEquals(9, draft.ingredients.size)
        assertEquals(7, draft.steps.size)
        assertEquals(listOf("Chicken Marination", "Green Masala Paste", "Cooking Essentials"), draft.ingredients.mapNotNull { it.group }.distinct())
        assertTrue(draft.ingredients.any { it.name == "Chicken Breast (Diced)" && it.quantity.isBlank() })
        assertTrue(draft.steps.toString(), draft.steps.first().startsWith("Marinate the Chicken:"))
        assertTrue(draft.ingredients.none { it.name.contains("Total Fat", true) || it.name.contains("lighter take", true) })
        assertTrue(draft.steps.none { it.contains("Nutrition", true) || it.contains("450") })
    }

    @Test fun combinesRepeatedPdfRecipePagesAndParsesIngredientTables() {
        val pageBreak = "[[MISE_PDF_PAGE_BREAK]]"
        val draft = RecipeParser.fromDocumentText("""
            @samplecook Page 1
            2 EASY HIGH-PROTEIN
            DESSERTS
            5 MINUTES • FEW INGREDIENTS • NO OVEN
            Detailed recipes with measured ingredients and step-by-step methods.
            $pageBreak
            @samplecook Page 2
            Chocolate Protein Mug Cake
            1 mug cake (1 serving)
            Ingredients
            Ingredient Amount
            Banana 60 g
            Protein powder 20 g
            Unsweetened cocoa 10 g
            Egg 1 large
            Low-fat milk 30 ml
            Step-by-step method
            Step 1: Mash banana in a microwave-safe mug.
            Step 2: Add protein powder and cocoa; mix well.
            Step 3: Rest for 1 minute before eating; avoid overcooking. Nutrition per serving Calories: ~260 kcal Macros: Protein ~27 g
            Storage
            Refrigerate in a clean airtight container.
            @samplecook Page 2
            $pageBreak
            @samplecook Page 3
            Strawberry Cheesecake Yogurt Bowl
            1 bowl (1 serving)
            Ingredients
            Ingredient
            Amount
            Non-fat Greek yogurt
            150 g
            Strawberries
            50 g
            Honey
            5g
            Step-by-step method
            Step 1: Mix Greek yogurt until smooth.
            Step 2: Top with strawberries and drizzle with honey.
            Nutrition per serving
            Calories: ~220 kcal
            @samplecook Page 3
            $pageBreak
        """.trimIndent(), "2_Easy_High_Protein_Desserts_Detailed_Recipes_samplecook")

        assertEquals("2 Easy High Protein Desserts", draft.title)
        assertEquals("2 recipes imported from a recipe document.", draft.description)
        assertEquals(1, draft.servings)
        assertEquals(8, draft.ingredients.size)
        assertEquals(5, draft.steps.size)
        assertEquals(
            listOf("Chocolate Protein Mug Cake", "Strawberry Cheesecake Yogurt Bowl"),
            draft.ingredients.mapNotNull { it.group }.distinct(),
        )
        assertTrue(draft.ingredients.any { it.quantity == "20 g" && it.name == "Protein powder" })
        assertTrue(draft.ingredients.any { it.quantity == "1 large" && it.name == "Egg" })
        assertTrue(draft.ingredients.none { it.name.contains("Ingredient Amount", true) })
        assertTrue(draft.steps.all { it.contains(" — ") })
        assertTrue(draft.steps.none { it.contains("Nutrition", true) || it.contains("Calories", true) })
    }

    @Test fun parsesLayoutlessIllustratorRecipeBookPages() {
        val pageBreak = "[[MISE_PDF_PAGE_BREAK]]"
        val draft = RecipeParser.fromDocumentText("""
            Author Zach Rocheleau, creator of the flexible dieting lifestyle
            enjoy these 2 chalupa recipes that taste great
            $pageBreak
            Spicy Elote Chicken Chalupas Spicy Elote Chicken Chalupas
            Ingredients for Elote Sauce:
            • 170 g Plain Non Fat Greek Yogurt
            • Lime Juice and Zest
            • 1 tsp Sea Salt
            Ingredients for 1 Chalupa:
            • 1 Josephs Pita
            • 1/4th Batch Elote Sauce
            • 1 tsp Sea Salt
            • 20 g Cotija Cheese (Parmesan if you
            don’t have it!)
            Pat chicken breast dry, flatten and season both sides.
            Blend all elote sauce ingredients until smooth.
            Assemble chalupas and fill each pita.
            directions
            1 2
            3
            Servings Size: 1 chalupa
            Calories
            270
            $pageBreak
            Buff BuffaaloloChi Chickeken n ChChaalhuupupas
            Ingredients for Buffalo Chicken Dip
            • 907 g Chicken Breast
            • 0.3 Packet Ranch Dip Seasoning
            • 3 g arlic cloves, minced
            • 113 g Franks Red Hot Buffalo Sauce
            Ingredients for 1 Chalupa
            • 1 Josephs Pita
            • 2 Light Laughing Cow Cheese Wedges
            • 3 Sliced Pepperoncini Peppers
            • 1/8th Grinder Slaw
            • Roma Tomato
            Take your chicken breast and pat dry with a paper towel.
            Then flatten. I just use my fist lol.
            Then season both sides and air fry for 9-10 minutes.
            Fold your chalupa tightly and air fry for 4 minutes.
            Once done, add your tomato and enjoy!
            directions
            1
            2
            3
            4
            Servings Size: 1 chalupa
            $pageBreak
        """.trimIndent(), "chalupa-recipe-book")

        assertEquals("Chalupa Recipe Book", draft.title)
        assertEquals("2 recipes imported from a recipe document.", draft.description)
        assertEquals(16, draft.ingredients.size)
        assertEquals(7, draft.steps.size)
        assertEquals(
            listOf("Spicy Elote Chicken Chalupas", "Buffalo Chicken Chalupas"),
            draft.ingredients.mapNotNull { it.group }.distinct(),
        )
        assertTrue(draft.ingredients.any { it.quantity == "1/4th" && it.name == "Batch Elote Sauce" })
        assertTrue(draft.ingredients.any { it.quantity.isBlank() && it.name == "Lime Juice and Zest" })
        assertEquals(2, draft.ingredients.count { it.quantity == "1 tsp" && it.name == "Sea Salt" })
        assertTrue(draft.ingredients.any { it.quantity == "20 g" && it.name == "Cotija Cheese (Parmesan if you don’t have it!)" })
        assertTrue(draft.ingredients.any { it.quantity == "0.3 Packet" && it.name == "Ranch Dip Seasoning" })
        assertTrue(draft.ingredients.any { it.quantity == "3 g" && it.name == "Garlic cloves, minced" })
        assertTrue(draft.ingredients.any { it.quantity == "2" && it.name == "Light Laughing Cow Cheese Wedges" })
        assertTrue(draft.ingredients.any { it.quantity == "3" && it.name == "Sliced Pepperoncini Peppers" })
        assertTrue(draft.ingredients.any { it.quantity == "1/8th" && it.name == "Grinder Slaw" })
        assertTrue(draft.ingredients.none { it.name.contains("Pat chicken", true) })
        assertTrue(draft.steps.none { it.contains("Calories", true) || it.contains("Servings Size", true) })
    }
}
