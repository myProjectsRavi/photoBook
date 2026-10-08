package com.photobook.app.data.source

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.MediaStore
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
    fun fullScanBatches_neverAccumulateTheWholeProviderResult() = runBlocking {
        val scanner = scannerWithProvider { _, projection, _, _, _ ->
            val columns = requireNotNull(projection)
            MatrixCursor(columns).apply {
                repeat(450) { index -> addRow(rowFor(columns, id = index + 1L)) }
            }
        }
        val batchSizes = mutableListOf<Int>()
        val ids = mutableSetOf<Long>()

        scanner.scanAllBatches(batchSize = 128) { batch ->
            batchSizes += batch.size
            ids += batch.map { it.id }
        }

        assertEquals(listOf(128, 128, 128, 66), batchSizes)
        assertEquals(450, ids.size)
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        return MediaStoreScanner(context).apply {
            queryOverride = { uri, projection, selection, selectionArgs, sortOrder ->
                query(uri, projection, selection, selectionArgs, sortOrder)
            }
        }
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
