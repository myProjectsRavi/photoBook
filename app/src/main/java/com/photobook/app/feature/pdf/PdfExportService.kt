package com.photobook.app.feature.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
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
import com.photobook.app.util.PerformanceProfiler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PdfExportService @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun exportPhotos(photos: List<PhotoRecord>): PdfExportResult {
        return exportPhotos(
            photos = photos,
            destination = PdfExportDestination.Downloads,
        )
    }

    suspend fun exportPhotosForSharing(photos: List<PhotoRecord>): PdfExportResult {
        return exportPhotos(
            photos = photos,
            destination = PdfExportDestination.ShareCache,
        )
    }

    private suspend fun exportPhotos(
        photos: List<PhotoRecord>,
        destination: PdfExportDestination,
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

        return withContext(Dispatchers.IO) {
            val document = PdfDocument()
            var writtenPages = 0
            var output: PdfOutput? = null

            try {
                photos.forEachIndexed { index, photo ->
                    val bitmap = decodeSampledBitmap(
                        uri = Uri.parse(photo.uriString),
                        maxDimensionPx = constraints.maxImageDimensionPx,
                    ) ?: return@forEachIndexed

                    try {
                        val pageSpec = PdfPageLayout.pageSpecFor(bitmap.width, bitmap.height)
                        val pageInfo = PdfDocument.PageInfo.Builder(
                            pageSpec.width,
                            pageSpec.height,
                            index + 1,
                        ).create()
                        val page = document.startPage(pageInfo)
                        drawPage(page.canvas, bitmap, pageSpec)
                        document.finishPage(page)
                        writtenPages += 1
                    } finally {
                        bitmap.recycleSafely()
                    }
                }

                if (writtenPages == 0) {
                    return@withContext PdfExportResult.Error()
                }

                val fileName = buildFileName(photos.singleOrNull()?.fileName)
                output = when (destination) {
                    PdfExportDestination.Downloads -> writeDocumentToDownloads(document, fileName)
                    PdfExportDestination.ShareCache -> writeDocumentToShareCache(document, fileName)
                }

                PdfExportResult.Success(
                    uri = output.uri,
                    fileName = fileName,
                    pageCount = writtenPages,
                )
            } catch (t: Throwable) {
                output?.delete()
                PdfExportResult.Error(t)
            } finally {
                document.close()
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
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        } ?: return null

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

        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        } ?: return null

        val normalized = applyExifOrientation(decoded, uri)
        if (normalized !== decoded) {
            decoded.recycleSafely()
        }
        return normalized
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

    private fun writeDocumentToDownloads(document: PdfDocument, fileName: String): PdfOutput {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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

                context.contentResolver.openOutputStream(outputUri)?.use { stream ->
                    document.writeTo(stream)
                } ?: error("Unable to open PDF output")

                context.contentResolver.update(
                    outputUri,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null,
                )

                return PdfOutput(uri = outputUri)
            } catch (t: Throwable) {
                outputUri?.let { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                throw t
            }
        }

        val outputDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.cacheDir,
            "PhotoBook",
        ).apply { mkdirs() }
        val outputFile = File(outputDir, fileName)
        return try {
            FileOutputStream(outputFile).use { stream ->
                document.writeTo(stream)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile,
            )
            PdfOutput(uri = uri, file = outputFile)
        } catch (t: Throwable) {
            runCatching { outputFile.delete() }
            throw t
        }
    }

    private fun writeDocumentToShareCache(document: PdfDocument, fileName: String): PdfOutput {
        val outputDir = File(context.cacheDir, PdfShareCachePolicy.DIRECTORY_NAME).apply {
            mkdirs()
        }
        cleanupStaleShareCache(outputDir)
        val outputFile = File(outputDir, fileName)
        return try {
            FileOutputStream(outputFile).use { stream ->
                document.writeTo(stream)
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                outputFile,
            )
            PdfOutput(uri = uri, file = outputFile)
        } catch (t: Throwable) {
            runCatching { outputFile.delete() }
            throw t
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
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return PdfFileNames.build(stamp = stamp, sourceFileName = sourceFileName)
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) recycle()
    }

    private data class PdfOutput(
        val uri: Uri,
        val file: File? = null,
    ) {
        fun delete() {
            file?.let { outputFile ->
                runCatching { outputFile.delete() }
            }
        }
    }

    private enum class PdfExportDestination {
        Downloads,
        ShareCache,
    }

    companion object {
        private const val MIME_TYPE = "application/pdf"
    }
}
