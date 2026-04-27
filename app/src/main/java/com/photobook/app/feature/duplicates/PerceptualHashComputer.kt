package com.photobook.app.feature.duplicates

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class PerceptualHashComputer @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun computeFromUri(uriString: String): Long? {
        val source = decodeSampledBitmap(Uri.parse(uriString), HASH_SOURCE_MAX_DIMENSION) ?: return null
        val scaled = runCatching {
            Bitmap.createScaledBitmap(source, HASH_WIDTH, HASH_HEIGHT, true)
        }.getOrNull()

        if (scaled == null) {
            source.recycleSafely()
            return null
        }

        return try {
            val width = scaled.width
            val height = scaled.height
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)

            var hash = 0L
            var bit = 0
            for (y in 0 until height) {
                for (x in 0 until width - 1) {
                    val leftPixel = pixels[y * width + x]
                    val rightPixel = pixels[y * width + x + 1]
                    
                    val leftLum = ((leftPixel shr 16 and 0xFF) * 299 + 
                                  (leftPixel shr 8 and 0xFF) * 587 + 
                                  (leftPixel and 0xFF) * 114) / 1000
                    val rightLum = ((rightPixel shr 16 and 0xFF) * 299 + 
                                   (rightPixel shr 8 and 0xFF) * 587 + 
                                   (rightPixel and 0xFF) * 114) / 1000
                    
                    if (leftLum > rightLum) {
                        hash = hash or (1L shl bit)
                    }
                    bit += 1
                }
            }
            hash
        } finally {
            if (scaled !== source) {
                scaled.recycleSafely()
            }
            source.recycleSafely()
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

    private fun luminance(pixel: Int): Int {
        val red = android.graphics.Color.red(pixel)
        val green = android.graphics.Color.green(pixel)
        val blue = android.graphics.Color.blue(pixel)
        return (red * 299 + green * 587 + blue * 114) / 1000
    }

    private fun Bitmap.recycleSafely() {
        runCatching {
            if (!isRecycled) {
                recycle()
            }
        }
    }

    companion object {
        private const val HASH_WIDTH = 9
        private const val HASH_HEIGHT = 8
        private const val HASH_SOURCE_MAX_DIMENSION = 96
    }
}
