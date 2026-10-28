package com.photobook.app.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ThumbnailDecodePolicyTest {
    @Test
    fun standardThreeColumn1080Viewport_uses384BucketInsteadOf512() {
        val budget = ThumbnailDecodePolicy.forGrid(
            viewportWidthPx = 1080,
            columns = 3,
            maxRequestSizePx = 512,
        )

        assertThat(budget.requestSizePx).isEqualTo(384)
        assertThat(budget.estimatedArgbBytes).isEqualTo(589_824L)
        assertThat(budget.estimatedArgbBytes).isLessThan(1_048_576L)
    }

    @Test
    fun liteTier_neverExceedsExisting256Cap() {
        val budget = ThumbnailDecodePolicy.forGrid(
            viewportWidthPx = 1440,
            columns = 3,
            maxRequestSizePx = 256,
        )

        assertThat(budget.requestSizePx).isEqualTo(256)
        assertThat(budget.estimatedArgbBytes).isEqualTo(262_144L)
    }

    @Test
    fun compactFourColumn720Viewport_uses192Bucket() {
        val budget = ThumbnailDecodePolicy.forGrid(
            viewportWidthPx = 720,
            columns = 4,
            maxRequestSizePx = 512,
        )

        assertThat(budget.requestSizePx).isEqualTo(192)
        assertThat(budget.estimatedArgbBytes).isEqualTo(147_456L)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidColumnCount_failsClosed() {
        ThumbnailDecodePolicy.forGrid(1080, 0, 512)
    }
}
