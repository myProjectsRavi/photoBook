package com.photobook.app.ml

import com.photobook.app.data.model.MLTag

/**
 * Shared, conservative Food archive gate used by both local ML analysis and final classification.
 *
 * A generic color heuristic is never enough to authorize an Archive action. Eligibility requires
 * strong semantic food evidence plus either prepared/served-food semantics or packaging text from
 * local OCR, and rejects any live-subject signal including people, pets, livestock, birds and
 * wildlife. All matching is local and case-insensitive.
 */
object ArchiveFoodSignals {
    const val MIN_SEMANTIC_FOOD_CONFIDENCE = 0.70f
    const val MIN_PREPARED_FOOD_CONFIDENCE = 0.60f
    private const val MIN_LIVE_SUBJECT_CONFIDENCE = 0.50f

    private val liveSubjectLabels = setOf(
        "animal",
        "bird",
        "buffalo",
        "bull",
        "calf",
        "cattle",
        "chick",
        "chicken",
        "cow",
        "goat",
        "hen",
        "horse",
        "human",
        "insect",
        "lamb",
        "livestock",
        "mammal",
        "ox",
        "oxen",
        "people",
        "person",
        "pet",
        "poultry",
        "reptile",
        "rooster",
        "selfie",
        "sheep",
        "wildlife",
    )

    private val strongPackagingCues = listOf(
        "nutrition facts",
        "nutrition information",
        "nutritional information",
        "ingredients",
        "serving size",
        "net weight",
        "net wt",
        "best before",
        "use by",
    )

    private val supportingPackagingCues = listOf(
        "calories",
        "kcal",
        "protein",
        "carbohydrate",
        "carbohydrates",
        "sodium",
        "sugars",
        "sugar",
        "dietary fibre",
        "dietary fiber",
        "saturated fat",
    )

    fun isEligible(tags: List<MLTag>, ocrText: String = ""): Boolean {
        val hasSemanticFood = tags.any { tag ->
            tag.label.equals("food", ignoreCase = true) &&
                tag.confidence >= MIN_SEMANTIC_FOOD_CONFIDENCE
        }
        if (!hasSemanticFood) return false
        if (containsLiveSubject(tags)) return false

        val hasPreparedFoodContext = tags.any { tag ->
            tag.label.equals("prepared_food", ignoreCase = true) &&
                tag.confidence >= MIN_PREPARED_FOOD_CONFIDENCE
        }
        return hasPreparedFoodContext || hasPackagedFoodEvidence(ocrText)
    }

    fun hasPackagedFoodEvidence(ocrText: String): Boolean {
        val normalized = ocrText.lowercase().replace(Regex("\\s+"), " ").trim()
        if (normalized.length < MIN_PACKAGING_TEXT_LENGTH) return false
        if (strongPackagingCues.any(normalized::contains)) return true
        return supportingPackagingCues.count(normalized::contains) >= MIN_SUPPORTING_PACKAGING_CUES
    }

    fun containsLiveSubject(tags: List<MLTag>): Boolean {
        return tags.any { tag ->
            val normalized = tag.label.lowercase().trim()
            normalized in liveSubjectLabels &&
                tag.confidence >= MIN_LIVE_SUBJECT_CONFIDENCE
        }
    }

    private const val MIN_PACKAGING_TEXT_LENGTH = 12
    private const val MIN_SUPPORTING_PACKAGING_CUES = 2
}
