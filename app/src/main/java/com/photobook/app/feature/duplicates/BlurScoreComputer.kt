package com.photobook.app.feature.duplicates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BlurScoreComputer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun computeFromUri(uriString: String): Double? {
        val bitmap = decodeSampledBitmap(Uri.parse(uriString), BLUR_SAMPLE_MAX_DIMENSION) ?: return null
        return try {
            computeFromBitmap(bitmap)
        } finally {
            bitmap.recycleSafely()
        }
    }

    fun computeFromBitmap(bitmap: Bitmap): Double? {
        if (bitmap.width < 3 || bitmap.height < 3) {
            return null
        }

        return try {
            val width = bitmap.width
            val height = bitmap.height
            val rowPixels = IntArray(width)
            var rowAbove = IntArray(width)
            var rowCenter = IntArray(width)
            var rowBelow = IntArray(width)

            fillLuminanceRow(bitmap, rowPixels, rowAbove, 0)
            fillLuminanceRow(bitmap, rowPixels, rowCenter, 1)
            fillLuminanceRow(bitmap, rowPixels, rowBelow, 2)

            var sum = 0.0
            var sumSquares = 0.0
            var count = 0

            for (y in 1 until height - 1) {
                for (x in 1 until width - 1) {
                    val center = rowCenter[x]
                    val up = rowAbove[x]
                    val down = rowBelow[x]
                    val left = rowCenter[x - 1]
                    val right = rowCenter[x + 1]
                    val laplacian = (4 * center - up - down - left - right).toDouble()
                    sum += laplacian
                    sumSquares += laplacian * laplacian
                    count += 1
                }

                if (y < height - 2) {
                    val reusable = rowAbove
                    rowAbove = rowCenter
                    rowCenter = rowBelow
                    rowBelow = reusable
                    fillLuminanceRow(bitmap, rowPixels, rowBelow, y + 2)
                }
            }

            if (count == 0) return null
            val mean = sum / count.toDouble()
            (sumSquares / count.toDouble()) - (mean * mean)
        } catch (_: Throwable) {
            null
        }
    }

    private fun fillLuminanceRow(
        bitmap: Bitmap,
        rowPixels: IntArray,
        targetLuminance: IntArray,
        y: Int,
    ) {
        bitmap.getPixels(rowPixels, 0, bitmap.width, 0, y, bitmap.width, 1)
        for (x in rowPixels.indices) {
            val pixel = rowPixels[x]
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            targetLuminance[x] = (r * 299 + g * 587 + b * 114) / 1000
        }
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx || bounds.outHeight / sample > maxDimensionPx) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private fun Bitmap.recycleSafely() {
        runCatching {
            if (!isRecycled) {
                recycle()
            }
        }
    }

    companion object {
        private const val BLUR_SAMPLE_MAX_DIMENSION = 256
    }
}
