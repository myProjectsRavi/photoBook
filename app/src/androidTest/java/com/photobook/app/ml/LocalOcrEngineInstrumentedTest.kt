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
    fun recognizesMixedCaseEnglishAndNumbersLocally() = runBlocking {
        val bitmap = Bitmap.createBitmap(1_600, 520, Bitmap.Config.ARGB_8888)
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
