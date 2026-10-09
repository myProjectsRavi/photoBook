package com.photobook.app.ui.component

import kotlin.math.roundToInt

internal object GridContinuityPolicy {
    /**
     * A paging generation may replace its loaded rows with zero rows while retaining
     * an old LazyGridState index. Never dereference a stale snapshot index.
     */
    fun <T> loadedItemAt(index: Int, loadedItems: List<T>, placeholdersBefore: Int): T? {
        if (index < 0 || placeholdersBefore < 0 || index < placeholdersBefore) return null
        return loadedItems.getOrNull(index - placeholdersBefore)
    }

    fun loadedAnchorIndex(
        anchorId: Long,
        loadedIds: List<Long>,
        placeholdersBefore: Int,
    ): Int? {
        if (anchorId <= 0L || placeholdersBefore < 0) return null
        val loadedIndex = loadedIds.indexOf(anchorId)
        return loadedIndex.takeIf { it >= 0 }?.plus(placeholdersBefore)
    }

    fun scrubTargetIndex(
        rawY: Float,
        trackHeightPx: Float,
        itemCount: Int,
    ): Int? {
        if (itemCount <= 0 || trackHeightPx <= 0f) return null
        val clampedY = rawY.coerceIn(0f, trackHeightPx)
        val fraction = (clampedY / trackHeightPx).coerceIn(0f, 1f)
        return ((itemCount - 1) * fraction)
            .roundToInt()
            .coerceIn(0, itemCount - 1)
    }
}
