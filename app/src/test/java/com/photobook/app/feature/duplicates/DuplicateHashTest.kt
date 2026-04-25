package com.photobook.app.feature.duplicates

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DuplicateHashTest {
    @Test
    fun hammingDistance_countsDifferentBits() {
        assertThat(DuplicateHash.hammingDistance(0b1010L, 0b0011L)).isEqualTo(2)
    }

    @Test
    fun bandKey_extractsStableEightBitBand() {
        val hash = 0x1122334455667788L

        assertThat(DuplicateHash.bandKey(hash, bandIndex = 0)).isEqualTo(0x88L)
        assertThat(DuplicateHash.bandKey(hash, bandIndex = 3)).isEqualTo(0x55L)
    }
}
