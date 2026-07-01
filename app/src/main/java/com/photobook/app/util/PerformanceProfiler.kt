package com.photobook.app.util

import android.app.ActivityManager
import android.content.Context

enum class PerformanceTier {
    LITE,
    STANDARD,
}

class PerformanceProfiler private constructor(
    val currentTier: PerformanceTier,
) {
    val isLite: Boolean
        get() = currentTier == PerformanceTier.LITE

    val imageCacheMemoryPercent: Double
        get() = if (isLite) LITE_IMAGE_CACHE_PERCENT else STANDARD_IMAGE_CACHE_PERCENT

    val thumbnailRequestSizePx: Int
        get() = if (isLite) LITE_THUMBNAIL_SIZE_PX else STANDARD_THUMBNAIL_SIZE_PX

    val intelligenceBitmapMaxDimensionPx: Int
        get() = if (isLite) LITE_INTELLIGENCE_BITMAP_MAX_DIMENSION_PX else STANDARD_INTELLIGENCE_BITMAP_MAX_DIMENSION_PX

    val shouldRunMlSequentially: Boolean
        get() = isLite

    companion object {
        private const val LITE_TOTAL_MEMORY_THRESHOLD_BYTES = 2_684_354_560L // 2.5 GiB
        private const val LITE_IMAGE_CACHE_PERCENT = 0.08
        private const val STANDARD_IMAGE_CACHE_PERCENT = 0.15
        private const val LITE_THUMBNAIL_SIZE_PX = 256
        private const val STANDARD_THUMBNAIL_SIZE_PX = 512
        private const val LITE_INTELLIGENCE_BITMAP_MAX_DIMENSION_PX = 768
        private const val STANDARD_INTELLIGENCE_BITMAP_MAX_DIMENSION_PX = 1024

        fun from(context: Context): PerformanceProfiler {
            val activityManager = context.applicationContext
                .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)

            val tier = if (
                activityManager.isLowRamDevice ||
                memoryInfo.totalMem in 1..LITE_TOTAL_MEMORY_THRESHOLD_BYTES
            ) {
                PerformanceTier.LITE
            } else {
                PerformanceTier.STANDARD
            }
            return PerformanceProfiler(tier)
        }
    }
}
