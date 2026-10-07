package com.photobook.app.feature.duplicates

import com.google.common.truth.Truth.assertThat
import java.io.ByteArrayInputStream
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

    @Test
    fun guaranteedCandidateBands_coverAdversarialDistanceEightPairs() {
        val left = 0L
        val leftKeys = DuplicateHash.guaranteedCandidateBandKeys(left, maxDistance = 8).toSet()

        for (bitWithinLegacyBand in 0 until 8) {
            val right = (0 until 8)
                .map { legacyBand -> (legacyBand * 8) + bitWithinLegacyBand }
                .fold(0L) { value, bit -> value or (1L shl bit) }

            assertThat(DuplicateHash.hammingDistance(left, right)).isEqualTo(8)

            val oldFixedBandShared = (0 until 8).any { band ->
                DuplicateHash.bandKey(left, band) == DuplicateHash.bandKey(right, band)
            }
            assertThat(oldFixedBandShared).isFalse()

            val rightKeys = DuplicateHash.guaranteedCandidateBandKeys(right, maxDistance = 8).toSet()
            assertThat(leftKeys.intersect(rightKeys)).isNotEmpty()
        }
    }

    @Test
    fun guaranteedCandidateBands_coverEverySingleBitPosition() {
        val baselineKeys = DuplicateHash.guaranteedCandidateBandKeys(0L, maxDistance = 8).toSet()

        for (bit in 0 until Long.SIZE_BITS) {
            val changed = 1L shl bit
            val changedKeys = DuplicateHash.guaranteedCandidateBandKeys(changed, maxDistance = 8).toSet()
            assertThat(baselineKeys.intersect(changedKeys)).isNotEmpty()
        }
    }

    @Test
    fun fullSha256_rejectsEqualPartialPrefixWithDifferentTail() {
        val prefix = ByteArray(64 * 1024) { index -> (index % 251).toByte() }
        val left = prefix + byteArrayOf(1, 2, 3, 4)
        val right = prefix + byteArrayOf(1, 2, 3, 5)

        val leftPartial = DuplicateHash.partialMd5Hex(
            ByteArrayInputStream(left),
            maxBytes = prefix.size,
        )
        val rightPartial = DuplicateHash.partialMd5Hex(
            ByteArrayInputStream(right),
            maxBytes = prefix.size,
        )
        assertThat(leftPartial).isEqualTo(rightPartial)

        val leftSha = DuplicateHash.sha256Hex(ByteArrayInputStream(left))
        val rightSha = DuplicateHash.sha256Hex(ByteArrayInputStream(right))
        assertThat(leftSha).isNotEqualTo(rightSha)
        assertThat(leftSha).hasLength(64)
        assertThat(rightSha).hasLength(64)
    }

    @Test
    fun fullSha256_matchesForByteIdenticalContent() {
        val bytes = ByteArray(96 * 1024) { index -> (index % 239).toByte() }

        assertThat(DuplicateHash.sha256Hex(ByteArrayInputStream(bytes)))
            .isEqualTo(DuplicateHash.sha256Hex(ByteArrayInputStream(bytes.copyOf())))
    }
}
