package com.photobook.app.ml

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Disposable release-certification coverage only.
 *
 * These fixtures deliberately approximate common camera degradation without pretending to be a
 * representative real-photo corpus. The goal is reproducible regression pressure on the exact
 * bundled OCR engine when physical-device testing is unavailable.
 */
@RunWith(AndroidJUnit4::class)
class LocalOcrRobustnessInstrumentedTest {

    @Test
    fun recognizesDeterministicCameraLikeDegradationsOffline() = runBlocking {
        val engine = LocalOcrEngine()
        val fixtures = listOf(
            Fixture("baseline") { renderReceipt() },
            Fixture("jpeg_q55") { source -> jpegRoundTrip(source, 55) },
            Fixture("rotate_plus_6") { source -> rotate(source, 6f) },
            Fixture("rotate_minus_6") { source -> rotate(source, -6f) },
            Fixture("downsample_blur") { source -> downsampleUpsample(source) },
            Fixture("horizontal_skew") { source -> skew(source, 0.10f) },
            Fixture("low_contrast") { renderReceipt(lowContrast = true) },
        )

        fixtures.forEach { fixture ->
            val baseline = renderReceipt()
            val bitmap = if (fixture.directFactory != null) {
                baseline.recycle()
                fixture.directFactory.invoke()
            } else {
                try {
                    fixture.transform!!.invoke(baseline)
                } finally {
                    baseline.recycle()
                }
            }

            try {
                val start = SystemClock.elapsedRealtime()
                val result = engine.recognize(bitmap)
                val elapsed = SystemClock.elapsedRealtime() - start
                assertTrue("${fixture.name}: bundled OCR failed: ${result.exceptionOrNull()}", result.isSuccess)

                val normalized = normalize(result.getOrThrow())
                println("OCR_ROBUSTNESS fixture=${fixture.name} elapsed_ms=$elapsed text=$normalized")
                assertTrue("${fixture.name}: expected photobook in '$normalized'", normalized.contains("photobook"))
                assertTrue("${fixture.name}: expected invoice in '$normalized'", normalized.contains("invoice"))
                assertTrue("${fixture.name}: expected 12345 in '$normalized'", normalized.contains("12345"))
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun renderReceipt(lowContrast: Boolean = false): Bitmap {
        val bitmap = Bitmap.createBitmap(2_400, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = if (lowContrast) Color.rgb(236, 236, 236) else Color.WHITE
        val foreground = if (lowContrast) Color.rgb(105, 105, 105) else Color.BLACK
        canvas.drawColor(background)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = foreground
            textSize = 132f
        }
        canvas.drawText("PhotoBook INVOICE 12345", 70f, 260f, paint)
        paint.textSize = 104f
        canvas.drawText("TOTAL 500  PAID", 90f, 470f, paint)
        return bitmap
    }

    private fun jpegRoundTrip(source: Bitmap, quality: Int): Bitmap {
        val bytes = ByteArrayOutputStream().use { output ->
            assertTrue("JPEG compression failed", source.compress(Bitmap.CompressFormat.JPEG, quality, output))
            output.toByteArray()
        }
        return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
    }

    private fun rotate(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun downsampleUpsample(source: Bitmap): Bitmap {
        val small = Bitmap.createScaledBitmap(source, source.width / 4, source.height / 4, true)
        return try {
            Bitmap.createScaledBitmap(small, source.width, source.height, true)
        } finally {
            small.recycle()
        }
    }

    private fun skew(source: Bitmap, amount: Float): Bitmap {
        val output = Bitmap.createBitmap(source.width + 320, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            setSkew(amount, 0f)
            postTranslate(35f, 0f)
        }
        canvas.drawBitmap(source, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun normalize(value: String): String {
        return value.lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private class Fixture {
        val name: String
        val directFactory: (() -> Bitmap)?
        val transform: ((Bitmap) -> Bitmap)?

        constructor(name: String, directFactory: () -> Bitmap) {
            this.name = name
            this.directFactory = directFactory
            this.transform = null
        }

        constructor(name: String, transform: (Bitmap) -> Bitmap) {
            this.name = name
            this.directFactory = null
            this.transform = transform
        }
    }
}
