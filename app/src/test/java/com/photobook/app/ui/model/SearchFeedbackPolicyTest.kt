package com.photobook.app.ui.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SearchFeedbackPolicyTest {
    @Test
    fun notReady_precedesOtherStates() {
        assertThat(
            SearchFeedbackPolicy.resolve(false, "invoice", "invoice", 4, true),
        ).isEqualTo(SearchFeedbackState.NOT_READY)
    }

    @Test
    fun staleResultRevision_isSearchingAndNeverCurrentResults() {
        assertThat(
            SearchFeedbackPolicy.resolve(true, "invoice", "older", 50, false),
        ).isEqualTo(SearchFeedbackState.SEARCHING)
    }

    @Test
    fun currentRefreshFailure_isExplicit() {
        assertThat(
            SearchFeedbackPolicy.resolve(true, "invoice", "invoice", 0, true),
        ).isEqualTo(SearchFeedbackState.FAILED)
    }

    @Test
    fun currentZeroAndNonZeroResults_areDistinct() {
        assertThat(
            SearchFeedbackPolicy.resolve(true, "invoice", "invoice", 0, false),
        ).isEqualTo(SearchFeedbackState.EMPTY)
        assertThat(
            SearchFeedbackPolicy.resolve(true, "invoice", "invoice", 3, false),
        ).isEqualTo(SearchFeedbackState.RESULTS)
    }
}
