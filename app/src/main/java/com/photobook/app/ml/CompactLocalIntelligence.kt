package com.photobook.app.ml

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.media.FaceDetector

data class CompactLocalLabel(
    val label: String,
    val confidence: Float,
)

/** Small deterministic image features used when vendor model payloads exceed the release budget. */
object CompactLocalIntelligence {
    fun labels(bitmap: Bitmap): List<CompactLocalLabel> {
        if (bitmap.width < 8 || bitmap.height < 8) return emptyList()
        var red = 0L
        var green = 0L
        var blue = 0L
        var saturated = 0
        val stepX = (bitmap.width / 32).coerceAtLeast(1)
        val stepY = (bitmap.height / 32).coerceAtLeast(1)
        var samples = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16) and 0xff
                val g = (color shr 8) and 0xff
                val b = color and 0xff
                red += r
                green += g
                blue += b
                if (maxOf(r, g, b) - minOf(r, g, b) > 72) saturated += 1
                samples += 1
                x += stepX
            }
            y += stepY
        }
        if (samples == 0) return emptyList()
        val averageRed = red.toFloat() / samples
        val averageGreen = green.toFloat() / samples
        val averageBlue = blue.toFloat() / samples
        val saturationRatio = saturated.toFloat() / samples

        return buildList {
            if (averageGreen > averageRed * 1.12f && averageGreen > averageBlue * 1.08f) {
                add(CompactLocalLabel("nature", 0.62f))
            }
            if (averageRed > averageBlue * 1.25f && saturationRatio > 0.28f) {
                add(CompactLocalLabel("food", 0.61f))
            }
            if (averageRed > averageBlue * 1.35f && averageGreen > averageBlue * 1.15f) {
                add(CompactLocalLabel("sunset", 0.66f))
            }
        }
    }

    /**
     * Best-effort face detection used by optional tagging features.
     * A detector/setup failure intentionally degrades to no tags instead of failing the ML job.
     */
    fun detectFaces(bitmap: Bitmap): List<Rect> {
        return runCatching { detectFacesStrict(bitmap) }.getOrDefault(emptyList())
    }

    /**
     * Strict face detection for privacy-sensitive callers such as Safe Share.
     * Setup/detector failures are surfaced so callers can fail closed instead of treating them as
     * proof that no face exists.
     */
    fun detectFacesStrict(bitmap: Bitmap): List<Rect> {
        if (bitmap.width < 64 || bitmap.height < 64) return emptyList()
        val evenWidth = bitmap.width - (bitmap.width % 2)
        if (evenWidth < 2) return emptyList()

        // FaceDetector requires the supplied bitmap dimensions to exactly match the dimensions
        // passed to its constructor, and its width must be even. Crop only the final column for an
        // odd-width source; never recycle or mutate the caller-owned bitmap.
        val detectorSource = if (bitmap.width == evenWidth) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, evenWidth, bitmap.height)
        }
        val rgb565 = try {
            detectorSource.copy(Bitmap.Config.RGB_565, false)
                ?: error("Unable to create RGB565 bitmap for face detection")
        } finally {
            if (detectorSource !== bitmap) detectorSource.recycle()
        }

        return try {
            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val count = FaceDetector(rgb565.width, rgb565.height, MAX_FACES).findFaces(rgb565, faces)
            buildList {
                for (index in 0 until count.coerceAtMost(MAX_FACES)) {
                    val face = faces[index] ?: continue
                    val midpoint = PointF()
                    face.getMidPoint(midpoint)
                    val radius = (face.eyesDistance() * 1.65f).toInt().coerceAtLeast(12)
                    add(
                        Rect(
                            (midpoint.x - radius).toInt(),
                            (midpoint.y - radius).toInt(),
                            (midpoint.x + radius).toInt(),
                            (midpoint.y + radius).toInt(),
                        ),
                    )
                }
            }
        } finally {
            rgb565.recycle()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun ocr(bitmap: Bitmap): Result<String> {
        return Result.failure(
            UnsupportedOperationException(
                "No compact Latin OCR model is available within the hard offline size budget",
            ),
        )
    }

    private const val MAX_FACES = 8
}
