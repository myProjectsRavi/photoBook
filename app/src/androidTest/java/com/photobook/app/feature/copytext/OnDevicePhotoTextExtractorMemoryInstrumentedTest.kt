package com.photobook.app.feature.copytext

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.ml.BundledOnDeviceIntelligence
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Disposable device proof for Copy Text bitmap budgets.
 *
 * The workflow seeds blank image fixtures into the target app cache before each instrumentation
 * process starts. This is deliberate: generating 3200/3600 px source bitmaps inside the measured
 * app process would pollute the very memory peak this proof is trying to attribute to Copy Text.
 * Each case therefore measures only the production extractor path, in a fresh app process, using
 * the production default Hilt-shared OCR dependency.
 */
@RunWith(AndroidJUnit4::class)
class OnDevicePhotoTextExtractorMemoryInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun smallControl_completesAt512Pixels() = runBlocking {
        val fixture = seededFixture("copytext-control-512.png", 512)
        println("COPYTEXT_MEMORY_PHASE=CONTROL_EXTRACT_START")
        val result = realExtractor().extract(Uri.fromFile(fixture).toString())
        println("COPYTEXT_MEMORY_PHASE=CONTROL_EXTRACT_END result=$result")
        assertFalse("Small Copy Text control returned an error: $result", result is ExtractedTextResult.Error)
    }

    @Test
    fun fullImageFallback_completesAtCurrent3600PixelBudget() = runBlocking {
        val fixture = seededFixture("copytext-full-3600.png", 3_600)
        println("COPYTEXT_MEMORY_PHASE=FULL_EXTRACT_START")
        val result = realExtractor().extract(Uri.fromFile(fixture).toString())
        println("COPYTEXT_MEMORY_PHASE=FULL_EXTRACT_END result=$result")
        assertFalse("Full-image Copy Text returned an OCR error: $result", result is ExtractedTextResult.Error)
    }

    @Test
    fun nearFullRegionFallback_completesAtCurrent3200PixelBudget() = runBlocking {
        val fixture = seededFixture("copytext-region-3200.png", 3_200)
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
    }

    private fun realExtractor(): OnDevicePhotoTextExtractor {
        return OnDevicePhotoTextExtractor(
            context = context.applicationContext,
            onDeviceIntelligence = BundledOnDeviceIntelligence(),
        )
    }

    private fun seededFixture(name: String, expectedDimension: Int): File {
        val file = File(context.cacheDir, name)
        assertTrue("Workflow did not seed fixture: ${file.absolutePath}", file.isFile && file.length() > 0L)
        assertFixtureBoundsReadable(file, expectedDimension)
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
}
