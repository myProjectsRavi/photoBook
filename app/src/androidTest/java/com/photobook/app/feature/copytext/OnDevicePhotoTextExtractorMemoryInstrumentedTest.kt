package com.photobook.app.feature.copytext

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.ml.BundledOnDeviceIntelligence
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Disposable device proof for Copy Text bitmap budgets.
 *
 * Each method is executed in a fresh app process by the disposable workflow. The blank fixtures
 * force the production extractor through its enhanced-bitmap fallback. The extractor intentionally
 * uses its production default OCR dependency so the test exercises the Hilt application-scoped
 * LocalOcrEngine path rather than constructing a second recognizer just for the test.
 */
@RunWith(AndroidJUnit4::class)
class OnDevicePhotoTextExtractorMemoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun smallControl_completesAt512Pixels() = runBlocking {
        val fixture = createBlankJpeg("copytext-control-512.jpg", 512)
        try {
            assertFixtureBoundsReadable(fixture, 512)
            releaseFixtureGenerationMemory()
            println("COPYTEXT_MEMORY_PHASE=CONTROL_EXTRACT_START")
            val result = realExtractor().extract(Uri.fromFile(fixture).toString())
            println("COPYTEXT_MEMORY_PHASE=CONTROL_EXTRACT_END result=$result")
            assertFalse("Small Copy Text control returned an error: $result", result is ExtractedTextResult.Error)
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun fullImageFallback_completesAtCurrent3600PixelBudget() = runBlocking {
        val fixture = createBlankJpeg("copytext-full-3600.jpg", 3_600)
        try {
            assertFixtureBoundsReadable(fixture, 3_600)
            releaseFixtureGenerationMemory()
            println("COPYTEXT_MEMORY_PHASE=FULL_EXTRACT_START")
            val result = realExtractor().extract(Uri.fromFile(fixture).toString())
            println("COPYTEXT_MEMORY_PHASE=FULL_EXTRACT_END result=$result")
            assertFalse("Full-image Copy Text returned an OCR error: $result", result is ExtractedTextResult.Error)
        } finally {
            fixture.delete()
        }
    }

    @Test
    fun nearFullRegionFallback_completesAtCurrent3200PixelBudget() = runBlocking {
        val fixture = createBlankJpeg("copytext-region-3200.jpg", 3_200)
        try {
            assertFixtureBoundsReadable(fixture, 3_200)
            releaseFixtureGenerationMemory()
            println("COPYTEXT_MEMORY_PHASE=REGION_EXTRACT_START")
            val result = realExtractor().extractRegion(
                photoUri = Uri.fromFile(fixture).toString(),
                region = NormalizedTextRegion(
                    left = 0.01f,
                    top = 0.01f,
                    right = 0.99f,
                    bottom = 0.99f,
                ),
            )
            println("COPYTEXT_MEMORY_PHASE=REGION_EXTRACT_END result=$result")
            assertFalse("Region Copy Text returned an OCR error: $result", result is ExtractedTextResult.Error)
        } finally {
            fixture.delete()
        }
    }

    private fun realExtractor(): OnDevicePhotoTextExtractor {
        return OnDevicePhotoTextExtractor(
            context = context.applicationContext,
            onDeviceIntelligence = BundledOnDeviceIntelligence(),
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

    private fun assertFixtureBoundsReadable(file: File, expectedDimension: Int) {
        val uri = Uri.fromFile(file)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val stream = context.contentResolver.openInputStream(uri)
        assertTrue("ContentResolver could not open fixture URI: $uri", stream != null)
        stream!!.use { BitmapFactory.decodeStream(it, null, options) }
        assertEquals("Fixture width was not decodable", expectedDimension, options.outWidth)
        assertEquals("Fixture height was not decodable", expectedDimension, options.outHeight)
    }

    private fun releaseFixtureGenerationMemory() {
        repeat(2) {
            Runtime.getRuntime().gc()
            System.runFinalization()
        }
        Thread.sleep(1_000)
    }
}
