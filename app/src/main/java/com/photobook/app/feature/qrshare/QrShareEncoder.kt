package com.photobook.app.feature.qrshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.UUID
import javax.inject.Inject
import kotlin.math.max

sealed interface QrShareGenerationResult {
    data class Success(val packet: QrSharePacket) : QrShareGenerationResult
    data class TooLarge(val byteSize: Int, val maxSupportedBytes: Int) : QrShareGenerationResult
    data class Error(val throwable: Throwable? = null) : QrShareGenerationResult
}

data class QrSharePacket(
    val transferId: String,
    val fileName: String,
    val mimeType: String,
    val byteSize: Int,
    val totalChunks: Int,
    val frames: List<String>,
)

class QrShareEncoder @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun generateForPhoto(photo: PhotoRecord): QrShareGenerationResult {
        return withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull()
                ?: return@withContext QrShareGenerationResult.Error()

            val bitmap = loadBitmap(uri) ?: return@withContext QrShareGenerationResult.Error()
            val imageBytes = try {
                compressForTransfer(bitmap)
            } finally {
                bitmap.recycleSafely()
            }
            if (imageBytes.isEmpty()) {
                return@withContext QrShareGenerationResult.Error()
            }
            if (imageBytes.size > MAX_TRANSFER_BYTES) {
                return@withContext QrShareGenerationResult.TooLarge(
                    byteSize = imageBytes.size,
                    maxSupportedBytes = MAX_TRANSFER_BYTES,
                )
            }

            val transferId = UUID.randomUUID().toString().replace("-", "").take(12)
            val fileName = photo.fileName.ifBlank { "PhotoBook_${photo.id}.jpg" }
            val mimeType = "image/jpeg"
            val sha256 = QrPayloadHash.sha256(imageBytes)
            val base64Payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(imageBytes)
            val chunks = base64Payload.chunked(CHUNK_SIZE)
            if (chunks.isEmpty()) {
                return@withContext QrShareGenerationResult.Error()
            }

            val metadataFrame = QrTransferProtocol.encodeMetadata(
                QrTransferFrame.Metadata(
                    transferId = transferId,
                    totalChunks = chunks.size,
                    fileName = fileName,
                    mimeType = mimeType,
                    sha256 = sha256,
                    byteSize = imageBytes.size,
                )
            )

            val dataFrames = chunks.mapIndexed { index, chunk ->
                QrTransferProtocol.encodeData(
                    QrTransferFrame.Data(
                        transferId = transferId,
                        chunkIndex = index,
                        chunkPayload = chunk,
                    )
                )
            }

            QrShareGenerationResult.Success(
                packet = QrSharePacket(
                    transferId = transferId,
                    fileName = fileName,
                    mimeType = mimeType,
                    byteSize = imageBytes.size,
                    totalChunks = chunks.size,
                    frames = listOf(metadataFrame) + dataFrames,
                ),
            )
        }
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumbnail = runCatching {
                context.contentResolver.loadThumbnail(
                    uri,
                    Size(INITIAL_MAX_DIMENSION_PX, INITIAL_MAX_DIMENSION_PX),
                    null,
                )
            }.getOrNull()
            if (thumbnail != null) return thumbnail
        }

        return decodeSampledBitmap(uri, INITIAL_MAX_DIMENSION_PX)
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, bounds)
        } ?: return null

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var inSampleSize = 1
        while (bounds.outWidth / inSampleSize > maxDimensionPx || bounds.outHeight / inSampleSize > maxDimensionPx) {
            inSampleSize *= 2
        }

        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use { input ->
            BitmapFactory.decodeStream(input, null, options)
        }
    }

    private fun compressForTransfer(source: Bitmap): ByteArray {
        var bestBytes = ByteArray(0)
        var currentMaxDimension = max(source.width, source.height)

        while (currentMaxDimension >= MIN_MAX_DIMENSION_PX) {
            val scaled = scaleBitmapToMaxDimension(source, currentMaxDimension)
            try {
                var quality = INITIAL_JPEG_QUALITY
                while (quality >= MIN_JPEG_QUALITY) {
                    val encoded = encodeJpeg(scaled, quality)
                    if (bestBytes.isEmpty() || encoded.size < bestBytes.size) {
                        bestBytes = encoded
                    }
                    if (encoded.size <= MAX_TRANSFER_BYTES) {
                        return encoded
                    }
                    quality -= JPEG_QUALITY_STEP
                }
            } finally {
                if (scaled !== source) {
                    scaled.recycleSafely()
                }
            }
            currentMaxDimension = (currentMaxDimension * DOWNSCALE_FACTOR).toInt()
        }

        return bestBytes
    }

    private fun encodeJpeg(bitmap: Bitmap, quality: Int): ByteArray {
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
    }

    private fun scaleBitmapToMaxDimension(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val currentMax = max(bitmap.width, bitmap.height)
        if (currentMax <= maxDimension) return bitmap

        val scale = maxDimension.toFloat() / currentMax.toFloat()
        val targetWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun Bitmap.recycleSafely() {
        runCatching {
            if (!isRecycled) {
                recycle()
            }
        }
    }

    companion object {
        const val MAX_TRANSFER_BYTES = 120_000
        const val CHUNK_SIZE = 700
        private const val INITIAL_MAX_DIMENSION_PX = 1280
        private const val MIN_MAX_DIMENSION_PX = 640
        private const val INITIAL_JPEG_QUALITY = 92
        private const val MIN_JPEG_QUALITY = 50
        private const val JPEG_QUALITY_STEP = 7
        private const val DOWNSCALE_FACTOR = 0.82f
    }
}
