package com.photobook.app.feature.copytext

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.ml.BundledOnDeviceIntelligence
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable OCR-quality proof for a lower Copy Text decode cap. */
@RunWith(AndroidJUnit4::class)
class OnDevicePhotoTextExtractorAccuracyInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun fullImage_recognizesReadableTextAfterLowerCapSampling() = runBlocking {
        val file = seeded("copytext-accuracy-full.png")
        val result = extractor().extract(Uri.fromFile(file).toString())
        assertContainsStableTokens("full", result)
    }

    @Test
    fun nearFullRegion_recognizesReadableTextAfterLowerCapSampling() = runBlocking {
        val file = seeded("copytext-accuracy-region.png")
        val result = extractor().extractRegion(
            photoUri = Uri.fromFile(file).toString(),
            region = NormalizedTextRegion(0.02f, 0.02f, 0.98f, 0.98f),
        )
        assertContainsStableTokens("region", result)
    }

    private fun extractor() = OnDevicePhotoTextExtractor(
        context = context.applicationContext,
        onDeviceIntelligence = BundledOnDeviceIntelligence(),
    )

    private fun seeded(name: String): File {
        val file = File(context.cacheDir, name)
        assertTrue("Workflow did not seed ${file.absolutePath}", file.isFile && file.length() > 0L)
        return file
    }

    private fun assertContainsStableTokens(label: String, result: ExtractedTextResult) {
        assertTrue("$label Copy Text should succeed, got $result", result is ExtractedTextResult.Success)
        val normalized = (result as ExtractedTextResult.Success).text.lowercase()
        assertTrue("Expected PhotoBook in $label OCR: $normalized", normalized.contains("photobook"))
        assertTrue("Expected receipt in $label OCR: $normalized", normalized.contains("receipt"))
    }
}
