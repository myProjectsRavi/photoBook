package com.photobook.app.data.source

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
import android.test.mock.MockContentResolver
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaStoreScannerFailureInstrumentedTest {

    @Test
    fun nullProviderResult_isFailureNotSuccessfulEmptyGallery() = runBlocking {
        val scanner = scannerWithProvider { _, _, _, _, _ -> null }

        val failure = runCatching { scanner.scanAll() }.exceptionOrNull()

        assertTrue(failure is MediaStoreScanException)
    }

    @Test
    fun realEmptyCursor_isSuccessfulEmptyGallery() = runBlocking {
        val scanner = scannerWithProvider { _, projection, _, _, _ ->
            MatrixCursor(requireNotNull(projection))
        }

        assertTrue(scanner.scanAll().isEmpty())
        assertTrue(scanner.scanAllIds().isEmpty())
    }

    @Test
    fun providerSecurityFailure_propagatesInsteadOfPublishingEmptyData() = runBlocking {
        val scanner = scannerWithProvider { _, _, _, _, _ ->
            throw SecurityException("grant revoked during query")
        }

        val failure = runCatching { scanner.scanAll() }.exceptionOrNull()

        assertTrue(failure is SecurityException)
    }

    @Test
    fun halfReadFailure_neverReturnsPartialScanAsComplete() = runBlocking {
        val scanner = scannerWithProvider { _, projection, _, _, _ ->
            val columns = requireNotNull(projection)
            object : MatrixCursor(columns) {
                init {
                    addRow(rowFor(columns, id = 11L))
                    addRow(rowFor(columns, id = 12L))
                }

                override fun onMove(oldPosition: Int, newPosition: Int): Boolean {
                    if (newPosition >= 1) throw IllegalStateException("provider disappeared mid-scan")
                    return super.onMove(oldPosition, newPosition)
                }
            }
        }

        val failure = runCatching { scanner.scanAll() }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
    }

    private fun scannerWithProvider(
        query: (
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ) -> Cursor?,
    ): MediaStoreScanner {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val resolver = MockContentResolver(base)
        resolver.addProvider(
            MediaStore.AUTHORITY,
            object : ContentProvider() {
                override fun onCreate(): Boolean = true
                override fun getType(uri: Uri): String? = null
                override fun insert(uri: Uri, values: ContentValues?): Uri? = null
                override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
                override fun update(
                    uri: Uri,
                    values: ContentValues?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                ): Int = 0

                override fun query(
                    uri: Uri,
                    projection: Array<out String>?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                    sortOrder: String?,
                ): Cursor? = query(uri, projection, selection, selectionArgs, sortOrder)
            },
        )
        val context = object : ContextWrapper(base) {
            override fun getContentResolver(): ContentResolver = resolver
        }
        return MediaStoreScanner(context)
    }

    private fun rowFor(columns: Array<out String>, id: Long): Array<Any?> {
        return columns.map { column ->
            when (column) {
                MediaStore.Images.Media._ID -> id
                MediaStore.Images.Media.DISPLAY_NAME -> "IMG_$id.jpg"
                MediaStore.Images.Media.DATE_ADDED -> 1_700_000_000L
                MediaStore.Images.Media.DATE_MODIFIED -> 1_700_000_100L
                MediaStore.Images.Media.SIZE -> 1_024L
                MediaStore.Images.Media.WIDTH -> 100
                MediaStore.Images.Media.HEIGHT -> 100
                MediaStore.Images.Media.MIME_TYPE -> "image/jpeg"
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> "Camera"
                MediaStore.Images.Media.RELATIVE_PATH -> "DCIM/Camera/"
                MediaStore.Images.Media.DATA -> "/storage/emulated/0/DCIM/Camera/IMG_$id.jpg"
                MediaStore.MediaColumns.GENERATION_MODIFIED -> 10L
                else -> null
            }
        }.toTypedArray()
    }
}
