package com.photobook.app.ml

import com.photobook.app.data.model.MLTag

/**
 * Shared, conservative Food archive gate used by both Room prefiltering and final classification.
 *
 * The generic compact color heuristic remains useful for search labels, but it is not strong
 * enough to authorize an Archive action. Food archive eligibility therefore requires a strong
 * semantic food label plus a prepared/served/packaged-food context from the current ML analysis,
 * and rejects any live-subject signal, including face-derived people/selfie tags. The caller must
 * pass only those ephemeral semantic signals; persisted records use the separate archive flag
 * plus this object's live-subject defense.
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

    fun isEligible(tags: List<MLTag>): Boolean {
        val hasSemanticFood = tags.any { tag ->
            tag.label.equals("food", ignoreCase = true) &&
                tag.confidence >= MIN_SEMANTIC_FOOD_CONFIDENCE
        }
        if (!hasSemanticFood) return false

        val hasPreparedFoodContext = tags.any { tag ->
            tag.label.equals("prepared_food", ignoreCase = true) &&
                tag.confidence >= MIN_PREPARED_FOOD_CONFIDENCE
        }
        if (!hasPreparedFoodContext) return false

        return !containsLiveSubject(tags)
    }

    fun containsLiveSubject(tags: List<MLTag>): Boolean {
        return tags.any { tag ->
            val normalized = tag.label.lowercase().trim()
            normalized in liveSubjectLabels &&
                tag.confidence >= MIN_LIVE_SUBJECT_CONFIDENCE
        }
    }
}
