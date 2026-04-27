package com.photobook.app.feature.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
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
        if (photos.isEmpty()) return PdfExportResult.Error()
        if (photos.size > MAX_PDF_PAGE_COUNT) {
            return PdfExportResult.TooManyPages(
                requested = photos.size,
                maxAllowed = MAX_PDF_PAGE_COUNT,
            )
        }

        return withContext(Dispatchers.IO) {
            var outputUri: Uri? = null
            val document = PdfDocument()
            var writtenPages = 0

            try {
                photos.forEachIndexed { index, photo ->
                    val bitmap = decodeSampledBitmap(Uri.parse(photo.uriString), MAX_IMAGE_DIMENSION)
                        ?: return@forEachIndexed

                    val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, index + 1).create()
                    val page = document.startPage(pageInfo)
                    drawPage(page.canvas, bitmap)
                    document.finishPage(page)
                    writtenPages += 1
                    bitmap.recycle()
                }

                if (writtenPages == 0) {
                    return@withContext PdfExportResult.Error()
                }

                val fileName = buildFileName()
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_DOWNLOADS + "/PhotoBook",
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                outputUri = context.contentResolver.insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return@withContext PdfExportResult.Error()

                context.contentResolver.openOutputStream(outputUri)?.use { stream ->
                    document.writeTo(stream)
                } ?: return@withContext PdfExportResult.Error()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(
                        outputUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }

                PdfExportResult.Success(
                    uri = outputUri,
                    fileName = fileName,
                    pageCount = writtenPages,
                )
            } catch (t: Throwable) {
                outputUri?.let { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                PdfExportResult.Error(t)
            } finally {
                document.close()
            }
        }
    }

    private fun drawPage(canvas: Canvas, bitmap: Bitmap) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawColor(Color.WHITE)

        val pageRect = RectF(0f, 0f, PAGE_WIDTH.toFloat(), PAGE_HEIGHT.toFloat())
        val srcWidth = bitmap.width.toFloat().coerceAtLeast(1f)
        val srcHeight = bitmap.height.toFloat().coerceAtLeast(1f)
        val scale = minOf(pageRect.width() / srcWidth, pageRect.height() / srcHeight)
        val drawWidth = srcWidth * scale
        val drawHeight = srcHeight * scale
        val left = (pageRect.width() - drawWidth) / 2f
        val top = (pageRect.height() - drawHeight) / 2f
        val dest = RectF(left, top, left + drawWidth, top + drawHeight)

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

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun buildFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        return "PhotoBook_$stamp.pdf"
    }

    companion object {
        private const val PAGE_WIDTH = 1240
        private const val PAGE_HEIGHT = 1754
        private const val MAX_IMAGE_DIMENSION = 2400
        private const val MAX_PDF_PAGE_COUNT = 100
        private const val MIME_TYPE = "application/pdf"
    }
}
