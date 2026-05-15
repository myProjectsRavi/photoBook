package com.photobook.app.feature.copytext

data class NormalizedTextRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized(): NormalizedTextRegion {
        val safeLeft = left.coerceIn(0f, 1f)
        val safeTop = top.coerceIn(0f, 1f)
        val safeRight = right.coerceIn(0f, 1f)
        val safeBottom = bottom.coerceIn(0f, 1f)
        val orderedLeft = minOf(safeLeft, safeRight)
        val orderedTop = minOf(safeTop, safeBottom)
        val orderedRight = maxOf(safeLeft, safeRight)
        val orderedBottom = maxOf(safeTop, safeBottom)
        return NormalizedTextRegion(
            left = orderedLeft,
            top = orderedTop,
            right = orderedRight,
            bottom = orderedBottom,
        )
    }

    fun isUsable(): Boolean {
        val normalized = normalized()
        return normalized.right - normalized.left >= MIN_SIZE &&
            normalized.bottom - normalized.top >= MIN_SIZE
    }

    companion object {
        private const val MIN_SIZE = 0.03f
    }
}
