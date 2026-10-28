package com.photobook.app.feature.duplicates

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DuplicateCandidatePolicyTest {
    @Test
    fun matchesSnapshot_acceptsSameIdsInDifferentOrder() {
        assertThat(
            DuplicateCandidatePolicy.matchesSnapshot(
                recordIds = listOf(1L, 2L, 3L),
                databaseIds = listOf(3L, 1L, 2L),
            ),
        ).isTrue()
    }

    @Test
    fun matchesSnapshot_rejectsEqualCountsWithDifferentIds() {
        assertThat(
            DuplicateCandidatePolicy.matchesSnapshot(
                recordIds = listOf(1L, 2L, 3L),
                databaseIds = listOf(1L, 2L, 4L),
            ),
        ).isFalse()
    }

    @Test
    fun matchesSnapshot_rejectsMissingOrAdditionalIds() {
        assertThat(
            DuplicateCandidatePolicy.matchesSnapshot(
                recordIds = listOf(1L, 2L, 3L),
                databaseIds = listOf(1L, 2L),
            ),
        ).isFalse()
        assertThat(
            DuplicateCandidatePolicy.matchesSnapshot(
                recordIds = listOf(1L, 2L),
                databaseIds = listOf(1L, 2L, 3L),
            ),
        ).isFalse()
    }

    @Test
    fun matchesSnapshot_rejectsDuplicateIdentityMismatch() {
        assertThat(
            DuplicateCandidatePolicy.matchesSnapshot(
                recordIds = listOf(1L, 2L),
                databaseIds = listOf(1L, 1L),
            ),
        ).isFalse()
    }
}
