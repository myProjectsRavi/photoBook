package com.photobook.app.feature.copytext

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.photobook.app.ml.BundledOnDeviceIntelligence
import com.photobook.app.ml.LocalOcrEngine
import com.photobook.app.util.LocalDiagnostics
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OnDevicePhotoTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context,
    private val formatter: PhotoTextFormatter = PhotoTextFormatter(),
    private val onDeviceIntelligence: BundledOnDeviceIntelligence,
    // PhotoViewerScreen is still manually composed. Resolve that legacy default through Hilt so
    // Copy Text and background indexing share the application-scoped OCR client instead of
    // constructing a second ML Kit TextRecognizer. Hilt-injected callers pass this dependency
    // directly and never execute the default expression.
    private val localOcrEngine: LocalOcrEngine = sharedLocalOcrEngine(context),
) : PhotoTextExtractor {

    override suspend fun extract(photoUri: String): ExtractedTextResult {
        return withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(photoUri) }.getOrNull()
                ?: return@withContext ExtractedTextResult.Error()

            if (!ensureTextRecognizerReady()) {
                return@withContext ExtractedTextResult.Error()
            }

            val bitmap = loadTextBitmap(uri, MAX_TEXT_BITMAP_DIMENSION_PX)
                ?: return@withContext ExtractedTextResult.Error()

            try {
                val rawText = runCatching {
                    recognizeBitmapWithFallback(bitmap)
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

            if (!ensureTextRecognizerReady()) {
                return@withContext ExtractedTextResult.Error()
            }

            val bitmap = loadTextBitmap(uri, MAX_REGION_BITMAP_DIMENSION_PX)
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
                val rawText = runCatching {
                    recognizeBitmapWithFallback(crop)
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

    private suspend fun ensureTextRecognizerReady(): Boolean {
        return onDeviceIntelligence.ensureReady(needsMl = false, needsOcr = true).ocrReady
    }

    private suspend fun recognizeBitmapWithFallback(bitmap: Bitmap): String {
        val primary = localOcrEngine.recognize(bitmap)
        primary.getOrNull()?.takeIf { text -> text.isNotBlank() }?.let { return it }

        val enhanced = enhanceTextBitmap(bitmap)
        if (enhanced != null) {
            try {
                val enhancedResult = localOcrEngine.recognize(enhanced)
                if (enhancedResult.isSuccess) {
                    return enhancedResult.getOrDefault("")
                }
                if (primary.isFailure) {
                    val error = enhancedResult.exceptionOrNull() ?: primary.exceptionOrNull()
                    if (error != null) {
                        LocalDiagnostics.record(
                            context = context,
                            area = "copy-text",
                            message = "Bundled local OCR failed",
                            throwable = error,
                        )
                        throw error
                    }
                }
            } finally {
                recycleSafely(enhanced)
            }
        }

        primary.exceptionOrNull()?.let { error ->
            LocalDiagnostics.record(
                context = context,
                area = "copy-text",
                message = "Bundled local OCR failed",
                throwable = error,
            )
            throw error
        }
        return primary.getOrDefault("")
    }

    private fun enhanceTextBitmap(source: Bitmap): Bitmap? {
        return runCatching {
            val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
            val matrix = ColorMatrix().apply {
                setSaturation(0f)
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.35f, 0f, 0f, 0f, -28f,
                            0f, 1.35f, 0f, 0f, -28f,
                            0f, 0f, 1.35f, 0f, -28f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            Canvas(output).drawBitmap(source, 0f, 0f, paint)
            output
        }.getOrNull()
    }

    private fun loadTextBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val decoded = decodeSampledBitmap(uri, maxDimensionPx) ?: return null
        val oriented = applyExifOrientation(decoded, uri)
        if (oriented !== decoded) {
            recycleSafely(decoded)
        }
        return oriented
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

    private fun applyExifOrientation(source: Bitmap, uri: Uri): Bitmap {
        val orientation = readExifOrientation(uri)
        if (
            orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return source
        }

        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    preScale(-1f, 1f)
                    postRotate(270f)
                }

                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    preScale(-1f, 1f)
                    postRotate(90f)
                }

                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }

        return runCatching {
            Bitmap.createBitmap(
                source,
                0,
                0,
                source.width,
                source.height,
                matrix,
                true,
            )
        }.getOrDefault(source)
    }

    private fun readExifOrientation(uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                )
            } ?: ExifInterface.ORIENTATION_UNDEFINED
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
    }

    private fun recycleSafely(bitmap: Bitmap) {
        runCatching {
            if (!bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    companion object {
        private const val MAX_TEXT_BITMAP_DIMENSION_PX = 3600
        private const val MAX_REGION_BITMAP_DIMENSION_PX = 3200
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface CopyTextOcrEntryPoint {
    fun localOcrEngine(): LocalOcrEngine
}

private fun sharedLocalOcrEngine(context: Context): LocalOcrEngine {
    return EntryPointAccessors.fromApplication(
        context.applicationContext,
        CopyTextOcrEntryPoint::class.java,
    ).localOcrEngine()
}
