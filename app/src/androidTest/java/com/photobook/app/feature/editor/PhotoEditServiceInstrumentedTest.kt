package com.photobook.app.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.model.PhotoRecord
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoEditServiceInstrumentedTest {

    @Test
    fun asymmetricCrop_tracksSamePixelsAcrossAllQuarterTurnsAndExport() {
        runBlocking {
        val fixture = createAsymmetricFixture("editor_rotation")
        val service = PhotoEditService(fixture.context)
        val sourceCorner = NormalizedCropRegion(0.04f, 0.04f, 0.46f, 0.46f)

        try {
            for (turns in 0..3) {
                val state = PhotoEditState(
                    rotationQuarterTurns = turns,
                    cropPreset = CropPreset.Original,
                    customCrop = sourceCorner.rotatedClockwise(turns),
                )
                val preview = service.renderPreviewBitmap(fixture.photo, state, maxDimension = 256)
                assertTrue("preview missing for turn $turns", preview != null)
                preview!!
                assertRedDominant(preview.getPixel(preview.width / 2, preview.height / 2))
                preview.recycle()
            }

            val export = service.renderEditedCopy(
                fixture.photo,
                PhotoEditState(
                    rotationQuarterTurns = 1,
                    cropPreset = CropPreset.Original,
                    customCrop = sourceCorner.rotatedClockwise(1),
                ),
            )
            assertTrue(export is PhotoEditResult.Success)
            export as PhotoEditResult.Success
            val rendered = fixture.context.contentResolver.openInputStream(export.uri)?.use {
                BitmapFactory.decodeStream(it)
            }
            assertTrue(rendered != null)
            rendered!!
            assertRedDominant(rendered.getPixel(rendered.width / 2, rendered.height / 2))
            rendered.recycle()
            File(fixture.context.cacheDir, "safe_share/${export.fileName}").delete()
        } finally {
            fixture.close()
        }
        }
    }

    @Test
    fun exifQuarterTurn_swapsCropGeometryAndTargetsNormalizedPixels() {
        runBlocking {
        val fixture = createAsymmetricFixture(
            prefix = "editor_exif",
            exifOrientation = ExifInterface.ORIENTATION_ROTATE_90,
        )
        val service = PhotoEditService(fixture.context)
        try {
            assertEquals(40 to 80, service.normalizedSourceDimensions(fixture.photo))

            val exifNormalizedTopRight =
                NormalizedCropRegion(0.04f, 0.04f, 0.46f, 0.46f).rotatedClockwise(1)
            val preview = service.renderPreviewBitmap(
                fixture.photo,
                PhotoEditState(
                    rotationQuarterTurns = 0,
                    cropPreset = CropPreset.Original,
                    customCrop = exifNormalizedTopRight,
                ),
                maxDimension = 256,
            )
            assertTrue(preview != null)
            preview!!
            assertRedDominant(preview.getPixel(preview.width / 2, preview.height / 2))
            preview.recycle()
        } finally {
            fixture.close()
        }
        }
    }

    private fun assertRedDominant(pixel: Int) {
        assertTrue("red channel too low: ${Color.red(pixel)}", Color.red(pixel) > 150)
        assertTrue("green channel too high: ${Color.green(pixel)}", Color.green(pixel) < 120)
        assertTrue("blue channel too high: ${Color.blue(pixel)}", Color.blue(pixel) < 120)
    }

    private fun createAsymmetricFixture(
        prefix: String,
        exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL,
    ): Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = File(context.cacheDir, "safe_share").apply { mkdirs() }
        val source = File(root, "${prefix}_${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(80, 40, Bitmap.Config.ARGB_8888)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = when {
                    x < 40 && y < 20 -> Color.RED
                    x >= 40 && y < 20 -> Color.GREEN
                    x < 40 -> Color.BLUE
                    else -> Color.YELLOW
                }
                bitmap.setPixel(x, y, color)
            }
        }
        FileOutputStream(source).use { output ->
            assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
        }
        bitmap.recycle()
        if (exifOrientation != ExifInterface.ORIENTATION_NORMAL) {
            ExifInterface(source.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                saveAttributes()
            }
        }

        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            source,
        )
        return Fixture(
            context = context,
            source = source,
            photo = PhotoRecord(
                id = System.nanoTime(),
                uriString = uri.toString(),
                filePath = source.absolutePath,
                fileName = source.name,
                dateAdded = 1_786_000_000_000L,
                year = 2026,
                month = 10,
                dayOfMonth = 7,
                dayOfWeek = 3,
                hourOfDay = 12,
                latitude = null,
                longitude = null,
                city = null,
                state = null,
                country = null,
                fileSize = source.length(),
                width = 80,
                height = 40,
                mimeType = "image/jpeg",
                folderName = "Test",
                folderPath = root.absolutePath,
                cameraModel = null,
                isFrontCamera = false,
                isHdr = false,
            ),
        )
    }

    private data class Fixture(
        val context: android.content.Context,
        val source: File,
        val photo: PhotoRecord,
    ) {
        fun close() {
            source.delete()
        }
    }
}
