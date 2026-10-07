package com.photobook.app.feature.cleanup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CleanupSelectionPolicyTest {
    @Test
    fun refresh_neverSelectsNewCandidatesAutomatically() {
        val selected = CleanupSelectionPolicy.retainExplicitSelection(
            selectedPhotoIds = emptySet(),
            candidatePhotoIds = setOf(1L, 2L, 3L),
        )

        assertThat(selected).isEmpty()
    }

    @Test
    fun refresh_retainsOnlyStillValidExplicitSelections() {
        val selected = CleanupSelectionPolicy.retainExplicitSelection(
            selectedPhotoIds = setOf(1L, 2L, 9L),
            candidatePhotoIds = setOf(2L, 3L, 4L),
        )

        assertThat(selected).containsExactly(2L)
    }

    @Test
    fun emptyCandidateRefresh_clearsStaleExplicitSelection() {
        val selected = CleanupSelectionPolicy.retainExplicitSelection(
            selectedPhotoIds = setOf(1L, 2L),
            candidatePhotoIds = emptySet(),
        )

        assertThat(selected).isEmpty()
    }
}
