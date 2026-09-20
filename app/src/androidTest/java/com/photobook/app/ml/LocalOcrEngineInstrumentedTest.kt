package com.photobook.app.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalOcrEngineInstrumentedTest {

    @Test
    fun recognizeLayout_returnsNormalizedGeometryAndKeepsBitmapOwnedByCaller() = runBlocking {
        val bitmap = Bitmap.createBitmap(1_600, 600, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 170f
            }
            canvas.drawText("Invoice 001234", 80f, 330f, paint)

            val result = LocalOcrEngine().recognizeLayout(bitmap)
            assertTrue("Bundled OCR layout should complete successfully", result.isSuccess)
            assertTrue("OCR must not recycle caller bitmap", !bitmap.isRecycled)

            val layout = result.getOrThrow()
            assertTrue("Expected OCR geometry", layout.elements.isNotEmpty())
            assertTrue("Expected numeric text", layout.elements.any { it.text.contains("001234") || it.text.contains("1234") })
            layout.elements.forEach { element ->
                assertTrue("Each retained element must have a polygon", element.corners.size == 4)
                element.corners.forEach { point ->
                    assertTrue("Normalized X out of range: ${point.x}", point.x in 0f..1f)
                    assertTrue("Normalized Y out of range: ${point.y}", point.y in 0f..1f)
                }
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun recognizesMixedCaseEnglishAndNumbersLocally() = runBlocking {
        // Keep the complete fixture inside the bitmap. The previous 1,600 px canvas clipped the
        // trailing digits on the API 35 emulator, turning "12345" into an OCR-visible "12".
        val bitmap = Bitmap.createBitmap(2_400, 520, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 150f
            }
            canvas.drawText("PhotoBook ABC xyz 12345", 45f, 300f, paint)

            val result = LocalOcrEngine().recognize(bitmap)
            assertTrue("Bundled OCR should complete successfully", result.isSuccess)

            val normalized = result.getOrThrow()
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .trim()
            assertTrue("Expected PhotoBook text in: $normalized", normalized.contains("photobook"))
            assertTrue("Expected uppercase token in: $normalized", normalized.contains("abc"))
            assertTrue("Expected lowercase token in: $normalized", normalized.contains("xyz"))
            assertTrue("Expected numeric token in: $normalized", normalized.contains("12345"))
        } finally {
            bitmap.recycle()
        }
    }
}
