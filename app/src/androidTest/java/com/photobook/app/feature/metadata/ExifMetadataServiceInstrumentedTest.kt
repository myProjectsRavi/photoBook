package com.photobook.app.feature.metadata

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.PhotoRecord
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExifMetadataServiceInstrumentedTest {
    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val createdSourceFiles = mutableListOf<File>()
    private val createdMediaUris = mutableListOf<Uri>()

    @After
    fun cleanup() {
        createdMediaUris.forEach { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        createdMediaUris.clear()
        createdSourceFiles.forEach { file -> runCatching { file.delete() } }
        createdSourceFiles.clear()
        runCatching { File(context.cacheDir, "safe_share").deleteRecursively() }
    }

    @Test
    fun createCleanCopy_stripsSensitiveMetadataAndPublishesPendingRow() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val source = createSensitiveJpeg()
        val service = ExifMetadataService(context)

        val result = service.createCleanCopy(photoFor(source))

        assertThat(result).isInstanceOf(MetadataCleanResult.Success::class.java)
        val success = result as MetadataCleanResult.Success
        createdMediaUris += success.uri
        assertSensitiveMetadataRemoved(success.uri)

        val pending = context.contentResolver.query(
            success.uri,
            arrayOf(MediaStore.MediaColumns.IS_PENDING),
            null,
            null,
            null,
        )?.use { cursor ->
            check(cursor.moveToFirst())
            cursor.getInt(0)
        }
        assertThat(pending).isEqualTo(0)
        assertThat(success.fileName).contains("_clean_")
        assertThat(success.fileName).endsWith(".jpg")
    }

    @Test
    fun safeShare_stripsSensitiveMetadataBeforeReturningUri() = runBlocking {
        val source = createSensitiveJpeg()
        val safeShareDir = File(context.cacheDir, "safe_share")
        safeShareDir.deleteRecursively()
        val service = ExifMetadataService(context)

        val result = service.createSafeShareCopies(
            photos = listOf(photoFor(source)),
            options = SafeShareOptions(stripMetadata = true, blurFaces = false),
        )

        assertThat(result).isInstanceOf(SafeShareResult.Success::class.java)
        val item = (result as SafeShareResult.Success).items.single()
        assertSensitiveMetadataRemoved(item.uri)
        assertThat(item.mimeType).isEqualTo("image/jpeg")
    }

    @Test
    fun safeShare_laterAssetFailureDeletesEarlierPreparedOutputs() = runBlocking {
        val source = createSensitiveJpeg()
        val safeShareDir = File(context.cacheDir, "safe_share")
        safeShareDir.deleteRecursively()
        val missing = File(context.cacheDir, "missing-${UUID.randomUUID()}.jpg")
        val service = ExifMetadataService(context)

        val result = service.createSafeShareCopies(
            photos = listOf(
                photoFor(source, id = 1L),
                photoFor(missing, id = 2L),
            ),
            options = SafeShareOptions(stripMetadata = true, blurFaces = false),
        )

        assertThat(result).isInstanceOf(SafeShareResult.Error::class.java)
        assertThat(safeShareDir.listFiles().orEmpty().filter { it.isFile }).isEmpty()
    }

    private fun createSensitiveJpeg(): File {
        val file = File(context.cacheDir, "phase1-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(90, 140, 210))
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()

        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MODEL, "Phase1SensitiveCamera")
            setAttribute(ExifInterface.TAG_MAKE, "Phase1SensitiveMaker")
            setAttribute(ExifInterface.TAG_USER_COMMENT, "private-comment")
            setAttribute(ExifInterface.TAG_XMP, "<x:xmpmeta>private-xmp</x:xmpmeta>")
            setLatLong(17.3850, 78.4867)
            saveAttributes()
        }
        createdSourceFiles += file
        return file
    }

    private fun assertSensitiveMetadataRemoved(uri: Uri) {
        val exif = context.contentResolver.openInputStream(uri)?.use { input ->
            ExifInterface(input)
        }
        checkNotNull(exif)
        assertThat(exif.getAttribute(ExifInterface.TAG_MODEL)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_MAKE)).isNull()
        assertThat(exif.getAttribute(ExifInterface.TAG_USER_COMMENT)).isNull()
        assertThat(exif.getAttributeBytes(ExifInterface.TAG_XMP)).isNull()
        assertThat(exif.latLong).isNull()
    }

    private fun photoFor(file: File, id: Long = 1L): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = Uri.fromFile(file).toString(),
            filePath = file.absolutePath,
            fileName = file.name,
            dateAdded = System.currentTimeMillis() - 86_400_000L,
            year = 2026,
            month = 7,
            dayOfMonth = 1,
            dayOfWeek = 3,
            hourOfDay = 10,
            latitude = 17.3850,
            longitude = 78.4867,
            city = null,
            state = null,
            country = null,
            fileSize = file.length(),
            width = 96,
            height = 96,
            mimeType = "image/jpeg",
            folderName = "Phase1",
            folderPath = context.cacheDir.absolutePath,
            cameraModel = "Phase1SensitiveCamera",
            isFrontCamera = false,
            isHdr = false,
            isFavorite = false,
        )
    }
}
