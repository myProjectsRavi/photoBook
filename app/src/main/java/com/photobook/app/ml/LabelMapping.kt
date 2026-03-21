package com.photobook.app.ml

object LabelMapping {

    val keywords: Set<String> = setOf(
        "selfie", "food", "sunset", "sunrise", "beach", "mountain", "document", "text",
        "pet", "dog", "cat", "car", "vehicle", "flower", "people", "group", "building",
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
        "building" to 0.60f,
        "nature" to 0.55f,
        "receipt" to 0.50f,
    )

    private val rawToCanonical = mapOf(
        "pizza" to "food",
        "burger" to "food",
        "salad" to "food",
        "sushi" to "food",
        "cake" to "food",
        "coffee" to "food",
        "plate" to "food",
        "bowl" to "food",
        "food" to "food",

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
            normalized.contains(raw)
        }?.value
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
}
