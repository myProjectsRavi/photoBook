package com.photobook.app.feature.duplicates

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

    const val DEFAULT_BAND_BITS = 8
}
