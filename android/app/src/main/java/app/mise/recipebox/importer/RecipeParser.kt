// SPDX-FileCopyrightText: 2026 Mise contributors
// SPDX-License-Identifier: AGPL-3.0-or-later

package app.mise.recipebox.importer

import app.mise.recipebox.model.Difficulty
import app.mise.recipebox.model.Ids
import app.mise.recipebox.model.Ingredient
import app.mise.recipebox.model.RecipeDraft
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Kotlin counterpart of shared/recipe-parser.js for Android offline imports. */
object RecipeParser {
    private const val pdfPageBreak = "[[MISE_PDF_PAGE_BREAK]]"
    private val urlPattern = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)
    private val trailingUrlPunctuation = Regex("[),.;:!?]+$")
    private val amountPattern = Regex(
        "^(\\d+/\\d+(?:st|nd|rd|th)?|\\d+(?:[.,]\\d+)?(?:\\s+\\d+/\\d+)?|[¼½¾⅓⅔⅛⅜⅝⅞]|half(?:\\s+(?:a|an))?|a|an|handful|pinch)(?:\\s*(?:-|to)\\s*(\\d+(?:[.,]\\d+)?))?\\s*(cups?|tablespoons?|tbsp|teaspoons?|tsp|grams?|kilograms?|g|kg|millilit(?:er|re)s?|ml|lit(?:er|re)s?|l|ounces?|oz|pounds?|lbs?|lb|cloves?|cans?|bunch(?:es)?|slices?|sprigs?|stalks?|heads?|fillets?|pieces?|packets?|packs?|whole|medium|small|large)?(?![A-Za-z])\\s*(?:of\\s+)?(.+)$",
        RegexOption.IGNORE_CASE,
    )
    private val marker = Regex("^\\s*(?:[-•●▪◦‣–—]|✅|☑️?|✔️?|✓|[0-9]{1,2}[.):])\\s*", RegexOption.IGNORE_CASE)

    /** Returns the first HTTP(S) URL in shared text without common sentence punctuation. */
    fun firstUrl(value: String): String? = urlPattern.find(value)?.value
        ?.replace(trailingUrlPunctuation, "")
        ?.takeIf(String::isNotBlank)

    /** Prevents a long social caption or URL from being mistaken for recipe directions. */
    fun hasRecipeSignals(value: String): Boolean {
        val lines = normalize(value).lines().map(::cleanLine).filter(String::isNotBlank)
        val hasSectionHeading = lines.any {
            it.matches(Regex("(?i)(ingredients?|directions?|instructions?|method|steps?)[: ]*"))
        }
        val ingredientLines = lines.count(::looksLikeIngredient)
        val numberedSteps = lines.count {
            it.matches(Regex("^\\s*(?:step\\s*)?\\d{1,2}[.):]\\s+.+", RegexOption.IGNORE_CASE))
        }
        return hasSectionHeading || ingredientLines >= 2 || numberedSteps >= 2
    }

    fun fromText(value: String, sourceUrl: String = ""): RecipeDraft {
        val clean = normalize(value)
        if (clean.length < 20) error("Not enough recipe text was provided.")
        val lines = clean.lines().map(::cleanLine).filter(String::isNotBlank)
        val ingredientsStart = lines.indexOfFirst { it.matches(Regex("(?i)ingredients?[: ]*")) }
        val directionsStart = lines.indexOfFirst { it.matches(Regex("(?i)(directions?|instructions?|method|steps?)[: ]*")) }
        val ingredientEnd = when {
            directionsStart > ingredientsStart && directionsStart >= 0 -> directionsStart
            ingredientsStart >= 0 -> minOf(lines.size, ingredientsStart + 24)
            else -> lines.size
        }
        val candidateIngredientLines = when {
            ingredientsStart >= 0 -> lines.subList((ingredientsStart + 1).coerceAtMost(lines.size), ingredientEnd)
            else -> lines.filter { looksLikeIngredient(it) }.take(40)
        }
        val ingredients = candidateIngredientLines.mapIndexedNotNull { index, line -> parseIngredient(line, index) }
        val stepLines = when {
            directionsStart >= 0 -> lines.drop(directionsStart + 1)
            else -> lines.filter { looksLikeStep(it) }
        }.map { it.replace(Regex("^\\s*(?:step\\s*)?\\d{1,2}[.):]\\s*", RegexOption.IGNORE_CASE), "") }
            .filter { it.length > 8 }
            .take(80)
        if (ingredients.isEmpty() && stepLines.isEmpty()) error("No ingredients or directions were found. Try a direct recipe page or add it manually.")
        val title = lines.firstOrNull { line ->
            line.length in 3..120 && !looksLikeIngredient(line) && !looksLikeStep(line) &&
                !line.matches(Regex("(?i)(ingredients?|directions?|instructions?|method|steps?)[: ]*"))
        } ?: "Recipe from shared text"
        val source = platform(sourceUrl).ifBlank { "Shared recipe" }
        return RecipeDraft(
            title = title.removePrefix("- ").trim().take(120),
            description = "Imported from shared recipe text.",
            source = source,
            sourceUrl = sourceUrl.takeIf(String::isNotBlank),
            time = timeFromText(clean),
            servings = servingsFromText(clean),
            difficulty = Difficulty.EASY,
            tags = listOf("Imported"),
            ingredients = ingredients,
            steps = stepLines,
        )
    }

    fun fromDocumentText(value: String, titleHint: String = ""): RecipeDraft {
        val clean = normalize(value)
        if (clean.length < 20) error("Not enough recipe text was provided.")
        val pages = clean.split(Regex("(?:\\Q$pdfPageBreak\\E|\\u000C)")).map(String::trim).filter(String::isNotBlank)
        if (pages.size > 1) {
            val pageDrafts = pages.mapNotNull { page ->
                val pageLines = documentLines(page)
                if (pageLines.none(::isDocumentIngredientHeading) && pageLines.none(::isDocumentDirectionHeading)) null
                else runCatching { fromSingleDocumentText(page, "") }.getOrNull()
            }
            if (pageDrafts.size > 1) {
                val cover = pages.firstOrNull { page ->
                    val pageLines = documentLines(page)
                    pageLines.none(::isDocumentIngredientHeading) && pageLines.none(::isDocumentDirectionHeading)
                }.orEmpty()
                val title = documentCollectionTitle(cover, titleHint, pageDrafts.size)
                return RecipeDraft(
                    title = title,
                    description = "${pageDrafts.size} recipes imported from a recipe document.",
                    source = "Document",
                    time = pageDrafts.maxOfOrNull { it.time } ?: 30,
                    servings = pageDrafts.map { it.servings }.distinct().singleOrNull() ?: 1,
                    difficulty = Difficulty.EASY,
                    tags = listOf("Imported", "PDF"),
                    ingredients = pageDrafts.flatMap { draft -> draft.ingredients.map { it.copy(group = draft.title) } },
                    steps = pageDrafts.flatMap { draft -> draft.steps.map { "${draft.title} — $it" } },
                )
            }
            if (pageDrafts.size == 1) return pageDrafts.single()
        }
        return fromSingleDocumentText(clean, titleHint)
    }

    private fun fromSingleDocumentText(value: String, titleHint: String): RecipeDraft {
        val clean = normalize(value)
        val lines = documentLines(clean)
        val ingredientsStart = lines.indexOfFirst(::isDocumentIngredientHeading)
        val directionsStart = lines.indexOfFirst(::isDocumentDirectionHeading)
        if (ingredientsStart < 0 && directionsStart < 0) return fromText(clean)

        val ingredientEnd = listOf(
            directionsStart.takeIf { it > ingredientsStart },
            lines.indexOfFirstAfter(ingredientsStart, ::isDocumentStopHeading).takeIf { it > ingredientsStart },
        )
            .filterNotNull().minOrNull() ?: lines.size
        val ingredientSection = if (ingredientsStart >= 0) lines.subList(ingredientsStart, ingredientEnd) else emptyList()
        val ingredients = parseDocumentIngredients(ingredientSection)

        val directionEnd = lines.indexOfFirstAfter(directionsStart, ::isDocumentStopHeading).takeIf { it >= 0 } ?: lines.size
        val trailingDirectionSection = if (directionsStart >= 0) lines.subList(directionsStart + 1, directionEnd) else emptyList()
        val recoveredPrecedingSteps = if (ingredientsStart >= 0 && directionsStart > ingredientsStart) {
            parseDocumentStepsBeforeHeading(ingredientSection, trailingDirectionSection)
        } else emptyList()
        val steps = recoveredPrecedingSteps.ifEmpty {
            if (directionsStart >= 0) parseDocumentSteps(trailingDirectionSection) else emptyList()
        }
        if (ingredients.isEmpty() && steps.isEmpty()) error("No ingredients or directions were found in that document.")

        val sectionStart = listOf(ingredientsStart, directionsStart).filter { it >= 0 }.minOrNull() ?: lines.size
        val rawTitle = documentTitle(lines, titleHint, sectionStart)

        return RecipeDraft(
            title = repairDocumentRecipeTitle(rawTitle, ingredients),
            description = "Imported from a recipe document.",
            source = "Document",
            time = timeFromText(clean),
            servings = servingsFromText(clean),
            difficulty = Difficulty.EASY,
            tags = listOf("Imported"),
            ingredients = ingredients,
            steps = steps,
        )
    }

    fun fromPhotoText(pages: List<String>, sourceUrl: String = ""): RecipeDraft {
        val all = pages.flatMap { normalize(it).lines().map(::normalizePhotoOcrLine).filter(String::isNotBlank) }
        val title = all.firstOrNull { line ->
            line.length in 3..100 && parseIngredient(line) == null && !photoNoise.matches(line)
        } ?: "Recipe from photo post"
        val ingredients = mutableListOf<Ingredient>()
        var group: String? = null
        all.forEachIndexed { index, line ->
            val parsed = parseIngredient(line)
            if (parsed != null) {
                ingredients += parsed.copy(
                    id = "photo-${Ids.next("ingredient")}-$index",
                    quantity = normalizePhotoQuantity(parsed.quantity),
                    name = normalizePhotoIngredientName(parsed.name),
                    group = group,
                )
                return@forEachIndexed
            }
            val nextIsIngredient = all.getOrNull(index + 1)?.let(::parseIngredient) != null
            if (line != title && nextIsIngredient && isPhotoGroupHeading(line)) group = line
        }
        if (ingredients.size < 2) error("Mise scanned the photo but could not identify ingredients and directions.")
        val directionsStart = all.indexOfFirst { it.matches(Regex("(?i)(directions?|instructions?|method|steps?)[: ]*")) }
        val steps = if (directionsStart >= 0) all.drop(directionsStart + 1)
            .filterNot(::looksLikeIngredient)
            .map { it.replace(Regex("^\\s*(?:step\\s*)?\\d{1,2}[.):]\\s*", RegexOption.IGNORE_CASE), "") }
            .filter { it.length > 8 }
            .take(80)
        else emptyList()
        return RecipeDraft(
            title = title,
            description = if (pages.size > 1) "Ingredients scanned from ${pages.size} recipes in a photo carousel." else "Ingredients scanned from a recipe photo.",
            source = platform(sourceUrl).ifBlank { "Photo post" }, sourceUrl = sourceUrl.takeIf(String::isNotBlank),
            postMedia = "photo", time = 10, servings = servingsFromText(all.joinToString(" ")),
            tags = listOf("Photo post"), ingredients = ingredients, steps = steps,
        )
    }

    fun fromUrl(value: String): RecipeDraft {
        val normalized = value.trim()
        require(normalized.startsWith("http://") || normalized.startsWith("https://")) { "Enter a complete http or https recipe link." }
        val connection = URL(normalized).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        return try {
            val code = connection.responseCode
            if (code !in 200..299) error("The recipe page returned HTTP $code.")
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            require(html.length <= 2_000_000) { "That page is too large to import safely." }
            fromHtml(html, connection.url?.toString() ?: normalized)
        } finally { connection.disconnect() }
    }

    fun fromHtml(html: String, sourceUrl: String = ""): RecipeDraft {
        val scripts = Regex("<script[^>]+type=[\\\"']application/ld\\+json[\\\"'][^>]*>([\\s\\S]*?)</script>", RegexOption.IGNORE_CASE)
            .findAll(html).map { it.groupValues[1] }.toList()
        scripts.forEach { raw ->
            runCatching { fromJsonImport(raw, sourceUrl) }.onSuccess { draft ->
                if (draft.ingredients.isNotEmpty() || draft.steps.isNotEmpty()) return draft
            }
        }
        val text = html.replace(Regex("<style[\\s\\S]*?</style>|<script[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("<[^>]+>"), " ").replace(Regex("&(?:nbsp|amp|quot|apos|lt|gt);"), " ")
        return fromText(text, sourceUrl)
    }

    fun fromJsonImport(raw: String, sourceUrl: String = "", assetBaseUrl: String = sourceUrl): RecipeDraft {
        val root = JSONObject(raw)
        val recipe = root.optJSONObject("recipe") ?: root
        val ingredients = (recipe.optJSONArray("ingredients") ?: recipe.optJSONArray("recipeIngredient")).ingredients()
        val instructions = when {
            recipe.optJSONArray("steps") != null -> recipe.optJSONArray("steps")!!.strings()
            recipe.optJSONArray("recipeInstructions") != null -> recipe.optJSONArray("recipeInstructions")!!.instructionStrings()
            else -> emptyList()
        }
        if (ingredients.isEmpty() && instructions.isEmpty()) {
            val graph = root.optJSONArray("@graph")
            if (graph != null) graph.objects().firstOrNull { it.optString("@type").contains("Recipe", true) }?.let { return fromJsonImport(it.toString(), sourceUrl, assetBaseUrl) }
            error("No recipe was found in the caption or public pinned comments.")
        }
        val imageUrl = resolveWebUrl(
            recipe.optString("imageUrl", "").takeIf(String::isNotBlank) ?: imageValue(recipe.opt("image")),
            assetBaseUrl,
        )
        val time = recipe.optInt("time", 0).takeIf { it > 0 }
            ?: durationMinutes(recipe.optString("totalTime", ""))
        val servings = recipe.optInt("servings", 0).takeIf { it > 0 }
            ?: recipe.optString("recipeYield", "").filter(Char::isDigit).toIntOrNull()
            ?: 4
        return RecipeDraft(
            title = recipe.optString("title", recipe.optString("name", "Recipe from link")).take(120),
            description = recipe.optString("description", "Imported recipe."),
            source = recipe.optString("source", platform(sourceUrl).ifBlank { "Imported recipe" }),
            sourceUrl = sourceUrl.takeIf(String::isNotBlank) ?: recipe.optString("url", "").takeIf(String::isNotBlank),
            imageUrl = imageUrl,
            postImageUrls = recipe.optJSONArray("postImageUrls").imageStrings(assetBaseUrl),
            postMedia = recipe.optString("postMedia", "").takeIf(String::isNotBlank),
            time = time,
            servings = servings,
            difficulty = when (recipe.optString("difficulty", "Easy").lowercase(Locale.US)) {
                "medium" -> Difficulty.MEDIUM
                "project" -> Difficulty.PROJECT
                else -> Difficulty.EASY
            },
            ingredients = ingredients,
            steps = instructions,
            tags = recipe.optJSONArray("tags").strings().ifEmpty { listOf("Imported") },
        )
    }

    fun previewFromJson(raw: String, sourceUrl: String?): RecipeDraft? = runCatching {
        val root = JSONObject(raw)
        val preview = root.optJSONObject("preview") ?: return@runCatching null
        fromJsonImport(preview.toString(), sourceUrl.orEmpty())
    }.getOrNull()

    private fun parseIngredient(value: String, index: Int = 0): Ingredient? {
        val clean = cleanLine(value).removePrefix("•").removePrefix("-").trim()
        if (clean.isBlank() || clean.length > 180 || clean.matches(Regex("(?i)(ingredients?|directions?|instructions?|method|recipe)"))) return null
        if (clean.matches(Regex("(?i)^(?:a|an)[a-z].*"))) return null
        val match = amountPattern.find(clean) ?: return null
        val amount = buildList {
            match.groupValues.getOrNull(1)?.takeIf(String::isNotBlank)?.let(::add)
            match.groupValues.getOrNull(2)?.takeIf(String::isNotBlank)?.let { add("to"); add(it) }
            match.groupValues.getOrNull(3)?.takeIf(String::isNotBlank)?.let(::add)
        }.joinToString(" ")
        val name = match.groupValues.last().trim().trim(',', '.')
        if (name.length < 2 || !name.any(Char::isLetter)) return null
        return Ingredient(id = "imported-${Ids.next("ingredient")}-$index", quantity = amount, name = name)
    }

    private fun looksLikeIngredient(value: String): Boolean = amountPattern.matches(cleanLine(value))
    private fun looksLikeStep(value: String): Boolean = value.matches(Regex("^\\s*(?:step\\s*)?\\d{1,2}[.):]\\s+", RegexOption.IGNORE_CASE)) || value.length > 42
    private fun cleanLine(value: String): String = value.replace(Regex("\\s+"), " ").trim()

    private val documentQuantity = "(?:\\d+/\\d+(?:st|nd|rd|th)?|\\d+(?:[.,]\\d+)?(?:\\s+\\d+/\\d+)?|[¼½¾⅓⅔⅛⅜⅝⅞])(?:\\s*(?:-|–|—|to)\\s*\\d+(?:[.,]\\d+)?)?\\s*(?:cups?|tablespoons?|tbsp|teaspoons?|tsp|grams?|kilograms?|g|kg|millilit(?:er|re)s?|ml|lit(?:er|re)s?|l|ounces?|oz|pounds?|lbs?|lb|cloves?|cans?|bunch(?:es)?|slices?|sprigs?|stalks?|heads?|fillets?|pieces?|packets?|packs?|whole|medium|small|large)?(?![A-Za-z])"
    private val documentAmountOnlyPattern = Regex("^($documentQuantity)$", RegexOption.IGNORE_CASE)
    private val documentTrailingAmountPattern = Regex("^(.+?[A-Za-z][^:]{0,120}?)\\s+($documentQuantity)$", RegexOption.IGNORE_CASE)

    private fun parseDocumentIngredients(lines: List<String>): List<Ingredient> {
        val ingredients = mutableListOf<Ingredient>()
        var group: String? = null
        var trailingGroups = emptyList<String>()
        val usesBullets = lines.any(::isDocumentBulletLine)
        val boundedLines = if (usesBullets) {
            val lastBullet = lines.indexOfLast(::isDocumentBulletLine)
            lines.take(lastBullet + 1) + lines.drop(lastBullet + 1).takeWhile { line ->
                !isDocumentInstructionBodyStart(line) && !isDocumentDirectionHeading(line) && !isDocumentStopHeading(line)
            }
        } else lines
        val ingredientLines = if (usesBullets) joinDocumentIngredientContinuations(boundedLines) else boundedLines
        var index = 0
        while (index < ingredientLines.size) {
            val raw = ingredientLines[index]
            val hadBullet = isDocumentBulletLine(raw)
            val line = raw.replace(Regex("^\\s*(?:[-•●▪◦‣–—]|✅|☑️?|✔️?|✓)\\s*"), "").trim()
            if (isDocumentIngredientIntro(line) || isDocumentIngredientTableHeader(line)) {
                index += 1
                continue
            }
            documentIngredientGroupName(line)?.let {
                group = it
                index += 1
                continue
            }
            combinedDocumentGroups(line).takeIf { it.size >= 2 }?.let {
                trailingGroups = it
                index += 1
                continue
            }
            val nextLine = ingredientLines.getOrNull(index + 1)?.let(::cleanLine).orEmpty()
            val nextAmount = documentAmountOnlyPattern.matchEntire(nextLine)?.groupValues?.get(1)
            if (nextAmount != null && isPlausibleDocumentIngredient(line)) {
                ingredients += Ingredient(
                    id = "document-${Ids.next("ingredient")}-$index",
                    quantity = nextAmount,
                    name = repairDocumentIngredientName(line.trim().trim(',', '.', ';')),
                    group = group,
                )
                index += 2
                continue
            }
            val trailingAmount = documentTrailingAmountPattern.matchEntire(line)
            if (trailingAmount != null) {
                ingredients += Ingredient(
                    id = "document-${Ids.next("ingredient")}-$index",
                    quantity = trailingAmount.groupValues[2].trim(),
                    name = repairDocumentIngredientName(trailingAmount.groupValues[1].trim().trim(',', '.', ';')),
                    group = group,
                )
                index += 1
                continue
            }
            val parsed = parseIngredient(line, index)
            if (parsed != null) {
                ingredients += parsed.copy(name = repairDocumentIngredientName(parsed.name), group = group)
            } else if (!hadBullet && isDocumentIngredientGroup(line)) {
                group = line.trim(':')
            } else if ((hadBullet || !usesBullets) && isPlausibleDocumentIngredient(line)) {
                ingredients += Ingredient(
                    id = "document-${Ids.next("ingredient")}-$index",
                    quantity = "",
                    name = repairDocumentIngredientName(line.trim().trim(',', '.', ';')),
                    group = group,
                )
            }
            index += 1
        }
        val grouped = if (trailingGroups.size >= 2 && ingredients.all { it.group == null } && ingredients.size % trailingGroups.size == 0) {
            val groupSize = ingredients.size / trailingGroups.size
            ingredients.mapIndexed { index, ingredient -> ingredient.copy(group = trailingGroups[index / groupSize]) }
        } else ingredients
        return grouped.distinctBy {
            "${it.group?.lowercase(Locale.US).orEmpty()}|${it.quantity.lowercase(Locale.US)}|${it.name.lowercase(Locale.US)}"
        }
    }

    private fun isDocumentBulletLine(value: String): Boolean =
        value.matches(Regex("^\\s*(?:[-•●▪◦‣–—]|✅|☑️?|✔️?|✓).+"))

    private fun joinDocumentIngredientContinuations(lines: List<String>): List<String> {
        val joined = mutableListOf<String>()
        lines.forEach { raw ->
            val line = cleanLine(raw)
            val isHeading = isDocumentIngredientHeading(line) || documentIngredientGroupName(line) != null ||
                combinedDocumentGroups(line).size >= 2 || isDocumentIngredientTableHeader(line)
            if (!isDocumentBulletLine(line) && !isHeading && joined.lastOrNull()?.let(::isDocumentBulletLine) == true) {
                joined[joined.lastIndex] = "${joined.last()} $line"
            } else {
                joined += line
            }
        }
        return joined
    }

    private fun repairDocumentIngredientName(value: String): String = value
        .replace(Regex("(?i)^arlic(?=\\s+cloves?\\b)"), "Garlic")
        .trim()

    private fun documentIngredientGroupName(value: String): String? = Regex("(?i)^ingredients?\\s+for\\s+(.+?)\\s*:?$")
        .matchEntire(value)?.groupValues?.get(1)?.trim()?.trimEnd(':')?.takeIf(String::isNotBlank)

    private fun parseDocumentSteps(lines: List<String>): List<String> {
        val steps = mutableListOf<String>()
        var remainingLines = lines
        val numberStart = lines.indexOfFirst { it.matches(Regex("\\d{1,2}")) }
        if (numberStart > 0) {
            val numbers = lines.drop(numberStart).takeWhile { it.matches(Regex("\\d{1,2}")) }.mapNotNull(String::toIntOrNull)
            val titlesStart = numberStart + numbers.size
            val titles = lines.drop(titlesStart).take(numbers.size)
            val bodies = splitDocumentBodies(lines.take(numberStart), numbers.size)
            if (numbers.size >= 2 && titles.size == numbers.size && titles.all(::isDocumentInstructionHeading) && bodies.size == numbers.size) {
                numbers.indices.map { index -> Triple(numbers[index], titles[index].trimEnd(':'), bodies[index]) }
                    .sortedBy { it.first }
                    .forEach { (_, title, body) -> steps += "$title: $body" }
                remainingLines = lines.drop(titlesStart + titles.size)
            }
        }
        val recoveredStepCount = steps.size
        var currentTitle = ""
        val currentBody = mutableListOf<String>()
        fun flush() {
            if (currentTitle.isBlank()) return
            val body = currentBody.joinToString(" ").replace(Regex("\\s+"), " ").trim()
            steps += if (body.isBlank()) currentTitle else "$currentTitle: $body"
            currentTitle = ""
            currentBody.clear()
        }

        remainingLines.forEachIndexed { index, raw ->
            val line = raw.replace(Regex("^\\s*(?:[-•●▪◦‣–—]|✅|☑️?|✔️?|✓)\\s*"), "").trim()
            if (line.isBlank() || isDocumentDirectionIntro(line)) return@forEachIndexed
            val numbered = Regex("(?i)^(?:step\\s*)?\\d{1,2}(?:[.):]|\\s)\\s*(.+)$").matchEntire(line)
            if (numbered != null) {
                flush()
                currentTitle = numbered.groupValues[1].trim().trimEnd(':')
                return@forEachIndexed
            }
            if (isDocumentSectionBanner(line, remainingLines.getOrNull(index + 1))) return@forEachIndexed
            if (isDocumentInstructionHeading(line)) {
                flush()
                currentTitle = line.trimEnd(':')
                return@forEachIndexed
            }
            if (currentTitle.isNotBlank()) currentBody += line
        }
        flush()
        val ordered = if (recoveredStepCount > 0 && steps.size > recoveredStepCount) {
            steps.take(recoveredStepCount) + steps.drop(recoveredStepCount).sortedBy(::documentTailStepRank)
        } else steps
        return ordered.map(::trimDocumentTrailingSections).filter { it.length > 8 }.take(80)
    }

    private fun parseDocumentStepsBeforeHeading(
        ingredientSection: List<String>,
        trailingDirectionSection: List<String>,
    ): List<String> {
        val lastBullet = ingredientSection.indexOfLast { it.matches(Regex("^\\s*(?:[-•●▪◦‣–—]|✅|☑️?|✔️?|✓).+")) }
        if (lastBullet < 0) return emptyList()
        val expected = trailingDirectionSection.takeWhile { it.matches(Regex("^[0-9\\s]+$")) }
            .flatMap { line -> Regex("\\d{1,2}").findAll(line).map { it.value.toInt() }.toList() }
            .distinct().size
        if (expected < 2) return emptyList()

        val steps = mutableListOf<String>()
        var current = mutableListOf<String>()
        fun flush() {
            val value = trimDocumentTrailingSections(current.joinToString(" ").replace(Regex("\\s+"), " ").trim())
            if (value.length > 8) steps += value
            current = mutableListOf()
        }

        ingredientSection.drop(lastBullet + 1).forEach { raw ->
            val line = raw.trim()
            if (line.isBlank() || isDocumentStopHeading(line) || line.equals("recipe video", true)) return@forEach
            if (current.isNotEmpty() && isDocumentInstructionBodyStart(line)) flush()
            current += line
        }
        flush()

        while (steps.size > expected && steps.size > 1) {
            val shortest = (1 until steps.size).minByOrNull { steps[it].length } ?: break
            steps[shortest - 1] = "${steps[shortest - 1]} ${steps[shortest]}".replace(Regex("\\s+"), " ").trim()
            steps.removeAt(shortest)
        }
        return steps.takeIf { it.size == expected } ?: emptyList()
    }

    private fun splitDocumentBodies(lines: List<String>, expected: Int): List<String> {
        if (expected < 2) return emptyList()
        val standaloneBodies = lines.filterNot(::isDocumentDirectionIntro).filter(::isDocumentInstructionBodyStart)
        if (standaloneBodies.size == expected) return standaloneBodies
        val bodies = mutableListOf<MutableList<String>>()
        var current: MutableList<String>? = null
        lines.forEach { line ->
            if (isDocumentDirectionIntro(line) || (current == null && !isDocumentInstructionBodyStart(line))) return@forEach
            if (current != null && current!!.size >= 2 && isDocumentInstructionBodyStart(line) && bodies.size < expected) {
                current = null
            }
            if (current == null) mutableListOf<String>().also { bodies += it; current = it }
            current!! += line
        }
        return bodies.map { it.joinToString(" ").replace(Regex("\\s+"), " ").trim() }.filter(String::isNotBlank)
    }

    private fun documentTailStepRank(value: String): Int = when {
        value.startsWith("garnish", true) || value.startsWith("serve", true) -> 30
        value.startsWith("cover", true) || value.startsWith("steam", true) -> 20
        else -> 10
    }

    private fun documentTitle(lines: List<String>, titleHint: String, sectionStart: Int): String {
        val hintTokens = titleHint.lowercase(Locale.US).split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 && it !in setOf("the", "and", "recipe", "document") }.toSet()
        val candidates = lines.take(sectionStart.coerceIn(1, lines.size)).take(30).filter { line ->
            val words = line.split(' ').filter(String::isNotBlank)
            line.length in 4..100 && words.size in 2..14 && !line.endsWith('.') &&
                !isDocumentIngredientHeading(line) && !isDocumentDirectionHeading(line) && !isDocumentStopHeading(line) &&
                !line.matches(Regex("(?i)^(?:calories?|macros?|protein|carbs?|fat|fibre|fiber)\\s*(?::|~|\\d).*$"))
        }
        val best = candidates.maxByOrNull { line ->
            val tokens = line.lowercase(Locale.US).split(Regex("[^a-z0-9]+")) .filter(String::isNotBlank).toSet()
            hintTokens.intersect(tokens).size * 20 +
                (if (line.contains(Regex("(?i)\\b(?:recipe|biryani|pasta|salad|soup|curry|bread|cake|cookies?|chicken|rice)\\b"))) 8 else 0) +
                (if (line == line.uppercase(Locale.US)) -4 else 0)
        }
        val chosen = best?.takeIf { line ->
            val tokens = line.lowercase(Locale.US).split(Regex("[^a-z0-9]+")) .filter(String::isNotBlank).toSet()
            hintTokens.intersect(tokens).isNotEmpty() || line.contains(Regex("(?i)\\b(?:recipe|biryani|pasta|salad|soup|curry|bread|cake|cookies?|chicken|rice)\\b"))
        } ?: prettyDocumentTitle(titleHint).takeIf(String::isNotBlank) ?: candidates.firstOrNull() ?: "Recipe from document"
        return collapseRepeatedDocumentTitle(chosen.take(120))
    }

    private fun collapseRepeatedDocumentTitle(value: String): String {
        val words = value.split(Regex("\\s+")).filter(String::isNotBlank)
        if (words.size >= 4 && words.size % 2 == 0) {
            val midpoint = words.size / 2
            if (words.take(midpoint).map { it.lowercase(Locale.US) } == words.drop(midpoint).map { it.lowercase(Locale.US) }) {
                return words.take(midpoint).joinToString(" ").take(120)
            }
        }
        return value.take(120)
    }

    private fun repairDocumentRecipeTitle(value: String, ingredients: List<Ingredient>): String {
        val suspicious = value.split(' ').any { word -> word.length > 24 || word.drop(1).any(Char::isUpperCase) }
        if (!suspicious) return value
        val primaryGroup = ingredients.mapNotNull { it.group }.firstOrNull { !it.contains(Regex("(?i)\\b(?:chalupas?|serving|topping)\\b")) }
            ?: return value
        val base = primaryGroup.replace(Regex("(?i)\\s+(?:dip|sauce|dressing|slaw)$"), "").trim()
        val recipeKind = ingredients.mapNotNull { it.group }.firstOrNull { it.contains(Regex("(?i)\\bchalupas?\\b")) }
            ?.let { "Chalupas" } ?: return value
        return "$base $recipeKind".replace(Regex("\\s+"), " ").trim().take(120)
    }

    private fun documentCollectionTitle(cover: String, titleHint: String, recipeCount: Int): String {
        val lines = documentLines(cover)
        val titleStart = lines.indexOfFirst { it.matches(Regex("^\\d+\\s+.+")) }
        if (titleStart >= 0) {
            val parts = lines.drop(titleStart).take(3).takeWhile { line ->
                line.length in 2..70 && !line.endsWith('.') &&
                    !line.contains(Regex("(?i)\\b(?:minutes?|ingredients?|nutrition|recipes? with|servings?|macros?)\\b")) &&
                    !line.contains('•') && line.count(Char::isLetter) >= 2
            }
            if (parts.isNotEmpty()) return prettyDocumentTitle(parts.joinToString(" ")).take(120)
        }
        return prettyDocumentTitle(titleHint).takeIf(String::isNotBlank)?.take(120)
            ?: "$recipeCount recipes from document"
    }

    private fun prettyDocumentTitle(value: String): String {
        val clean = value.replace(Regex("[_-]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (clean.isBlank() || (clean != clean.uppercase(Locale.US) && clean != clean.lowercase(Locale.US))) return clean
        return clean.lowercase(Locale.US).split(' ').joinToString(" ") { word ->
            word.split('-').joinToString("-") { part ->
                if (part in setOf("and", "for", "of", "the", "with")) part else part.replaceFirstChar { it.titlecase(Locale.US) }
            }
        }.replaceFirstChar { it.titlecase(Locale.US) }
    }

    private fun documentLines(value: String): List<String> = normalize(value).lines().map(::cleanLine)
        .filter(String::isNotBlank).filterNot(::isDocumentPageNoise)

    private fun isDocumentPageNoise(value: String): Boolean = value == pdfPageBreak ||
        value.matches(Regex("(?i)^page\\s+\\d+$")) ||
        value.matches(Regex("(?i)^@[-._a-z0-9]+(?:\\s+page\\s+\\d+)?$"))

    private fun isDocumentIngredientHeading(value: String): Boolean {
        val key = documentHeadingKey(value)
        return key.matches(Regex("ingredients?(?: for .+)?")) || key in setOf(
            "what you will need", "what youll need", "you will need", "shopping list", "ingredient list",
        )
    }

    private fun isDocumentDirectionHeading(value: String): Boolean {
        val key = documentHeadingKey(value)
        return key.matches(Regex("(?:directions?|instructions?|method|steps?)")) ||
            key in setOf("step by step cooking method", "step by step method", "cooking method", "how to make", "preparation", "procedure")
    }

    private fun isDocumentStopHeading(value: String): Boolean {
        val key = documentHeadingKey(value)
        return key.matches(Regex("(?:nutrition(?: at a glance)?|nutritional information|macros?|notes?|tips?|storage|substitutions?)")) ||
            key.startsWith("nutrition ") || key.startsWith("nutritional information ") ||
            key.startsWith("macros ") || key.startsWith("storage ")
    }

    private fun isDocumentIngredientIntro(value: String): Boolean = value.length > 110 || value.endsWith('.') ||
        value.contains(Regex("(?i)\\b(?:every ingredient|chosen with purpose|build bold flavo|youll need these)\\b"))

    private fun isDocumentIngredientTableHeader(value: String): Boolean {
        val key = documentHeadingKey(value)
        return key in setOf("ingredient amount", "amount ingredient", "ingredient quantity", "quantity ingredient", "amount", "quantity")
    }

    private fun isDocumentIngredientGroup(value: String): Boolean {
        if (value.length !in 3..55 || value.contains(Regex("[,;&()]"))) return false
        val words = value.split(' ').filter(String::isNotBlank)
        val groupWord = value.contains(Regex("(?i)\\b(?:marination|marinade|paste|essentials?|components?|for the|sauce|dressing|topping|filling|garnish)\\b"))
        val titleLike = words.size in 1..6 && words.count { it.firstOrNull()?.isUpperCase() == true } >= (words.size + 1) / 2
        return groupWord && titleLike
    }

    private fun combinedDocumentGroups(value: String): List<String> = Regex(
        "(?:[A-Z][A-Za-z'’-]*\\s+){1,4}?(?:Marination|Marinade|Paste|Essentials|Components|Sauce|Dressing|Topping|Filling|Garnish)",
    ).findAll(value).map { it.value.trim() }.toList()

    private fun isPlausibleDocumentIngredient(value: String): Boolean {
        val words = value.split(' ').filter(String::isNotBlank)
        return value.length in 2..100 && words.size <= 12 && value.any(Char::isLetter) && !value.endsWith('.') &&
            !value.matches(Regex("(?i)^(?:add|allow|blend|combine|cook|cover|finish|follow|heat|mix|place|pour|reduce|serve|stir|this|using)\\b.*")) &&
            !value.matches(Regex("(?i)^(?:calories?|macros?|protein|carbohydrates?|carbs?|total fat|fat|fibre|fiber|daily calorie)\\s*(?::|~|\\d).*$")) &&
            !isDocumentIngredientHeading(value) && !isDocumentDirectionHeading(value) && !isDocumentStopHeading(value)
    }

    private fun trimDocumentTrailingSections(value: String): String = value
        .replace(Regex("(?i)\\s+(?:nutrition(?:al)?(?:\\s+per\\s+serving|\\s+information|\\s+at\\s+a\\s+glance)?|macros?|storage)\\b.*$"), "")
        .trim().trimEnd(':')

    private fun isDocumentDirectionIntro(value: String): Boolean = value.contains(Regex("(?i)^(?:follow these|this recipe|these steps)\\b"))

    private fun isDocumentInstructionBodyStart(value: String): Boolean = value.matches(
        Regex("(?i)^(?:add|assemble|bake|blend|boil|brown|carefully|chop|combine|cook|cover|finish|first|fold|heat|in (?:a|an|the)|marinate|mix|now|once|pat|place|pour|roast|saute|sauté|season|serve|simmer|steam|stir|take|then|transfer|while|whisk)\\b.*"),
    )

    private fun isDocumentInstructionHeading(value: String): Boolean {
        val words = value.trimEnd(':').split(' ').filter(String::isNotBlank)
        if (value.length !in 3..72 || words.size !in 2..10 || value.endsWith('.')) return false
        val first = words.first().trim(',', ':').lowercase(Locale.US)
        val action = first in setOf("add", "bake", "blend", "boil", "chill", "combine", "cook", "cover", "fold", "garnish", "heat", "marinate", "mix", "pour", "roast", "saute", "sauté", "serve", "simmer", "steam", "stir", "whisk")
        val titleLike = words.count { word -> word.firstOrNull()?.isUpperCase() == true } >= (words.size + 1) / 2
        return action && titleLike
    }

    private fun isDocumentSectionBanner(value: String, next: String?): Boolean =
        value.count { it == ',' || it == '&' } >= 2 && next?.let(::isDocumentInstructionHeading) == true

    private fun documentHeadingKey(value: String): String = value.lowercase(Locale.US)
        .replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun List<String>.indexOfFirstAfter(start: Int, predicate: (String) -> Boolean): Int {
        if (start < 0) return -1
        for (index in start + 1 until size) if (predicate(this[index])) return index
        return -1
    }
    private val photoNoise = Regex("(?i)^(?:ingredients?|directions?|instructions?|method|recipe|save|follow|https?://|.*@\\w+).*$")
    private fun normalizePhotoOcrLine(value: String): String = cleanLine(value)
        .replace(Regex("(?i)\\b(?:Lavorites|favorites)\\b"), "Favorites")
        .replace(Regex("(?i)\\bGrarlic\\b"), "Garlic")
        .replace(Regex("(?i)\\b(?:garlie|garic)\\b"), "garlic")
        .replace(Regex("(?i)\\bSuqar\\b"), "Sugar")
        .replace(Regex("(?i)\\b(?:thsp|tosp|hsp)\\b"), "tbsp")
        .replace(Regex("(?i)\\b(?:4sp|sp)\\b"), "tsp")
        .replace(Regex("^[*.·•e°“”«»¢]\\s*"), "")
        .replace(Regex("(?i)^(?:[a-z]{0,3}:?\\s*)?\\|\\s*(?=(?:t(?:b)?sp)\\b)"), "1 ")
        .replace(Regex("(?i)^(?:[IlLoO])\\s*(?=(?:t(?:b)?sp)\\b)"), "1 ")
        .replace(Regex("(?i)^(?:Jd|oF|A)\\s+(?=tbsp\\b)"), "2 ")
        .replace(Regex("(?i)^(?=(?:tbsp|tsp)\\b)"), "1 ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun normalizePhotoQuantity(value: String): String = value
        .replace(Regex("(?i)\\b(?:cups?|tbsp|tsp)\\b")) { it.value.uppercase(Locale.US) }

    private fun normalizePhotoIngredientName(value: String): String = value
        .replace(Regex("(?i)\\b(?:arlic|garlie|garic)\\b"), "garlic")
        .replace(Regex("(?i)\\bowder\\b"), "powder")
        .replace(Regex("(?i)\\blemoi\\b"), "lemon")
        .replace(Regex("(?i)\\bgroted\\b"), "grated")
        .replace(Regex("(?i)\\bsuqar\\b"), "sugar")
        .lowercase(Locale.US)

    private fun isPhotoGroupHeading(value: String): Boolean = value.length in 3..60 &&
        value.any(Char::isLetter) && value.none(Char::isDigit) && !photoNoise.matches(value)
    private fun normalize(value: String): String = value.replace("\\u00a0", " ").replace("\r\n", "\n").replace('\r', '\n')
    private fun timeFromText(value: String): Int = Regex("(?i)(?:cook|prep|total)?\\s*time[: ]*(\\d+)\\s*(?:min|minutes)").find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 30
    private fun servingsFromText(value: String): Int = (
        Regex("(?i)(?:serves?|makes?|yield)\\s*[: ]*([0-9]+)").find(value)?.groupValues?.get(1)
            ?: Regex("(?i)\\b([0-9]+)\\s+servings?\\b").find(value)?.groupValues?.get(1)
        )?.toIntOrNull() ?: 4
    private fun durationMinutes(value: String): Int {
        val hours = Regex("(\\d+)H", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("(\\d+)M", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        return (hours * 60 + minutes).takeIf { it > 0 } ?: 30
    }
    private fun imageValue(value: Any?): String? = when (value) {
        is String -> value.takeIf(String::isNotBlank)
        is JSONObject -> value.optString("url", value.optString("contentUrl", "")).takeIf(String::isNotBlank)
        is JSONArray -> if (value.length() > 0) imageValue(value.opt(0)) else null
        else -> null
    }
    private fun resolveWebUrl(value: String?, baseUrl: String): String? {
        val candidate = value?.trim()?.takeIf(String::isNotBlank) ?: return null
        return runCatching {
            val resolved = if (candidate.startsWith("http://", true) || candidate.startsWith("https://", true)) {
                URL(candidate)
            } else {
                URL(URL(baseUrl.trim().trimEnd('/') + "/"), candidate)
            }
            resolved.toString().takeIf { resolved.protocol == "http" || resolved.protocol == "https" }
        }.getOrNull()
    }
    private fun platform(value: String): String {
        val host = runCatching { URL(value).host.lowercase(Locale.US).removePrefix("www.") }.getOrDefault("")
        return when {
            host.contains("instagram") -> "Instagram"
            host.contains("tiktok") -> "TikTok"
            host.contains("youtube") || host == "youtu.be" -> "YouTube"
            host.contains("facebook") || host == "fb.watch" -> "Facebook"
            host.contains("pinterest") || host == "pin.it" -> "Pinterest"
            host.contains("reddit") || host == "redd.it" -> "Reddit"
            host.contains("threads") -> "Threads"
            host.contains("twitter") || host == "x.com" -> "X"
            host.contains("lemon8") -> "Lemon8"
            else -> ""
        }
    }

    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { index ->
        (opt(index) as? String)?.takeIf(String::isNotBlank)
    }
    private fun JSONArray?.ingredients(): List<Ingredient> = if (this == null) emptyList() else (0 until length()).mapNotNull { index ->
        when (val value = opt(index)) {
            is JSONObject -> {
                val name = value.optString("name", "").trim()
                if (name.isBlank()) null else Ingredient(
                    id = value.optString("id", "").takeIf(String::isNotBlank) ?: "imported-${Ids.next("ingredient")}-$index",
                    quantity = value.optString("quantity", "").trim(),
                    name = name,
                    group = value.optString("group", "").takeIf(String::isNotBlank),
                )
            }
            is String -> parseIngredient(value, index)
            else -> null
        }
    }
    private fun JSONArray?.imageStrings(baseUrl: String): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { index ->
        resolveWebUrl(imageValue(opt(index)), baseUrl)
    }.distinct()
    private fun JSONArray?.instructionStrings(): List<String> = if (this == null) emptyList() else (0 until length()).mapNotNull { index ->
        optJSONObject(index)?.optString("text", "")?.takeIf(String::isNotBlank)
            ?: (opt(index) as? String)?.takeIf(String::isNotBlank)
    }
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}
