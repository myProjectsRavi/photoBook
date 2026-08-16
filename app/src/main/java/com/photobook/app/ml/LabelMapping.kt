package com.photobook.app.ml

object LabelMapping {

    val keywords: Set<String> = setOf(
        "selfie", "food", "sunset", "sunrise", "beach", "mountain", "document", "text",
        "pet", "dog", "cat", "bird", "animal", "car", "vehicle", "flower", "people", "group", "building",
        "nature", "screenshot", "meme", "receipt", "sky", "water", "tree", "indoor", "outdoor",
    )

    private val thresholdByKeyword = mapOf(
        "selfie" to 0.70f,
        "food" to 0.60f,
        "sunset" to 0.65f,
        "beach" to 0.60f,
        "mountain" to 0.60f,
        "document" to 0.65f,
        "pet" to 0.60f,
        "car" to 0.60f,
        "people" to 0.70f,
        "flower" to 0.60f,
        "bird" to 0.50f,
        "animal" to 0.50f,
        "building" to 0.60f,
        "nature" to 0.55f,
        "receipt" to 0.50f,
    )

    private val rawToCanonical = mapOf(
        "fast food" to "food",
        "pizza" to "food",
        "burger" to "food",
        "salad" to "food",
        "sushi" to "food",
        "cake" to "food",
        "coffee" to "food",
        "cheeseburger" to "food",
        "hot dog" to "food",
        "cookie" to "food",
        "bread" to "food",
        "bento" to "food",
        "couscous" to "food",
        "cuisine" to "food",
        "meal" to "food",
        "lunch" to "food",
        "supper" to "food",
        "juice" to "food",
        "food" to "food",

        "bird" to "bird",
        "animal" to "animal",
        "wildlife" to "animal",
        "mammal" to "animal",
        "reptile" to "animal",
        "insect" to "animal",
        "hen" to "bird",
        "rooster" to "bird",
        "chick" to "bird",
        "chicken" to "bird",
        "poultry" to "bird",
        "penguin" to "bird",
        "waterfowl" to "bird",
        "duck" to "bird",

        "bear" to "animal",
        "cow" to "animal",
        "buffalo" to "animal",
        "goat" to "animal",
        "sheep" to "animal",
        "lamb" to "animal",
        "livestock" to "animal",
        "calf" to "animal",
        "ox" to "animal",
        "oxen" to "animal",
        "cattle" to "animal",
        "crocodile" to "animal",
        "dinosaur" to "animal",
        "dragon" to "animal",
        "fish" to "animal",
        "gerbil" to "pet",
        "horse" to "animal",
        "seal" to "animal",
        "turtle" to "animal",
        "bull" to "animal",
        "butterfly" to "animal",
        "larva" to "animal",
        "pomacentridae" to "animal",
        "primate" to "animal",
        "monkey" to "animal",
        "pest" to "animal",

        "sunset" to "sunset",
        "sunrise" to "sunset",
        "dusk" to "sunset",
        "dawn" to "sunset",
        "horizon" to "sunset",
        "sky" to "sunset",

        "beach" to "beach",
        "coast" to "beach",
        "seashore" to "beach",
        "sand" to "beach",
        "ocean" to "beach",

        "mountain" to "mountain",
        "hill" to "mountain",
        "cliff" to "mountain",
        "valley" to "mountain",
        "ridge" to "mountain",

        "text" to "document",
        "paper" to "document",
        "whiteboard" to "document",
        "receipt" to "document",
        "book" to "document",
        "document" to "document",

        "dog" to "pet",
        "cat" to "pet",
        "shetland sheepdog" to "pet",
        "cairn terrier" to "pet",
        "basset hound" to "pet",
        "dalmatian" to "pet",
        "pixie-bob" to "pet",
        "ragdoll" to "pet",
        "cavalier" to "pet",
        "shikoku" to "pet",
        "sphynx" to "pet",
        "puppy" to "pet",
        "kitten" to "pet",
        "pet" to "pet",

        "car" to "car",
        "vehicle" to "car",
        "suv" to "car",
        "truck" to "car",
        "taxi" to "car",
        "bus" to "car",
        "automobile" to "car",

        "people" to "people",
        "person" to "people",
        "human" to "people",
        "dude" to "people",
        "baby" to "people",
        "child" to "people",
        "grandparent" to "people",
        "crowd" to "people",
        "crew" to "people",
        "musician" to "people",
        "singer" to "people",
        "bride" to "people",
        "groom" to "people",
        "eating" to "people",
        "group" to "people",
        "face" to "people",

        "flower" to "flower",
        "rose" to "flower",
        "daisy" to "flower",
        "tulip" to "flower",
        "sunflower" to "flower",
        "bouquet" to "flower",

        "building" to "building",
        "house" to "building",
        "temple" to "building",
        "mosque" to "building",
        "church" to "building",
        "skyscraper" to "building",

        "forest" to "nature",
        "garden" to "nature",
        "park" to "nature",
        "river" to "nature",
        "lake" to "nature",
        "waterfall" to "nature",
        "nature" to "nature",
    )

    fun map(rawLabel: String): String? {
        val normalized = rawLabel.lowercase().trim()
        if (normalized.isBlank()) return null

        rawToCanonical[normalized]?.let { return it }

        return rawToCanonical.entries.firstOrNull { (raw, _) ->
            containsPhrase(normalized, raw)
        }?.value
    }

    fun isPreparedFoodLabel(rawLabel: String): Boolean {
        return rawLabel.lowercase().trim() in PREPARED_FOOD_LABELS
    }

    fun canonicalKeyword(keyword: String): String {
        val normalized = keyword.lowercase().trim()
        return when (normalized) {
            "dog", "cat" -> "pet"
            "vehicle" -> "car"
            "group" -> "people"
            "text", "receipt", "meme" -> "document"
            else -> normalized
        }
    }

    fun threshold(keyword: String): Float {
        val canonical = canonicalKeyword(keyword)
        return thresholdByKeyword[canonical] ?: 0.60f
    }

    fun taggingThreshold(keyword: String): Float {
        val canonical = canonicalKeyword(keyword)
        return if (canonical in LIVE_SUBJECT_CANONICAL_LABELS) {
            LIVE_SUBJECT_MIN_CONFIDENCE
        } else {
            threshold(canonical)
        }
    }

    private fun containsPhrase(text: String, phrase: String): Boolean {
        var fromIndex = 0
        while (fromIndex <= text.length - phrase.length) {
            val index = text.indexOf(phrase, startIndex = fromIndex)
            if (index < 0) return false
            val beforeIsBoundary = index == 0 || !text[index - 1].isLetterOrDigit()
            val afterIndex = index + phrase.length
            val afterIsBoundary = afterIndex == text.length || !text[afterIndex].isLetterOrDigit()
            if (beforeIsBoundary && afterIsBoundary) return true
            fromIndex = index + 1
        }
        return false
    }

    private val LIVE_SUBJECT_CANONICAL_LABELS = setOf(
        "animal",
        "bird",
        "people",
        "pet",
    )

    private val PREPARED_FOOD_LABELS = setOf(
        "fast food",
        "pizza",
        "burger",
        "salad",
        "sushi",
        "cake",
        "coffee",
        "cheeseburger",
        "hot dog",
        "cookie",
        "bread",
        "bento",
        "couscous",
        "cuisine",
        "meal",
        "lunch",
        "supper",
        "juice",
    )

    private const val LIVE_SUBJECT_MIN_CONFIDENCE = 0.50f
}
