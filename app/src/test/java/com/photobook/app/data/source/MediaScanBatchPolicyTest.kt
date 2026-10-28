package com.photobook.app.data.source

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MediaScanBatchPolicyTest {
    @Test
    fun largeLibraries_useBoundedCheckpointCounts() {
        assertThat(MediaScanBatchPolicy.batchCount(10_000)).isEqualTo(40)
        assertThat(MediaScanBatchPolicy.batchCount(50_000)).isEqualTo(196)
        assertThat(MediaScanBatchPolicy.batchCount(100_000)).isEqualTo(391)
    }

    @Test
    fun boundaries_areDeterministic() {
        assertThat(MediaScanBatchPolicy.batchCount(0)).isEqualTo(0)
        assertThat(MediaScanBatchPolicy.batchCount(1)).isEqualTo(1)
        assertThat(MediaScanBatchPolicy.batchCount(256)).isEqualTo(1)
        assertThat(MediaScanBatchPolicy.batchCount(257)).isEqualTo(2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeItemCount_failsClosed() {
        MediaScanBatchPolicy.batchCount(-1)
    }
}
