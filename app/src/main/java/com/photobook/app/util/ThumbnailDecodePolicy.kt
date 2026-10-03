package com.photobook.app.util

import kotlin.math.ceil

/**
 * Pure decode-budget policy for square gallery thumbnails.
 *
 * It chooses the smallest bounded bucket that covers the rendered cell plus a modest
 * overscan margin, then caps that request by the device tier's existing maximum.
 * This keeps cache keys stable while avoiding fixed 512px decodes for smaller cells.
 */
object ThumbnailDecodePolicy {
    private val bucketsPx = intArrayOf(128, 192, 256, 384, 512)
    private const val OVERSCAN = 1.05

    data class Budget(
        val requestSizePx: Int,
        val estimatedArgbBytes: Long,
    )

    fun forGrid(
        viewportWidthPx: Int,
        columns: Int,
        maxRequestSizePx: Int,
    ): Budget {
        require(viewportWidthPx > 0)
        require(columns > 0)
        require(maxRequestSizePx > 0)

        val cellPx = viewportWidthPx.toDouble() / columns.toDouble()
        val requiredPx = ceil(cellPx * OVERSCAN).toInt().coerceAtLeast(1)
        val cappedMaximum = maxRequestSizePx.coerceAtMost(bucketsPx.last())
        val request = bucketsPx
            .firstOrNull { bucket -> bucket >= requiredPx && bucket <= cappedMaximum }
            ?: cappedMaximum

        return Budget(
            requestSizePx = request,
            estimatedArgbBytes = request.toLong() * request.toLong() * 4L,
        )
    }
}
