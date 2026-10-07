package com.photobook.app.feature.duplicates

import java.io.InputStream
import java.security.MessageDigest

object DuplicateHash {
    fun hammingDistance(left: Long, right: Long): Int {
        return java.lang.Long.bitCount(left xor right)
    }

    fun bandKey(hash: Long, bandIndex: Int, bandBits: Int = DEFAULT_BAND_BITS): Long {
        require(bandIndex >= 0)
        require(bandBits in 1..16)
        val shift = bandIndex * bandBits
        val mask = (1L shl bandBits) - 1L
        return (hash ushr shift) and mask
    }

    /**
     * Splits the 64-bit hash into maxDistance + 1 disjoint bands.
     *
     * If two hashes differ in at most [maxDistance] bits, at least one of these bands must be
     * identical by the pigeonhole principle. Candidate generation using these keys therefore
     * cannot miss a pair that the final Hamming-distance check would accept.
     */
    fun guaranteedCandidateBandKeys(
        hash: Long,
        maxDistance: Int,
    ): LongArray {
        require(maxDistance in 0..63)
        val bandCount = maxDistance + 1
        val baseWidth = Long.SIZE_BITS / bandCount
        val widerBandCount = Long.SIZE_BITS % bandCount
        var shift = 0

        return LongArray(bandCount) { bandIndex ->
            val width = baseWidth + if (bandIndex < widerBandCount) 1 else 0
            val mask = if (width == Long.SIZE_BITS) {
                -1L
            } else {
                (1L shl width) - 1L
            }
            val value = (hash ushr shift) and mask
            shift += width

            if (width == Long.SIZE_BITS) {
                value
            } else {
                (bandIndex.toLong() shl BAND_INDEX_SHIFT) or value
            }
        }
    }

    fun partialMd5Hex(
        input: InputStream,
        maxBytes: Int,
    ): String {
        require(maxBytes > 0)
        return digestHex(input, algorithm = "MD5", maxBytes = maxBytes)
    }

    fun sha256Hex(input: InputStream): String {
        return digestHex(input, algorithm = "SHA-256", maxBytes = null)
    }

    private fun digestHex(
        input: InputStream,
        algorithm: String,
        maxBytes: Int?,
    ): String {
        val digest = MessageDigest.getInstance(algorithm)
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalRead = 0

        while (maxBytes == null || totalRead < maxBytes) {
            val toRead = if (maxBytes == null) {
                buffer.size
            } else {
                minOf(buffer.size, maxBytes - totalRead)
            }
            if (toRead <= 0) break
            val read = input.read(buffer, 0, toRead)
            if (read <= 0) break
            digest.update(buffer, 0, read)
            totalRead += read
        }

        return digest.digest().joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    const val DEFAULT_BAND_BITS = 8
    private const val BAND_INDEX_SHIFT = 56
}
