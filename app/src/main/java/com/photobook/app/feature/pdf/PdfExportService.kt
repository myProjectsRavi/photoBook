package com.photobook.app.feature.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.util.LocalDiagnostics
import com.photobook.app.util.PerformanceProfiler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val publicationJournal by lazy {
        context.getSharedPreferences(PDF_PUBLICATION_JOURNAL, Context.MODE_PRIVATE)
    }

    suspend fun exportPhotos(
        photos: List<PhotoRecord>,
        onProgress: (PdfExportProgress) -> Unit = {},
    ): PdfExportResult {
        return exportPhotos(
            photos = photos,
            destination = PdfExportDestination.Downloads,
            onProgress = onProgress,
        )
    }

    suspend fun exportPhotosToDocumentUri(
        photos: List<PhotoRecord>,
        destinationUri: Uri,
        onProgress: (PdfExportProgress) -> Unit = {},
    ): PdfExportResult {
        return exportPhotos(
            photos = photos,
            destination = PdfExportDestination.DocumentUri(destinationUri),
            onProgress = onProgress,
        )
    }

    suspend fun exportPhotosForSharing(
        photos: List<PhotoRecord>,
        onProgress: (PdfExportProgress) -> Unit = {},
    ): PdfExportResult {
        return exportPhotos(
            photos = photos,
            destination = PdfExportDestination.ShareCache,
            onProgress = onProgress,
        )
    }

    private suspend fun exportPhotos(
        photos: List<PhotoRecord>,
        destination: PdfExportDestination,
        onProgress: (PdfExportProgress) -> Unit,
    ): PdfExportResult {
        if (photos.isEmpty()) return PdfExportResult.Error()

        val constraints = PdfExportConstraints.forLiteMode(
            isLite = PerformanceProfiler.from(context).isLite,
        )
        if (photos.size > constraints.maxPageCount) {
            return PdfExportResult.TooManyPages(
                requested = photos.size,
                maxAllowed = constraints.maxPageCount,
            )
        }

        return exportMutex.withLock {
            withContext(Dispatchers.IO) {
            if (
                destination is PdfExportDestination.Downloads &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                !reconcilePendingDownload()
            ) {
                return@withContext PdfExportResult.Error(
                    IllegalStateException("Unable to reconcile previous PDF publication"),
                )
            }

            val document = PdfDocument()
            var writtenPages = 0
            var skippedPages = 0
            var processedItems = 0
            var output: PdfOutput? = null

            try {
                onProgress(
                    PdfExportProgress(
                        totalItems = photos.size,
                        processedItems = 0,
                        writtenPages = 0,
                        skippedItems = 0,
                    ),
                )
                for (photo in photos) {
                    currentCoroutineContext().ensureActive()
                    val bitmap = decodeSampledBitmap(
                        uri = Uri.parse(photo.uriString),
                        maxDimensionPx = constraints.maxImageDimensionPx,
                    )
                    if (bitmap == null) {
                        skippedPages += 1
                        processedItems += 1
                        LocalDiagnostics.record(
                            context = context,
                            area = "pdf-export",
                            message = "Skipped unreadable image while creating PDF: ${photo.uriString}",
                        )
                        onProgress(
                            PdfExportProgress(
                                totalItems = photos.size,
                                processedItems = processedItems,
                                writtenPages = writtenPages,
                                skippedItems = skippedPages,
                            ),
                        )
                        continue
                    }

                    try {
                        currentCoroutineContext().ensureActive()
                        val pageSpec = PdfPageLayout.pageSpecFor(bitmap.width, bitmap.height)
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            pageSpec.width,
                            pageSpec.height,
                            writtenPages + 1,
                        ).create()
                        val page = document.startPage(pageInfo)
                        drawPage(page.canvas, bitmap, pageSpec)
                        document.finishPage(page)
                        writtenPages += 1
                    } finally {
                        bitmap.recycleSafely()
                    }
                    processedItems += 1
                    currentCoroutineContext().ensureActive()
                    onProgress(
                        PdfExportProgress(
                            totalItems = photos.size,
                            processedItems = processedItems,
                            writtenPages = writtenPages,
                            skippedItems = skippedPages,
                        ),
                    )
                }

                if (writtenPages == 0) {
                    return@withContext PdfExportResult.Error()
                }

                currentCoroutineContext().ensureActive()
                val fileName = buildFileName(photos.singleOrNull()?.fileName)
                output = when (destination) {
                    PdfExportDestination.Downloads -> writeDocumentToDownloads(document, fileName)
                    PdfExportDestination.ShareCache -> writeDocumentToShareCache(document, fileName)
                    is PdfExportDestination.DocumentUri -> writeDocumentToUri(
                        document = document,
                        destinationUri = destination.uri,
                    )
                }
                currentCoroutineContext().ensureActive()

                if (skippedPages > 0) {
                    PdfExportResult.PartialSuccess(
                        uri = output.uri,
                        fileName = fileName,
                        pageCount = writtenPages,
                        skippedCount = skippedPages,
                    )
                } else {
                    PdfExportResult.Success(
                        uri = output.uri,
                        fileName = fileName,
                        pageCount = writtenPages,
                    )
                }
            } catch (t: Throwable) {
                output?.let(::deleteOutput)
                if (t is CancellationException) throw t
                LocalDiagnostics.record(
                    context = context,
                    area = "pdf-export",
                    message = "PDF export failed",
                    throwable = t,
                )
                PdfExportResult.Error(t)
            } finally {
                document.close()
            }
            }
        }
    }

    private fun drawPage(canvas: Canvas, bitmap: Bitmap, pageSpec: PdfPageSpec) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(Color.WHITE)

        val placement = PdfPageLayout.fitCenter(
            pageWidth = pageSpec.width,
            pageHeight = pageSpec.height,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
        )
        val dest = RectF(
            placement.left,
            placement.top,
            placement.right,
            placement.bottom,
        )

        canvas.drawBitmap(bitmap, null, dest, paint)
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        decodeWithBitmapFactory(uri, maxDimensionPx)?.let { decoded ->
            val normalized = applyExifOrientation(decoded, uri)
            if (normalized !== decoded) {
                decoded.recycleSafely()
            }
            return normalized
        }

        return decodeWithImageDecoder(uri, maxDimensionPx)
    }

    private fun decodeWithBitmapFactory(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        val readBounds = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
                true
            }
        }.getOrDefault(false)
        if (readBounds != true) return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx || bounds.outHeight / sample > maxDimensionPx) {
            sample *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()
    }

    private fun decodeWithImageDecoder(uri: Uri, maxDimensionPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null

        return runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val width = info.size.width
                val height = info.size.height
                val largest = maxOf(width, height)
                if (width > 0 && height > 0 && largest > maxDimensionPx) {
                    val scale = maxDimensionPx.toFloat() / largest.toFloat()
                    decoder.setTargetSize(
                        (width * scale).toInt().coerceAtLeast(1),
                        (height * scale).toInt().coerceAtLeast(1),
                    )
                }
            }
        }.onFailure { error ->
            LocalDiagnostics.record(
                context = context,
                area = "pdf-export",
                message = "ImageDecoder fallback failed for PDF image",
                throwable = error,
            )
        }.getOrNull()
    }

    private fun applyExifOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = readExifOrientation(uri)
        val matrix = Matrix()
        val changed = when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> {
                matrix.setScale(-1f, 1f)
                true
            }
            ExifInterface.ORIENTATION_ROTATE_180 -> {
                matrix.setRotate(180f)
                true
            }
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setScale(1f, -1f)
                true
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
                true
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> {
                matrix.setRotate(90f)
                true
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(270f)
                matrix.postScale(-1f, 1f)
                true
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> {
                matrix.setRotate(270f)
                true
            }
            else -> false
        }

        if (!changed) return bitmap
        return runCatching {
            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true,
            )
        }.getOrDefault(bitmap)
    }

    private fun readExifOrientation(uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    }

    private suspend fun writeDocumentToUri(
        document: PdfDocument,
        destinationUri: Uri,
    ): PdfOutput {
        currentCoroutineContext().ensureActive()
        context.contentResolver.openOutputStream(destinationUri, "wt")?.use { stream ->
            document.writeTo(stream)
        } ?: error("Unable to open selected PDF destination")
        currentCoroutineContext().ensureActive()
        check(verifyPdf(destinationUri)) { "Selected PDF destination failed read-back verification" }
        return PdfOutput(uri = destinationUri)
    }

    private suspend fun writeDocumentToDownloads(
        document: PdfDocument,
        fileName: String,
    ): PdfOutput {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return writeDocumentToAppDocuments(document, fileName)
        }

        var outputUri: Uri? = null
        try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/PhotoBook",
                )
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            outputUri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values,
            ) ?: error("Unable to create PDF output")

            if (!recordPendingDownload(outputUri)) {
                runCatching { context.contentResolver.delete(outputUri, null, null) }
                error("Unable to journal pending PDF output")
            }

            currentCoroutineContext().ensureActive()
            context.contentResolver.openOutputStream(outputUri)?.use { stream ->
                document.writeTo(stream)
            } ?: error("Unable to open PDF output")
            currentCoroutineContext().ensureActive()

            check(verifyPdf(outputUri)) { "Written PDF output could not be verified" }
            val updated = context.contentResolver.update(
                outputUri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            check(updated > 0) { "Unable to publish PDF output" }
            clearPendingDownload()
            return PdfOutput(uri = outputUri)
        } catch (t: Throwable) {
            outputUri?.let { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            clearPendingDownload()
            if (t is CancellationException) throw t
            throw t
        }
    }

    private suspend fun writeDocumentToAppDocuments(
        document: PdfDocument,
        fileName: String,
    ): PdfOutput {
        val outputDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir,
            "PhotoBook",
        ).apply { check(exists() || mkdirs()) { "Unable to create PDF documents directory" } }
        return writeDocumentAtomically(document, outputDir, fileName)
    }

    private suspend fun writeDocumentToShareCache(
        document: PdfDocument,
        fileName: String,
    ): PdfOutput {
        val outputDir = File(context.cacheDir, PdfShareCachePolicy.DIRECTORY_NAME).apply {
            check(exists() || mkdirs()) { "Unable to create PDF share directory" }
        }
        cleanupStaleShareCache(outputDir)
        return writeDocumentAtomically(document, outputDir, fileName)
    }

    private suspend fun writeDocumentAtomically(
        document: PdfDocument,
        outputDir: File,
        fileName: String,
    ): PdfOutput {
        val outputFile = File(outputDir, fileName)
        val partialFile = File(outputDir, "$fileName.partial")
        runCatching { partialFile.delete() }
        try {
            currentCoroutineContext().ensureActive()
            FileOutputStream(partialFile, false).use { stream ->
                document.writeTo(stream)
            }
            currentCoroutineContext().ensureActive()
            check(verifyPdf(partialFile)) { "Temporary PDF output could not be verified" }
            if (outputFile.exists()) {
                check(outputFile.delete()) { "Unable to replace existing PDF output" }
            }
            check(partialFile.renameTo(outputFile)) { "Unable to publish PDF output atomically" }
            check(verifyPdf(outputFile)) { "Published PDF output could not be verified" }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile,
            )
            return PdfOutput(uri = uri, file = outputFile)
        } catch (t: Throwable) {
            runCatching { partialFile.delete() }
            runCatching { outputFile.delete() }
            if (t is CancellationException) throw t
            throw t
        }
    }

    private fun verifyPdf(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header = ByteArray(PDF_HEADER.size)
                val read = input.read(header)
                read == PDF_HEADER.size && header.contentEquals(PDF_HEADER)
            } ?: false
        }.getOrDefault(false)
    }

    private fun verifyPdf(file: File): Boolean {
        if (!file.isFile || file.length() <= PDF_HEADER.size) return false
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(PDF_HEADER.size)
                val read = input.read(header)
                read == PDF_HEADER.size && header.contentEquals(PDF_HEADER)
            }
        }.getOrDefault(false)
    }

    private fun recordPendingDownload(uri: Uri): Boolean {
        return publicationJournal.edit()
            .putString(KEY_PENDING_PDF_URI, uri.toString())
            .commit()
    }

    private fun clearPendingDownload(): Boolean {
        return publicationJournal.edit()
            .remove(KEY_PENDING_PDF_URI)
            .commit()
    }

    private fun reconcilePendingDownload(): Boolean {
        val raw = publicationJournal.getString(KEY_PENDING_PDF_URI, null) ?: return true
        val uri = runCatching { Uri.parse(raw) }.getOrNull() ?: return clearPendingDownload()
        val pending = queryPendingState(uri)
        return when (pending) {
            null -> false
            PENDING_ROW_MISSING, 0 -> clearPendingDownload()
            1 -> {
                val deleted = runCatching { context.contentResolver.delete(uri, null, null) }
                    .getOrDefault(0) > 0
                deleted && clearPendingDownload()
            }
            else -> false
        }
    }

    private fun queryPendingState(uri: Uri): Int? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.IS_PENDING),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) PENDING_ROW_MISSING else cursor.getInt(0)
            } ?: PENDING_ROW_MISSING
        }.getOrNull()
    }

    private fun deleteOutput(output: PdfOutput) {
        output.file?.let { file -> runCatching { file.delete() } }
        if (output.file == null && output.uri.scheme == "content") {
            runCatching { context.contentResolver.delete(output.uri, null, null) }
        }
    }

    private fun cleanupStaleShareCache(dir: File) {
        val nowMs = System.currentTimeMillis()
        dir.listFiles().orEmpty().forEach { file ->
            if (file.isFile && PdfShareCachePolicy.isStale(file.lastModified(), nowMs)) {
                runCatching { file.delete() }
            }
        }
    }

    private fun buildFileName(sourceFileName: String?): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date()) +
            "_" + UUID.randomUUID().toString().take(8)
        return PdfFileNames.build(stamp = stamp, sourceFileName = sourceFileName)
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private data class PdfOutput(
        val uri: Uri,
        val file: File? = null,
    )

    private sealed interface PdfExportDestination {
        data object Downloads : PdfExportDestination
        data object ShareCache : PdfExportDestination
        data class DocumentUri(val uri: Uri) : PdfExportDestination
    }

    companion object {
        private val exportMutex = Mutex()

        private const val MIME_TYPE = "application/pdf"
        private val PDF_HEADER = "%PDF-".encodeToByteArray()
        private const val PDF_PUBLICATION_JOURNAL = "pdf_publication_journal_v1"
        private const val KEY_PENDING_PDF_URI = "pending_pdf_uri"
        private const val PENDING_ROW_MISSING = -1
    }
}
