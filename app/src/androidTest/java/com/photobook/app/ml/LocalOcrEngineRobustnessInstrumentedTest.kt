package com.photobook.app.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deterministic camera-like OCR robustness surrogate for environments where physical-device/photo
 * validation is unavailable. This is intentionally synthetic and must never be reported as real
 * camera/photo evidence.
 */
@RunWith(AndroidJUnit4::class)
class LocalOcrEngineRobustnessInstrumentedTest {

    @Test
    fun baselineDocumentText_isRecognized() = runBlocking {
        assertCoreTokens(recognize(createDocumentBitmap()), "baseline")
    }

    @Test
    fun slightRotation_isRecognized() = runBlocking {
        val source = createDocumentBitmap()
        val rotated = rotate(source, 5f)
        try {
            assertCoreTokens(recognize(rotated), "rotation-5deg")
        } finally {
            if (rotated !== source) rotated.recycle()
            source.recycle()
        }
    }

    @Test
    fun lowContrastText_isRecognized() = runBlocking {
        val bitmap = createDocumentBitmap(
            background = Color.rgb(235, 235, 235),
            foreground = Color.rgb(70, 70, 70),
        )
        try {
            assertCoreTokens(recognize(bitmap), "low-contrast")
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun jpegQuality45_isRecognized() = runBlocking {
        val source = createDocumentBitmap()
        val degraded = jpegRoundTrip(source, 45)
        try {
            assertCoreTokens(recognize(degraded), "jpeg-q45")
        } finally {
            degraded.recycle()
            source.recycle()
        }
    }

    @Test
    fun reducedResolution_isRecognized() = runBlocking {
        val bitmap = createDocumentBitmap(width = 1200, height = 420, textSize = 76f)
        try {
            assertCoreTokens(recognize(bitmap), "reduced-resolution")
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognize(bitmap: Bitmap): String {
        val result = LocalOcrEngine().recognize(bitmap)
        assertTrue("Bundled OCR should complete successfully", result.isSuccess)
        return normalize(result.getOrThrow())
    }

    private fun assertCoreTokens(text: String, variant: String) {
        assertTrue("$variant: expected photobook in: $text", text.contains("photobook"))
        assertTrue("$variant: expected invoice in: $text", text.contains("invoice"))
        assertTrue("$variant: expected numeric marker 73951 in: $text", text.contains("73951"))
        assertTrue("$variant: expected total in: $text", text.contains("total"))
    }

    private fun createDocumentBitmap(
        width: Int = 2400,
        height: Int = 720,
        textSize: Float = 145f,
        background: Int = Color.WHITE,
        foreground: Int = Color.BLACK,
    ): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(background)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = foreground
                this.textSize = textSize
            }
            val scale = width / 2400f
            canvas.drawText("PhotoBook INVOICE", 70f * scale, 235f * height / 720f, paint)
            canvas.drawText("ORDER 73951", 70f * scale, 430f * height / 720f, paint)
            canvas.drawText("TOTAL 500", 70f * scale, 625f * height / 720f, paint)
        }
    }

    private fun rotate(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun jpegRoundTrip(source: Bitmap, quality: Int): Bitmap {
        val encoded = ByteArrayOutputStream().use { output ->
            assertTrue("JPEG compression failed", source.compress(Bitmap.CompressFormat.JPEG, quality, output))
            output.toByteArray()
        }
        return requireNotNull(BitmapFactory.decodeByteArray(encoded, 0, encoded.size)) {
            "JPEG decode failed"
        }
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
