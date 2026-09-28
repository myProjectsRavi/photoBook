package com.photobook.app.feature.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.photobook.app.data.model.PhotoRecord
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EditorOutputPublisherInstrumentedTest {

    @Test
    fun productionPath_publishesCapturedEditWithoutChangingOriginal() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceDir = File(context.cacheDir, "safe_share").apply { mkdirs() }
        val sourceFile = File(sourceDir, "s03_source_" + System.nanoTime() + ".jpg")
        createSourceJpeg(sourceFile, width = 24, height = 12)
        val beforeHash = sha256(sourceFile)
        val sourceUri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            sourceFile,
        )

        val photo = photoRecord(sourceUri, sourceFile)
        val state = PhotoEditState(
            rotationQuarterTurns = 1,
            exposure = 0.15f,
            contrast = 1.1f,
        )
        val editService = PhotoEditService(context)
        val publisher = EditorOutputPublisher(context)

        var publishedUri: Uri? = null
        try {
            val rendered = editService.renderEditedCopy(photo, state)
            assertTrue(rendered is PhotoEditResult.Success)
            rendered as PhotoEditResult.Success

            val result = publisher.publish(
                EditorPublicationRequest(
                    sourcePhotoId = photo.id,
                    sourceRevision = listOf(
                        photo.id,
                        photo.dateAdded,
                        photo.fileSize,
                        photo.width,
                        photo.height,
                    ).joinToString(":"),
                    editState = state,
                    renderedUriString = rendered.uri.toString(),
                    mimeType = rendered.mimeType,
                ),
            )
            assertTrue(result is EditorPublicationResult.Success)
            result as EditorPublicationResult.Success
            publishedUri = Uri.parse(result.uriString)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(publishedUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }
            assertEquals(12, bounds.outWidth)
            assertEquals(24, bounds.outHeight)
            assertEquals(beforeHash, sha256(sourceFile))
        } finally {
            publishedUri?.let { context.contentResolver.delete(it, null, null) }
            sourceFile.delete()
        }
    }

    private fun createSourceJpeg(file: File, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xFF336699.toInt())
            FileOutputStream(file).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.flush()
                output.fd.sync()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun photoRecord(uri: Uri, file: File): PhotoRecord {
        return PhotoRecord(
            id = 9003L,
            uriString = uri.toString(),
            filePath = file.absolutePath,
            fileName = file.name,
            dateAdded = 123456789L,
            year = 2026,
            month = 9,
            dayOfMonth = 28,
            dayOfWeek = 2,
            hourOfDay = 12,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = file.length(),
            width = 24,
            height = 12,
            mimeType = "image/jpeg",
            folderName = "Test",
            folderPath = file.parent.orEmpty(),
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
        )
    }
}
