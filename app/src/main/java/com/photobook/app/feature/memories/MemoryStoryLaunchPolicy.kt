package com.photobook.app.feature.memories

internal object MemoryStoryLaunchPolicy {
    const val MAX_WIDGET_STORY_IDS = 256

    /**
     * Widget/deep-link stories are accepted only when the entire cached story still maps to the
     * current visible PhotoBook index. Partial matches could reveal stale access assumptions.
     */
    fun validateExternal(
        requestedIds: List<Long>,
        resolvedIds: List<Long>,
        memoriesHidden: Boolean,
    ): List<Long>? {
        if (memoriesHidden || requestedIds.isEmpty()) return null
        if (requestedIds.size > MAX_WIDGET_STORY_IDS) return null
        if (requestedIds.any { id -> id <= 0L }) return null
        if (requestedIds.toSet().size != requestedIds.size) return null
        if (resolvedIds != requestedIds) return null
        return requestedIds
    }
}
