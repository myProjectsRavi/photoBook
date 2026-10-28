package com.photobook.app.ui.model

enum class SearchFeedbackState {
    NOT_READY,
    SEARCHING,
    FAILED,
    EMPTY,
    RESULTS,
}

object SearchFeedbackPolicy {
    fun resolve(
        searchReady: Boolean,
        query: String,
        resultQuery: String,
        resultCount: Int,
        refreshFailed: Boolean,
    ): SearchFeedbackState {
        if (!searchReady) return SearchFeedbackState.NOT_READY
        if (query != resultQuery) return SearchFeedbackState.SEARCHING
        if (refreshFailed) return SearchFeedbackState.FAILED
        if (resultCount == 0) return SearchFeedbackState.EMPTY
        return SearchFeedbackState.RESULTS
    }
}
