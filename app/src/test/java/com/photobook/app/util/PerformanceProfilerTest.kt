package com.photobook.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PerformanceProfilerTest {
    @Test
    fun liteTier_preservesConservativeCacheAndDecodeCaps() {
        val profiler = PerformanceProfiler.forTier(PerformanceTier.LITE)

        assertThat(profiler.imageCacheMemoryPercent).isEqualTo(0.08)
        assertThat(profiler.thumbnailRequestSizePx).isEqualTo(256)
        assertThat(profiler.shouldRunMlSequentially).isTrue()
    }

    @Test
    fun standardTier_preservesExistingCacheAndDecodeCaps() {
        val profiler = PerformanceProfiler.forTier(PerformanceTier.STANDARD)

        assertThat(profiler.imageCacheMemoryPercent).isEqualTo(0.15)
        assertThat(profiler.thumbnailRequestSizePx).isEqualTo(512)
        assertThat(profiler.shouldRunMlSequentially).isFalse()
    }
}
