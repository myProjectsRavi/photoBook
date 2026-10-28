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

    /**
     * Keep the scrubber thumb aligned with ordinary grid scrolling, not only drags.
     * The visible viewport cannot start at the final item; account for visible rows
     * and snap to the true ends of the scroll range.
     */
    fun scrubIndicatorY(
        firstVisibleIndex: Int,
        visibleItemCount: Int,
        itemCount: Int,
        trackHeightPx: Float,
        canScrollBackward: Boolean,
        canScrollForward: Boolean,
    ): Float {
        if (itemCount <= 1 || !trackHeightPx.isFinite() || trackHeightPx <= 0f) return 0f
        if (!canScrollBackward) return 0f
        if (!canScrollForward) return trackHeightPx

        val maxFirstVisibleIndex = (itemCount - visibleItemCount.coerceAtLeast(1)).coerceAtLeast(1)
        val fraction = firstVisibleIndex.coerceIn(0, maxFirstVisibleIndex).toFloat() / maxFirstVisibleIndex
        return (fraction * trackHeightPx).coerceIn(0f, trackHeightPx)
    }

    fun scrubTargetIndex(
        rawY: Float,
        trackHeightPx: Float,
        itemCount: Int,
        visibleItemCount: Int = 1,
    ): Int? {
        // NaN can reach roundToInt() and throw; non-finite drag input is never a valid target.
        if (itemCount <= 0 || !rawY.isFinite() || !trackHeightPx.isFinite() || trackHeightPx <= 0f) return null
        val clampedY = rawY.coerceIn(0f, trackHeightPx)
        val fraction = (clampedY / trackHeightPx).coerceIn(0f, 1f)
        // The passive thumb divides by the last possible first-visible index.
        // Invert that same scale so touching the thumb does not jump forward by
        // the size of the visible viewport. A full-range gesture still reaches
        // the true final item, even when the last row is only partly visible.
        if (fraction == 1f) return itemCount - 1
        val maxFirstVisibleIndex = (itemCount - visibleItemCount.coerceAtLeast(1)).coerceAtLeast(1)
        return (maxFirstVisibleIndex * fraction)
            .roundToInt()
            .coerceIn(0, itemCount - 1)
    }
}

/** Deduplicates queued scroll requests only within one active thumb gesture. */
internal class ScrubGestureTargetTracker {
    private var lastIndex: Int? = null

    /** New touches and paging-count swaps cannot inherit a stale target. */
    fun reset() {
        lastIndex = null
    }

    fun shouldDispatch(targetIndex: Int): Boolean {
        if (lastIndex == targetIndex) return false
        lastIndex = targetIndex
        return true
    }
}
