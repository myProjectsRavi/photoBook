package com.photobook.app.data.source

internal object MediaScanBatchPolicy {
    const val DEFAULT_BATCH_SIZE = 256

    fun batchCount(itemCount: Int, batchSize: Int = DEFAULT_BATCH_SIZE): Int {
        require(itemCount >= 0)
        require(batchSize > 0)
        if (itemCount == 0) return 0
        return (itemCount + batchSize - 1) / batchSize
    }
}
