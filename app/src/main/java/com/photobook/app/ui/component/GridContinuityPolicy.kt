package com.photobook.app.ui.component

import kotlin.math.roundToInt

internal object GridContinuityPolicy {
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
