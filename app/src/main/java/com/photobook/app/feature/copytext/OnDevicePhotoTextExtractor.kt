package com.photobook.app.feature.copytext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class OnDevicePhotoTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatter: PhotoTextFormatter = PhotoTextFormatter(),
) : PhotoTextExtractor {

    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun extract(photoUri: String): ExtractedTextResult {
        return withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(photoUri) }.getOrNull()
                ?: return@withContext ExtractedTextResult.Error()

            val bitmap = loadBitmap(uri)
                ?: return@withContext ExtractedTextResult.Error()

            try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val rawText = runCatching {
                    recognizer.process(image).await().text
                }.getOrElse { error ->
                    return@withContext ExtractedTextResult.Error(error)
                }

                val formatted = formatter.format(rawText)
                if (formatted.isBlank()) {
                    ExtractedTextResult.Empty
                } else {
                    ExtractedTextResult.Success(formatted)
                }
            } finally {
                recycleSafely(bitmap)
            }
        }
    }

    override suspend fun extractRegion(
        photoUri: String,
        region: NormalizedTextRegion,
    ): ExtractedTextResult {
        return withContext(Dispatchers.IO) {
            val normalizedRegion = region.normalized()
            if (!normalizedRegion.isUsable()) {
                return@withContext ExtractedTextResult.Empty
            }

            val uri = runCatching { Uri.parse(photoUri) }.getOrNull()
                ?: return@withContext ExtractedTextResult.Error()

            val bitmap = decodeSampledBitmap(uri, MAX_REGION_BITMAP_DIMENSION_PX)
                ?: return@withContext ExtractedTextResult.Error()

            val crop = try {
                cropBitmap(bitmap, normalizedRegion)
            } catch (_: Throwable) {
                null
            } ?: run {
                recycleSafely(bitmap)
                return@withContext ExtractedTextResult.Empty
            }

            try {
                val image = InputImage.fromBitmap(crop, 0)
                val rawText = runCatching {
                    recognizer.process(image).await().text
                }.getOrElse { error ->
                    return@withContext ExtractedTextResult.Error(error)
                }

                val formatted = formatter.format(rawText)
                if (formatted.isBlank()) {
                    ExtractedTextResult.Empty
                } else {
                    ExtractedTextResult.Success(formatted)
                }
            } finally {
                recycleSafely(crop)
                if (crop !== bitmap) {
                    recycleSafely(bitmap)
                }
            }
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumbnail = runCatching {
                context.contentResolver.loadThumbnail(
                    uri,
                    Size(MAX_BITMAP_DIMENSION_PX, MAX_BITMAP_DIMENSION_PX),
                    null,
                )
            }.getOrNull()
            if (thumbnail != null) {
                return thumbnail
            }
        }
        return decodeSampledBitmap(uri, MAX_BITMAP_DIMENSION_PX)
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

        val sampleSize = calculateInSampleSize(
            width = bounds.outWidth,
            height = bounds.outHeight,
            maxDimensionPx = maxDimensionPx,
        )
        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOptions)
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimensionPx: Int): Int {
        var sample = 1

        while (width / sample > maxDimensionPx || height / sample > maxDimensionPx) {
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun cropBitmap(bitmap: Bitmap, region: NormalizedTextRegion): Bitmap? {
        val left = (region.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (region.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (region.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width)
        val bottom = (region.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height)
        val rect = Rect(left, top, right, bottom)
        if (rect.width() <= 1 || rect.height() <= 1) return null
        return runCatching {
            Bitmap.createBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
        }.getOrNull()
    }

    private fun recycleSafely(bitmap: Bitmap) {
        runCatching {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    companion object {
        private const val MAX_BITMAP_DIMENSION_PX = 1600
        private const val MAX_REGION_BITMAP_DIMENSION_PX = 2048
    }
}
