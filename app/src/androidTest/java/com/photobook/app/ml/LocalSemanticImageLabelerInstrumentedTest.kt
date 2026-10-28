package com.photobook.app.ml

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Calls the real packaged TFLite model through its real TensorFlow metadata and
 * interpreter implementations. A metadata-only mock would miss the null-union crash.
 */
@RunWith(AndroidJUnit4::class)
class LocalSemanticImageLabelerInstrumentedTest {
    @Test
    fun actualPackagedModel_initializesAndRunsQuantizedInferenceOffline() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.RED)
            val result = LocalSemanticImageLabeler(context).labels(bitmap)
            assertTrue("Bundled semantic model must initialize and infer: ${result.exceptionOrNull()}", result.isSuccess)
            val labels = result.getOrThrow()
            assertFalse("Actual packaged model must produce canonical score slots", labels.isEmpty())
            assertTrue(
                "Scores must remain finite and normalized",
                labels.all { it.confidence.isFinite() && it.confidence in 0f..1f },
            )
        } finally {
            bitmap.recycle()
        }
    }
}
