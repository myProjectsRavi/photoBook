package com.photobook.app.feature.metadata

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ml.CompactLocalIntelligence
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        assertTrue(result is MetadataCleanResult.Success)
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
        assertEquals(0, pending)
        assertTrue(success.fileName.contains("_clean_"))
        assertTrue(success.fileName.endsWith(".jpg"))
    }

    @Test
    fun createCleanCopy_stripsExtendedGpsOnExifFastPath() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val source = createSensitiveJpeg(includeXmp = false)
        val sourceExif = ExifInterface(source.absolutePath)
        assertTrue(!sourceExif.getAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE).isNullOrBlank())
        assertTrue(!sourceExif.getAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE).isNullOrBlank())
        assertTrue(!sourceExif.getAttribute(ExifInterface.TAG_GPS_MAP_DATUM).isNullOrBlank())
        assertTrue(!sourceExif.getAttribute(ExifInterface.TAG_GPS_SPEED).isNullOrBlank())
        val service = ExifMetadataService(context)

        val result = service.createCleanCopy(photoFor(source))

        assertTrue(result is MetadataCleanResult.Success)
        val success = result as MetadataCleanResult.Success
        createdMediaUris += success.uri
        assertSensitiveMetadataRemoved(success.uri)
    }

    @Test
    fun createCleanCopy_forcedRewritePhysicallyPreservesExifRotation() = runBlocking {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        val source = createSensitiveJpeg(width = 48, height = 96, orientation = ExifInterface.ORIENTATION_ROTATE_90)
        val service = ExifMetadataService(context)

        val result = service.createCleanCopy(photoFor(source, width = 48, height = 96))

        assertTrue(result is MetadataCleanResult.Success)
        val success = result as MetadataCleanResult.Success
        createdMediaUris += success.uri
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(success.uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        }
        assertEquals(96, bounds.outWidth)
        assertEquals(48, bounds.outHeight)
        assertSensitiveMetadataRemoved(success.uri)
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

        assertTrue(result is SafeShareResult.Success)
        val item = (result as SafeShareResult.Success).items.single()
        assertSensitiveMetadataRemoved(item.uri)
        assertEquals("image/jpeg", item.mimeType)
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

        assertTrue(result is SafeShareResult.Error)
        assertTrue(safeShareDir.listFiles().orEmpty().none { it.isFile })
    }

    @Test
    fun strictFaceDetection_acceptsOddWidthBitmapWithoutDimensionMismatch() {
        val bitmap = Bitmap.createBitmap(101, 101, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.DKGRAY)
        try {
            val faces = CompactLocalIntelligence.detectFacesStrict(bitmap)
            assertTrue(faces.size <= 8)
        } finally {
            bitmap.recycle()
        }
    }

    private fun createSensitiveJpeg(
        width: Int = 96,
        height: Int = 96,
        orientation: Int = ExifInterface.ORIENTATION_NORMAL,
        includeXmp: Boolean = true,
    ): File {
        val file = File(context.cacheDir, "phase1-${UUID.randomUUID()}.jpg")
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(90, 140, 210))
        FileOutputStream(file).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
        }
        bitmap.recycle()

        ExifInterface(file.absolutePath).apply {
            setAttribute(ExifInterface.TAG_MODEL, "Phase1SensitiveCamera")
            setAttribute(ExifInterface.TAG_MAKE, "Phase1SensitiveMaker")
            setAttribute(ExifInterface.TAG_USER_COMMENT, "private-comment")
            if (includeXmp) {
                setAttribute(ExifInterface.TAG_XMP, "<x:xmpmeta>private-xmp</x:xmpmeta>")
            }
            setAttribute(ExifInterface.TAG_ORIENTATION, orientation.toString())
            setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, "123")
            setAttribute(ExifInterface.TAG_GPS_MAP_DATUM, "WGS-84")
            setAttribute(ExifInterface.TAG_GPS_SPEED_REF, "K")
            setAttribute(ExifInterface.TAG_GPS_SPEED, "42/1")
            setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF, "N")
            setAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE, "12/1,34/1,5600/100")
            setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF, "E")
            setAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE, "77/1,35/1,1200/100")
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
        assertNull(exif.getAttribute(ExifInterface.TAG_MODEL))
        assertNull(exif.getAttribute(ExifInterface.TAG_MAKE))
        assertNull(exif.getAttribute(ExifInterface.TAG_USER_COMMENT))
        assertNull(exif.getAttributeBytes(ExifInterface.TAG_XMP))
        assertNull(exif.getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_MAP_DATUM))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_SPEED))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_SPEED_REF))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_DEST_LATITUDE_REF))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE))
        assertNull(exif.getAttribute(ExifInterface.TAG_GPS_DEST_LONGITUDE_REF))
        assertNull(exif.latLong)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        )
        assertTrue(
            orientation == ExifInterface.ORIENTATION_NORMAL ||
                orientation == ExifInterface.ORIENTATION_UNDEFINED,
        )
    }

    private fun photoFor(
        file: File,
        id: Long = 1L,
        width: Int = 96,
        height: Int = 96,
    ): PhotoRecord {
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
            width = width,
            height = height,
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
