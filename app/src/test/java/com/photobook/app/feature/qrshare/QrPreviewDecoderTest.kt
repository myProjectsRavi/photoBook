package com.photobook.app.feature.qrshare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QrPreviewDecoderTest {

    @Test
    fun calculateSampleSize_keepsAlreadyBoundedPreviewAtFullResolution() {
        assertThat(QrPreviewDecoder.calculateSampleSize(800, 600)).isEqualTo(1)
    }

    @Test
    fun calculateSampleSize_downsamplesLargeCameraImageToBoundedPreview() {
        assertThat(QrPreviewDecoder.calculateSampleSize(4_000, 3_000)).isEqualTo(4)
    }

    @Test
    fun calculateSampleSize_boundsVeryWideImageWithoutOverflow() {
        assertThat(QrPreviewDecoder.calculateSampleSize(12_000, 1_000)).isEqualTo(16)
    }

    @Test
    fun calculateSampleSize_rejectsInvalidDimensions() {
        assertThat(QrPreviewDecoder.calculateSampleSize(0, 1_000)).isNull()
        assertThat(QrPreviewDecoder.calculateSampleSize(1_000, -1)).isNull()
    }
}
