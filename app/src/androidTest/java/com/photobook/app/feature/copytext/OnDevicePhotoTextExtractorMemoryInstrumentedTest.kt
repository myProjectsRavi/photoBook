package com.photobook.app.feature.copytext

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.ml.BundledOnDeviceIntelligence
import com.photobook.app.ml.LocalOcrEngine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Disposable device proof for Copy Text's current peak bitmap budgets.
 *
 * The fixtures are intentionally blank so primary OCR returns no text and the production extractor
 * executes its full-size enhanced-bitmap fallback. Host CI polls dumpsys meminfo while these tests
 * run; this class only proves the real operation completes without an app-level OCR error/OOM.
 */
@RunWith(AndroidJUnit4::class)
class OnDevicePhotoTextExtractorMemoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fullImageFallback_completesAtCurrent3600PixelBudget() = runBlocking {
        val fixture = createBlankJpeg("copytext-full-3600.jpg", 3_600)
        try {
            releaseFixtureGenerationMemory()
            println("COPYTEXT_MEMORY_PHASE=FULL_EXTRACT_START")
            val extractor = realExtractor()
            val result = extractor.extract(Uri.fromFile(fixture).toString())
            println("COPYTEXT_MEMORY_PHASE=FULL_EXTRACT_END result=${result::class.java.simpleName}")
            assertFalse("Full-image Copy Text returned an OCR error: $result", result is ExtractedTextResult.Error)
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun nearFullRegionFallback_completesAtCurrent3200PixelBudget() = runBlocking {
        val fixture = createBlankJpeg("copytext-region-3200.jpg", 3_200)
        try {
            releaseFixtureGenerationMemory()
            println("COPYTEXT_MEMORY_PHASE=REGION_EXTRACT_START")
            val extractor = realExtractor()
            val result = extractor.extractRegion(
                photoUri = Uri.fromFile(fixture).toString(),
                region = NormalizedTextRegion(
                    left = 0.01f,
                    top = 0.01f,
                    right = 0.99f,
                    bottom = 0.99f,
                ),
            )
            println("COPYTEXT_MEMORY_PHASE=REGION_EXTRACT_END result=${result::class.java.simpleName}")
            assertFalse("Region Copy Text returned an OCR error: $result", result is ExtractedTextResult.Error)
        } finally {
            fixture.delete()
        }
    }

    private fun realExtractor(): OnDevicePhotoTextExtractor {
        return OnDevicePhotoTextExtractor(
            context = context.applicationContext,
            onDeviceIntelligence = BundledOnDeviceIntelligence(),
            localOcrEngine = LocalOcrEngine(),
        )
    }

    private fun createBlankJpeg(name: String, dimension: Int): File {
        val file = File(context.cacheDir, name)
        file.delete()
        val bitmap = Bitmap.createBitmap(dimension, dimension, Bitmap.Config.ARGB_8888)
        try {
            Canvas(bitmap).drawColor(Color.WHITE)
            FileOutputStream(file).use { output ->
                assertTrue("Failed to encode $name", bitmap.compress(Bitmap.CompressFormat.JPEG, 92, output))
            }
        } finally {
            bitmap.recycle()
        }
        assertTrue("Fixture was not written: ${file.absolutePath}", file.isFile && file.length() > 0L)
        return file
    }

    private fun releaseFixtureGenerationMemory() {
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
        Thread.sleep(1_000)
    }
}
