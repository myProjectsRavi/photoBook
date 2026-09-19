package com.photobook.app.feature.qrshare

import android.graphics.Bitmap
import android.graphics.BitmapFactory

/**
 * Decodes only a bounded preview representation of an already-validated QR transfer payload.
 *
 * QR payload byte limits protect encoded size, not decoded bitmap memory. Keep preview decoding
 * separately bounded so a highly-compressed image cannot allocate an unbounded ARGB bitmap.
 */
internal object QrPreviewDecoder {
    private const val MAX_PREVIEW_EDGE_PX = 1_280
    private const val MAX_PREVIEW_PIXELS = 2_000_000L
    private const val MAX_SAMPLE_SIZE = 1 shl 29

    fun decode(bytes: ByteArray): Bitmap? {
        if (bytes.isEmpty() || bytes.size > QrTransferProtocol.MAX_TRANSFER_BYTES) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        }.getOrNull()
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        val sampleSize = calculateSampleSize(width, height) ?: return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = runCatching {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }.getOrNull() ?: return null

        val decodedPixels = bitmap.width.toLong() * bitmap.height.toLong()
        if (
            bitmap.width > MAX_PREVIEW_EDGE_PX ||
            bitmap.height > MAX_PREVIEW_EDGE_PX ||
            decodedPixels > MAX_PREVIEW_PIXELS
        ) {
            bitmap.recycle()
            return null
        }
        return bitmap
    }

    internal fun calculateSampleSize(width: Int, height: Int): Int? {
        if (width <= 0 || height <= 0) return null
        var sampleSize = 1
        while (sampleSize <= MAX_SAMPLE_SIZE) {
            val sampledWidth = ceilDiv(width, sampleSize)
            val sampledHeight = ceilDiv(height, sampleSize)
            val sampledPixels = sampledWidth.toLong() * sampledHeight.toLong()
            if (
                sampledWidth <= MAX_PREVIEW_EDGE_PX &&
                sampledHeight <= MAX_PREVIEW_EDGE_PX &&
                sampledPixels <= MAX_PREVIEW_PIXELS
            ) {
                return sampleSize
            }
            sampleSize *= 2
        }
        return null
    }

    private fun ceilDiv(value: Int, divisor: Int): Int {
        return ((value.toLong() + divisor.toLong() - 1L) / divisor.toLong()).toInt()
    }
}
