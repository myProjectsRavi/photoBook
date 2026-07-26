package com.photobook.app.feature.editor

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
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.photobook.app.data.model.PhotoRecord
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoEditService @Inject constructor(
    private val context: Context,
) {
    suspend fun renderEditedCopy(photo: PhotoRecord, state: PhotoEditState): PhotoEditResult {
        return withContext(Dispatchers.Default) {
            var source: Bitmap? = null
            var rotated: Bitmap? = null
            var cropped: Bitmap? = null
            var filtered: Bitmap? = null
            runCatching {
                source = decodeSampledBitmap(photo.uriString) ?: return@withContext PhotoEditResult.Error
                rotated = applyRotation(source!!, state.rotationQuarterTurns)
                if (rotated !== source) {
                    source?.recycleSafely()
                }
                cropped = applyCrop(rotated!!, state)
                if (cropped !== rotated) {
                    rotated?.recycleSafely()
                }

                filtered = applyToneAndFilter(cropped!!, state)
                if (filtered !== cropped) {
                    cropped?.recycleSafely()
                }

                val outputDir = File(context.cacheDir, "safe_share").apply { mkdirs() }
                val outputFile = File(outputDir, "edit_${photo.id}_${System.currentTimeMillis()}.jpg")
                withContext(Dispatchers.IO) {
                    FileOutputStream(outputFile).use { stream ->
                        filtered?.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)
                    }
                }

                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    outputFile,
                )
                PhotoEditResult.Success(
                    uri = uri,
                    fileName = outputFile.name,
                    mimeType = "image/jpeg",
                )
            }.getOrElse {
                PhotoEditResult.Error
            }.also {
                filtered?.recycleSafely()
                if (filtered !== cropped) {
                    cropped?.recycleSafely()
                }
                if (cropped !== rotated) {
                    rotated?.recycleSafely()
                }
                if (rotated !== source) {
                    source?.recycleSafely()
                }
            }
        }
    }

    private fun decodeSampledBitmap(uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > MAX_DIMENSION || bounds.outHeight / sample > MAX_DIMENSION) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        } ?: return null

        val normalized = applyExifOrientation(decoded, uri)
        if (normalized !== decoded) {
            decoded.recycleSafely()
        }
        return normalized
    }

    private fun applyRotation(source: Bitmap, quarterTurns: Int): Bitmap {
        val normalizedTurns = ((quarterTurns % 4) + 4) % 4
        if (normalizedTurns == 0) return source
        val matrix = Matrix().apply {
            postRotate(normalizedTurns * 90f)
        }
        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true,
        )
    }

    private fun applyCrop(source: Bitmap, state: PhotoEditState): Bitmap {
        state.customCrop?.normalized()?.takeIf { it.isUsable() }?.let { region ->
            return applyCustomCrop(source, region)
        }

        return applyPresetCrop(source, state.cropPreset)
    }

    private fun applyPresetCrop(source: Bitmap, preset: CropPreset): Bitmap {
        if (preset == CropPreset.Original) return source
        val targetRatio = preset.ratio
        val currentRatio = source.width.toFloat() / source.height.toFloat()

        val cropWidth: Int
        val cropHeight: Int
        if (currentRatio > targetRatio) {
            cropHeight = source.height
            cropWidth = (cropHeight * targetRatio).toInt().coerceAtLeast(1)
        } else {
            cropWidth = source.width
            cropHeight = (cropWidth / targetRatio).toInt().coerceAtLeast(1)
        }
        val left = ((source.width - cropWidth) / 2).coerceAtLeast(0)
        val top = ((source.height - cropHeight) / 2).coerceAtLeast(0)
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
    }

    private fun applyCustomCrop(source: Bitmap, region: NormalizedCropRegion): Bitmap {
        val left = (region.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (region.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (region.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (region.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val cropWidth = (right - left).coerceAtLeast(1)
        val cropHeight = (bottom - top).coerceAtLeast(1)
        if (cropWidth == source.width && cropHeight == source.height) return source
        return Bitmap.createBitmap(source, left, top, cropWidth, cropHeight)
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

    private fun applyToneAndFilter(source: Bitmap, state: PhotoEditState): Bitmap {
        if (state.isIdentity()) return source
        val matrix = ColorMatrix()
        matrix.postConcat(contrastMatrix(state.contrast))
        matrix.postConcat(exposureMatrix(state.exposure))
        matrix.postConcat(filterMatrix(state.filter))

        val output = Bitmap.createBitmap(
            source.width.coerceAtLeast(1),
            source.height.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(
            source,
            Rect(0, 0, source.width, source.height),
            Rect(0, 0, output.width, output.height),
            paint,
        )
        return output
    }

    private fun contrastMatrix(contrast: Float): ColorMatrix {
        val c = contrast.coerceIn(CONTRAST_MIN, CONTRAST_MAX)
        val translate = 128f * (1f - c)
        return ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, translate,
                0f, c, 0f, 0f, translate,
                0f, 0f, c, 0f, translate,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun exposureMatrix(exposure: Float): ColorMatrix {
        val offset = exposure.coerceIn(EXPOSURE_MIN, EXPOSURE_MAX) * 62f
        return ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, offset,
                0f, 1f, 0f, 0f, offset,
                0f, 0f, 1f, 0f, offset,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
    }

    private fun filterMatrix(filter: QuickFilter): ColorMatrix {
        return when (filter) {
            QuickFilter.Original -> ColorMatrix()
            QuickFilter.Mono -> ColorMatrix().apply { setSaturation(0f) }
            QuickFilter.Vivid -> ColorMatrix().apply {
                setSaturation(1.28f)
                postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            1.05f, 0f, 0f, 0f, 6f,
                            0f, 1.05f, 0f, 0f, 6f,
                            0f, 0f, 1.05f, 0f, 6f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                )
            }

            QuickFilter.Warm -> ColorMatrix(
                floatArrayOf(
                    1.08f, 0f, 0f, 0f, 8f,
                    0f, 1.0f, 0f, 0f, 2f,
                    0f, 0f, 0.92f, 0f, -6f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )

            QuickFilter.Cool -> ColorMatrix(
                floatArrayOf(
                    0.94f, 0f, 0f, 0f, -4f,
                    0f, 1.0f, 0f, 0f, 0f,
                    0f, 0f, 1.08f, 0f, 8f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
        }
    }

    private fun Bitmap.recycleSafely() {
        if (!isRecycled) {
            recycle()
        }
    }

    companion object {
        private const val MAX_DIMENSION = 2400
        private const val JPEG_QUALITY = 93
        const val EXPOSURE_MIN = -1f
        const val EXPOSURE_MAX = 1f
        const val CONTRAST_MIN = 0.6f
        const val CONTRAST_MAX = 1.6f
    }
}

data class PhotoEditState(
    val rotationQuarterTurns: Int = 0,
    val cropPreset: CropPreset = CropPreset.Original,
    val customCrop: NormalizedCropRegion? = null,
    val exposure: Float = 0f,
    val contrast: Float = 1f,
    val filter: QuickFilter = QuickFilter.Original,
) {
    fun isIdentity(): Boolean {
        return rotationQuarterTurns % 4 == 0 &&
            cropPreset == CropPreset.Original &&
            customCrop == null &&
            exposure == 0f &&
            contrast == 1f &&
            filter == QuickFilter.Original
    }
}

data class NormalizedCropRegion(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized(): NormalizedCropRegion {
        val safeLeft = left.coerceIn(0f, 1f)
        val safeTop = top.coerceIn(0f, 1f)
        val safeRight = right.coerceIn(0f, 1f)
        val safeBottom = bottom.coerceIn(0f, 1f)
        return NormalizedCropRegion(
            left = minOf(safeLeft, safeRight),
            top = minOf(safeTop, safeBottom),
            right = maxOf(safeLeft, safeRight),
            bottom = maxOf(safeTop, safeBottom),
        )
    }

    fun isUsable(): Boolean {
        val region = normalized()
        return region.right - region.left >= MIN_SIZE &&
            region.bottom - region.top >= MIN_SIZE
    }

    fun aspectRatioFor(photoAspectRatio: Float): Float {
        val region = normalized()
        val width = (region.right - region.left).coerceAtLeast(MIN_SIZE)
        val height = (region.bottom - region.top).coerceAtLeast(MIN_SIZE)
        return (photoAspectRatio.coerceAtLeast(0.01f) * width / height).coerceIn(0.45f, 2.2f)
    }

    companion object {
        private const val MIN_SIZE = 0.08f
    }
}

enum class CropPreset(val label: String, val ratio: Float) {
    Original("Original", 1f),
    Square("1:1", 1f),
    FourFive("4:5", 4f / 5f),
    SixteenNine("16:9", 16f / 9f),
}

enum class QuickFilter(val label: String) {
    Original("Original"),
    Vivid("Vivid"),
    Mono("Mono"),
    Warm("Warm"),
    Cool("Cool"),
}

sealed interface PhotoEditResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
        val mimeType: String,
    ) : PhotoEditResult

    data object Error : PhotoEditResult
}
