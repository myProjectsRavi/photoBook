package com.photobook.app.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LocalSemanticScoreAggregationTest {

    @Test
    fun preparedFoodConfidence_neverBorrowsHigherGenericFoodScore() {
        val generic = mergeSemanticScore(
            current = null,
            canonical = "food",
            confidence = 0.90f,
            isPreparedFood = false,
        )
        val combined = mergeSemanticScore(
            current = generic,
            canonical = "food",
            confidence = 0.01f,
            isPreparedFood = true,
        )

        assertThat(combined.confidence).isWithin(0.0001f).of(0.90f)
        assertThat(combined.preparedFoodConfidence).isNotNull()
        assertThat(combined.preparedFoodConfidence!!).isWithin(0.0001f).of(0.01f)
        assertThat(combined.isPreparedFood).isTrue()
    }

    @Test
    fun multiplePreparedClasses_keepOnlyStrongestPreparedEvidence() {
        val first = mergeSemanticScore(
            current = null,
            canonical = "food",
            confidence = 0.62f,
            isPreparedFood = true,
        )
        val second = mergeSemanticScore(
            current = first,
            canonical = "food",
            confidence = 0.81f,
            isPreparedFood = true,
        )
        val generic = mergeSemanticScore(
            current = second,
            canonical = "food",
            confidence = 0.95f,
            isPreparedFood = false,
        )

        assertThat(generic.confidence).isWithin(0.0001f).of(0.95f)
        assertThat(generic.preparedFoodConfidence!!).isWithin(0.0001f).of(0.81f)
    }
}
