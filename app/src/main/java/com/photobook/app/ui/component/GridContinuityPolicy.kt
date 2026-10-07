package com.photobook.app.ui.component

import kotlin.math.roundToInt

internal object GridContinuityPolicy {
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
