package com.photobook.app.feature.pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.model.PhotoRecord
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfExportServiceInstrumentedTest {

    @Test
    fun shareExport_publishesVerifiedPdfAndReportsProgress() = runBlocking {
        val fixture = createFixture("pdf_success")
        val service = PdfExportService(fixture.context)
        val progress = mutableListOf<PdfExportProgress>()
        var result: PdfExportResult? = null
        try {
            result = service.exportPhotosForSharing(listOf(fixture.photo)) { progress += it }
            assertTrue("Expected Success but got $result", result is PdfExportResult.Success)
            result as PdfExportResult.Success
            assertEquals(1, result.pageCount)
            assertPdfHeader(result.uri)
            assertTrue(progress.isNotEmpty())
            assertEquals(PdfExportProgress(1, 0, 0, 0), progress.first())
            assertEquals(PdfExportProgress(1, 1, 1, 0), progress.last())
        } finally {
            deletePublished(result)
            fixture.close()
        }
    }

    @Test
    fun shareExport_unreadableInputReturnsTruthfulPartialSuccess() = runBlocking {
        val fixture = createFixture("pdf_partial")
        val missing = fixture.photo.copy(
            id = fixture.photo.id + 1,
            uriString = Uri.fromFile(File(fixture.context.cacheDir, "missing_" + System.nanoTime() + ".jpg")).toString(),
            fileName = "missing.jpg",
        )
        val service = PdfExportService(fixture.context)
        val progress = mutableListOf<PdfExportProgress>()
        var result: PdfExportResult? = null
        try {
            result = service.exportPhotosForSharing(listOf(fixture.photo, missing)) { progress += it }
            assertTrue("Expected PartialSuccess but got $result", result is PdfExportResult.PartialSuccess)
            result as PdfExportResult.PartialSuccess
            assertEquals(1, result.pageCount)
            assertEquals(1, result.skippedCount)
            assertPdfHeader(result.uri)
            assertEquals(PdfExportProgress(2, 2, 1, 1), progress.last())
        } finally {
            deletePublished(result)
            fixture.close()
        }
    }

    @Test
    fun shareExport_cancellationDoesNotPublishOrLeavePartialOutput() = runBlocking {
        val fixture = createFixture("pdf_cancel")
        val service = PdfExportService(fixture.context)
        val before = partialFiles(fixture.context.cacheDir)
        var cancelled = false
        try {
            service.exportPhotosForSharing(listOf(fixture.photo)) { progress ->
                if (progress.processedItems == 1) {
                    throw CancellationException("test cancellation after page preparation")
                }
            }
        } catch (_: CancellationException) {
            cancelled = true
        } finally {
            fixture.close()
        }

        assertTrue("Cancellation must propagate", cancelled)
        assertEquals(before, partialFiles(fixture.context.cacheDir))
    }

    private fun assertPdfHeader(uri: Uri) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val header = ByteArray(5)
        val read = context.contentResolver.openInputStream(uri)?.use { it.read(header) } ?: -1
        assertEquals(5, read)
        assertEquals("%PDF-", header.decodeToString())
    }

    private fun deletePublished(result: PdfExportResult?) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = when (result) {
            is PdfExportResult.Success -> result.uri
            is PdfExportResult.PartialSuccess -> result.uri
            else -> null
        }
        if (uri != null) {
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
    }

    private fun partialFiles(root: File): Set<String> =
        root.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".partial") }
            .map { it.absolutePath }
            .toSet()

    private fun createFixture(prefix: String): Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val sourceDir = File(context.cacheDir, "safe_share").apply { mkdirs() }
        val source = File(sourceDir, prefix + "_" + System.nanoTime() + ".jpg")
        val bitmap = Bitmap.createBitmap(32, 20, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(0xFF336699.toInt())
            FileOutputStream(source).use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output))
                output.flush()
            }
        } finally {
            bitmap.recycle()
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
                dateAdded = 123456789L,
                year = 2026,
                month = 10,
                dayOfMonth = 4,
                dayOfWeek = 1,
                hourOfDay = 12,
                latitude = null,
                longitude = null,
                city = null,
                state = null,
                country = null,
                fileSize = source.length(),
                width = 32,
                height = 20,
                mimeType = "image/jpeg",
                folderName = "Test",
                folderPath = source.parent.orEmpty(),
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
            assertFalse("Fixture source should remain a regular file until cleanup", !source.exists())
            source.delete()
        }
    }
}
