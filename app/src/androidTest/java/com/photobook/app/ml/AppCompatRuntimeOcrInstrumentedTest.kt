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

/** Disposable compatibility smoke: verifies the theme/dependency substitution does not break OCR runtime. */
@RunWith(AndroidJUnit4::class)
class AppCompatRuntimeOcrInstrumentedTest {

    @Test
    fun bundledOcrRuntimeRemainsHealthy() = runBlocking {
        val bitmap = Bitmap.createBitmap(1_600, 520, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 170f
            }
            canvas.drawText("PhotoBook OCR", 80f, 310f, paint)

            val result = LocalOcrEngine().recognize(bitmap)
            assertTrue("Bundled OCR should complete successfully", result.isSuccess)
            val normalized = result.getOrThrow().lowercase().replace(Regex("\\s+"), " ").trim()
            assertTrue("Expected stable PhotoBook token in: $normalized", normalized.contains("photobook"))
        } finally {
            bitmap.recycle()
        }
    }
}
