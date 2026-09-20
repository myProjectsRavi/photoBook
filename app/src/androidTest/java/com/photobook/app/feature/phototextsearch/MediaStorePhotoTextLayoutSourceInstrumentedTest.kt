package com.photobook.app.feature.phototextsearch

import android.content.ContentUris
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.ml.BundledOnDeviceIntelligence
import com.photobook.app.ml.LocalOcrEngine
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStorePhotoTextLayoutSourceInstrumentedTest {

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun readableContentUri_withoutMediaStoreIdentityMetadata_isStillSearchable() = runBlocking {
        val dir = File(context.cacheDir, "safe_share").apply { mkdirs() }
        val file = File(dir, "photo-text-source-${System.nanoTime()}.png")
        writeFixture(file)
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file,
            )
            val result = source().load(
                PhotoTextSourceKey(
                    photoId = 987_654_321L,
                    uriString = uri.toString(),
                ),
            )
            assertTrue(
                "A readable content URI must not be rejected only because provider metadata is incomplete: result=$result",
                result is PhotoTextLayoutLoadResult.Success,
            )
        } finally {
            file.delete()
        }
    }

    @Test
    fun mediaStoreItem_usesAuthoritativeIdAndRemainsSearchable() = runBlocking {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "photobook-search-${System.nanoTime()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoBookVerification")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = checkNotNull(
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values),
        ) { "Unable to create MediaStore verification image" }

        try {
            resolver.openOutputStream(uri, "w")!!.use { output ->
                fixtureBitmap().useCompat { bitmap ->
                    check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
            }

            val mediaId = ContentUris.parseId(uri)
            val valid = source().load(
                PhotoTextSourceKey(
                    photoId = mediaId,
                    uriString = uri.toString(),
                ),
            )
            assertTrue(
                "A readable MediaStore image must produce an OCR layout: result=$valid",
                valid is PhotoTextLayoutLoadResult.Success,
            )

            val mismatchedRecordId = source().load(
                PhotoTextSourceKey(
                    photoId = mediaId + 1L,
                    uriString = uri.toString(),
                ),
            )
            assertTrue(
                "Search must follow the URI currently displayed even if a persisted record ID is stale: result=$mismatchedRecordId",
                mismatchedRecordId is PhotoTextLayoutLoadResult.Success,
            )
        } finally {
            runCatching { resolver.delete(uri, null, null) }
        }
    }

    private fun source(): MediaStorePhotoTextLayoutSource {
        return MediaStorePhotoTextLayoutSource(
            context = context,
            localOcrEngine = LocalOcrEngine(),
            onDeviceIntelligence = BundledOnDeviceIntelligence(),
        )
    }

    private fun writeFixture(file: File) {
        FileOutputStream(file).use { output ->
            fixtureBitmap().useCompat { bitmap ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }

    private fun fixtureBitmap(): Bitmap {
        return Bitmap.createBitmap(640, 240, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 64f
            }
            canvas.drawText("Invoice 12345", 32f, 135f, paint)
        }
    }

    private inline fun Bitmap.useCompat(block: (Bitmap) -> Unit) {
        try {
            block(this)
        } finally {
            if (!isRecycled) recycle()
        }
    }
}
