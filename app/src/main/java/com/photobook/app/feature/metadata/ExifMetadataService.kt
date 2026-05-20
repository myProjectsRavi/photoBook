package com.photobook.app.feature.metadata

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import androidx.core.content.FileProvider
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface ExifDetailsResult {
    data class Success(val details: ExifDetails) : ExifDetailsResult
    data class Error(val throwable: Throwable? = null) : ExifDetailsResult
}

sealed interface MetadataCleanResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
    ) : MetadataCleanResult

    data class Error(val throwable: Throwable? = null) : MetadataCleanResult
}

sealed interface SafeShareResult {
    data class Success(val items: List<SafeShareItem>) : SafeShareResult
    data class Error(val throwable: Throwable? = null) : SafeShareResult
}

sealed interface SharePrivacyScanResult {
    data class Success(val summary: SharePrivacySummary) : SharePrivacyScanResult
    data class Error(val throwable: Throwable? = null) : SharePrivacyScanResult
}

data class SafeShareItem(
    val uri: Uri,
    val mimeType: String,
    val label: String,
)

data class SafeShareOptions(
    val stripMetadata: Boolean = true,
    val blurFaces: Boolean = false,
)

data class SharePrivacySummary(
    val photoCount: Int,
    val faceCount: Int,
    val metadataRiskCount: Int,
)

data class ExifDetails(
    val fileName: String,
    val mimeType: String,
    val dimensions: String,
    val fileSizeBytes: Long,
    val folderName: String,
    val cameraModel: String,
    val lensModel: String,
    val captureDateTime: String,
    val orientation: String,
    val latitude: Double?,
    val longitude: Double?,
)

class ExifMetadataService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val faceDetector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build(),
        )
    }

    suspend fun loadDetails(photo: PhotoRecord): ExifDetailsResult {
        return withContext(Dispatchers.IO) {
            val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull()
                ?: return@withContext ExifDetailsResult.Error()

            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val exif = ExifInterface(input)
                    ExifDetails(
                        fileName = photo.fileName,
                        mimeType = photo.mimeType.ifBlank { "image/*" },
                        dimensions = "${photo.width}x${photo.height}",
                        fileSizeBytes = photo.fileSize,
                        folderName = photo.folderName,
                        cameraModel = exif.getAttribute(ExifInterface.TAG_MODEL)
                            ?: photo.cameraModel
                            ?: "Unknown",
                        lensModel = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)
                            ?: exif.getAttribute(ExifInterface.TAG_LENS_MAKE)
                            ?: "Unknown",
                        captureDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
                            ?: "Unknown",
                        orientation = orientationLabel(
                            exif.getAttributeInt(
                                ExifInterface.TAG_ORIENTATION,
                                ExifInterface.ORIENTATION_UNDEFINED,
                            ),
                        ),
                        latitude = exif.latLong?.getOrNull(0) ?: photo.latitude,
                        longitude = exif.latLong?.getOrNull(1) ?: photo.longitude,
                    )
                }
            }.fold(
                onSuccess = { details ->
                    if (details != null) {
                        ExifDetailsResult.Success(details)
                    } else {
                        ExifDetailsResult.Error()
                    }
                },
                onFailure = { error ->
                    ExifDetailsResult.Error(error)
                },
            )
        }
    }

    suspend fun createCleanCopy(photo: PhotoRecord): MetadataCleanResult {
        return withContext(Dispatchers.IO) {
            val sourceUri = runCatching { Uri.parse(photo.uriString) }.getOrNull()
                ?: return@withContext MetadataCleanResult.Error()

            var outputUri: Uri? = null
            try {
                val cleanedName = cleanedFileName(photo.fileName)
                val mimeType = normalizeImageMime(photo.mimeType)

                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, cleanedName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(
                            MediaStore.MediaColumns.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/PhotoBook/Clean",
                        )
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }
                }

                outputUri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    values,
                ) ?: return@withContext MetadataCleanResult.Error()

                val copied = copyImageBytes(sourceUri, outputUri)
                if (!copied) {
                    return@withContext MetadataCleanResult.Error()
                }

                val stripped = stripSensitiveExif(outputUri)
                if (!stripped) {
                    val rewritten = rewriteImageWithoutMetadata(sourceUri, outputUri, mimeType)
                    if (!rewritten) {
                        return@withContext MetadataCleanResult.Error()
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    context.contentResolver.update(
                        outputUri,
                        ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                        null,
                        null,
                    )
                }

                MetadataCleanResult.Success(
                    uri = outputUri,
                    fileName = cleanedName,
                )
            } catch (t: Throwable) {
                outputUri?.let { uri ->
                    runCatching { context.contentResolver.delete(uri, null, null) }
                }
                MetadataCleanResult.Error(t)
            }
        }
    }

    suspend fun scanSharePrivacy(photos: List<PhotoRecord>): SharePrivacyScanResult {
        if (photos.isEmpty()) {
            return SharePrivacyScanResult.Success(
                SharePrivacySummary(
                    photoCount = 0,
                    faceCount = 0,
                    metadataRiskCount = 0,
                ),
            )
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                val chunkedPhotos = photos.chunked(8)
                var totalFaceCount = 0

                for (chunk in chunkedPhotos) {
                    val chunkFaceCount = coroutineScope {
                        chunk.map { photo ->
                            async {
                                val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull() ?: return@async 0
                                val bitmap = decodeSampledBitmap(uri, PRIVACY_SCAN_MAX_DIMENSION) ?: return@async 0
                                try {
                                    detectFaces(bitmap).size
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }.awaitAll().sum()
                    }
                    totalFaceCount += chunkFaceCount
                }

                val metadataRiskCount = photos.count { photo ->
                    photo.latitude != null || photo.longitude != null || !photo.cameraModel.isNullOrBlank()
                }

                SharePrivacySummary(
                    photoCount = photos.size,
                    faceCount = totalFaceCount,
                    metadataRiskCount = metadataRiskCount,
                )
            }.fold(
                onSuccess = { summary -> SharePrivacyScanResult.Success(summary) },
                onFailure = { error -> SharePrivacyScanResult.Error(error) },
            )
        }
    }

    suspend fun createSafeShareCopies(
        photos: List<PhotoRecord>,
        options: SafeShareOptions = SafeShareOptions(),
    ): SafeShareResult {
        if (photos.isEmpty()) return SafeShareResult.Error()
        return withContext(Dispatchers.IO) {
            val safeShareDir = File(context.cacheDir, SAFE_SHARE_CACHE_DIR).apply {
                if (!exists()) mkdirs()
            }
            cleanupStaleSafeShareFiles(safeShareDir)

            val createdFiles = mutableListOf<File>()
            val prepared = mutableListOf<SafeShareItem>()
            var firstError: Throwable? = null

            // Process photos sequentially to keep memory bounded on low-RAM devices and to
            // ensure a single bad photo doesn't tank the whole batch.
            for (photo in photos) {
                val resultPair = runCatching {
                    val sourceUri = Uri.parse(photo.uriString)
                    val mimeType = normalizeImageMime(photo.mimeType)
                    val extension = extensionForMime(mimeType)
                    val targetFile = File(
                        safeShareDir,
                        safeShareFileName(photo.fileName, extension),
                    )

                    val copied = copyImageBytesToFile(sourceUri, targetFile)
                    if (!copied) error("Failed to copy image to cache")

                    if (options.blurFaces) {
                        // If face blur fails, log and continue with the unblurred copy.
                        runCatching {
                            blurFacesInSafeShareCopy(
                                sourceUri = sourceUri,
                                targetFile = targetFile,
                                mimeType = mimeType,
                            )
                        }
                    }

                    if (options.stripMetadata) {
                        // Try Exif strip first; if unsupported (PNG/WEBP) fall back to bitmap rewrite.
                        val stripped = stripSensitiveExif(targetFile)
                        if (!stripped) {
                            runCatching {
                                rewriteImageWithoutMetadataFromFile(
                                    sourceFile = targetFile,
                                    mimeType = mimeType,
                                )
                            }
                        }
                    }

                    val shareUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        targetFile,
                    )
                    SafeShareItem(
                        uri = shareUri,
                        mimeType = mimeType,
                        label = photo.fileName.ifBlank { targetFile.name },
                    ) to targetFile
                }

                resultPair.onSuccess { (item, file) ->
                    prepared += item
                    createdFiles += file
                }.onFailure { err ->
                    if (firstError == null) firstError = err
                }
            }

            if (prepared.isNotEmpty()) {
                SafeShareResult.Success(prepared)
            } else {
                cleanupSafeShareFiles(createdFiles)
                SafeShareResult.Error(firstError)
            }
        }
    }

    private fun copyImageBytes(sourceUri: Uri, targetUri: Uri): Boolean {
        val input = context.contentResolver.openInputStream(sourceUri) ?: return false
        val output = context.contentResolver.openOutputStream(targetUri, "w") ?: return false
        return runCatching {
            input.use { src ->
                output.use { dst ->
                    src.copyTo(dst)
                }
            }
            true
        }.getOrDefault(false)
    }

    private fun stripSensitiveExif(targetUri: Uri): Boolean {
        val descriptor = context.contentResolver.openFileDescriptor(targetUri, "rw") ?: return false
        return runCatching {
            descriptor.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                SENSITIVE_EXIF_TAGS.forEach { tag ->
                    exif.setAttribute(tag, null)
                }
                exif.saveAttributes()
            }
            true
        }.getOrDefault(false)
    }

    private fun stripSensitiveExif(targetFile: File): Boolean {
        return runCatching {
            val exif = ExifInterface(targetFile.absolutePath)
            SENSITIVE_EXIF_TAGS.forEach { tag ->
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            true
        }.getOrDefault(false)
    }

    private fun rewriteImageWithoutMetadata(sourceUri: Uri, targetUri: Uri, mimeType: String): Boolean {
        val bitmap = decodeSampledBitmap(sourceUri, MAX_BITMAP_DIMENSION) ?: return false
        val format = if (mimeType.contains("png")) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

        return runCatching {
            val output = context.contentResolver.openOutputStream(targetUri, "w") ?: return false
            output.use { stream ->
                bitmap.compress(format, JPEG_QUALITY, stream)
            }
            true
        }.getOrDefault(false).also {
            bitmap.recycle()
        }
    }

    private fun rewriteImageWithoutMetadataFromFile(sourceFile: File, mimeType: String): Boolean {
        // Decode directly from disk to avoid contentResolver gating on file:// URIs.
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(sourceFile.absolutePath, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return false
        var sample = 1
        while (bounds.outWidth / sample > MAX_BITMAP_DIMENSION || bounds.outHeight / sample > MAX_BITMAP_DIMENSION) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(sourceFile.absolutePath, opts)
        }.getOrNull() ?: return false
        val format = compressFormatForMime(mimeType)
        return runCatching {
            FileOutputStream(sourceFile, false).use { stream ->
                bitmap.compress(format, JPEG_QUALITY, stream)
            }
            true
        }.getOrDefault(false).also {
            bitmap.recycle()
        }
    }

    private suspend fun blurFacesInSafeShareCopy(
        sourceUri: Uri,
        targetFile: File,
        mimeType: String,
    ): Boolean {
        val bitmap = decodeSampledBitmap(sourceUri, SHARE_BLUR_MAX_DIMENSION) ?: return false
        val faces = runCatching { detectFaces(bitmap) }.getOrDefault(emptyList())
        if (faces.isEmpty()) {
            bitmap.recycle()
            return true
        }

        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: bitmap
        if (mutable !== bitmap) {
            bitmap.recycle()
        }
        val canvas = Canvas(mutable)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        faces.forEach { face ->
            pixelateRect(
                bitmap = mutable,
                canvas = canvas,
                paint = paint,
                bounds = face,
            )
        }

        val format = compressFormatForMime(mimeType)
        return runCatching {
            FileOutputStream(targetFile, false).use { output ->
                mutable.compress(format, JPEG_QUALITY, output)
            }
            true
        }.getOrDefault(false).also {
            mutable.recycle()
        }
    }

    private fun copyImageBytesToFile(sourceUri: Uri, targetFile: File): Boolean {
        val input = context.contentResolver.openInputStream(sourceUri) ?: return false
        return runCatching {
            input.use { src ->
                FileOutputStream(targetFile, false).use { dst ->
                    src.copyTo(dst)
                }
            }
            true
        }.getOrDefault(false)
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

        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    private suspend fun detectFaces(bitmap: Bitmap): List<Rect> {
        val image = InputImage.fromBitmap(bitmap, 0)
        return runCatching {
            faceDetector.process(image)
                .await()
                .map { face -> face.boundingBox }
        }.getOrDefault(emptyList())
    }

    private fun pixelateRect(
        bitmap: Bitmap,
        canvas: Canvas,
        paint: Paint,
        bounds: Rect,
    ) {
        val imageWidth = bitmap.width
        val imageHeight = bitmap.height
        val clipped = Rect(
            bounds.left.coerceIn(0, imageWidth - 1),
            bounds.top.coerceIn(0, imageHeight - 1),
            bounds.right.coerceIn(1, imageWidth),
            bounds.bottom.coerceIn(1, imageHeight),
        )
        if (clipped.width() <= 0 || clipped.height() <= 0) return

        val block = (minOf(clipped.width(), clipped.height()) / 8).coerceIn(MIN_PIXEL_BLOCK, MAX_PIXEL_BLOCK)
        
        // Optimize: Use cached pixels for the region if possible, or at least avoid many getPixel calls
        var y = clipped.top
        while (y < clipped.bottom) {
            var x = clipped.left
            while (x < clipped.right) {
                val sampleX = (x + block / 2).coerceIn(0, imageWidth - 1)
                val sampleY = (y + block / 2).coerceIn(0, imageHeight - 1)
                
                // Still using getPixel for simplicity in this specific block logic, 
                // but for massive regions we could grab all pixels at once.
                // Given faces are usually small, getPixel might be okay, but let's be safe.
                paint.color = bitmap.getPixel(sampleX, sampleY)
                
                val right = (x + block).coerceAtMost(clipped.right)
                val bottom = (y + block).coerceAtMost(clipped.bottom)
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    right.toFloat(),
                    bottom.toFloat(),
                    paint,
                )
                x += block
            }
            y += block
        }
    }

    private fun normalizeImageMime(rawMime: String): String {
        val normalized = rawMime.lowercase()
        return when {
            normalized.contains("png") -> "image/png"
            normalized.contains("webp") -> "image/webp"
            normalized.contains("jpeg") || normalized.contains("jpg") -> "image/jpeg"
            else -> "image/jpeg"
        }
    }

    private fun extensionForMime(mimeType: String): String {
        return when {
            mimeType.contains("png") -> "png"
            mimeType.contains("webp") -> "webp"
            else -> "jpg"
        }
    }

    private fun compressFormatForMime(mimeType: String): Bitmap.CompressFormat {
        return when {
            mimeType.contains("png") -> Bitmap.CompressFormat.PNG
            mimeType.contains("webp") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Bitmap.CompressFormat.WEBP_LOSSY
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun cleanedFileName(originalName: String): String {
        val dot = originalName.lastIndexOf('.')
        return if (dot > 0 && dot < originalName.lastIndex) {
            val base = originalName.substring(0, dot)
            val ext = originalName.substring(dot)
            "${base}_clean$ext"
        } else {
            "${originalName}_clean.jpg"
        }
    }

    private fun safeShareFileName(originalName: String, extension: String): String {
        val base = originalName.substringBeforeLast('.', missingDelimiterValue = originalName)
            .ifBlank { "PhotoBook" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(48)
        val uniqueId = UUID.randomUUID().toString().take(8)
        return "${base}_safe_${System.currentTimeMillis()}_${uniqueId}.$extension"
    }

    private fun cleanupStaleSafeShareFiles(dir: File) {
        val cutoff = System.currentTimeMillis() - SAFE_SHARE_TTL_MS
        dir.listFiles().orEmpty().forEach { file ->
            if (file.isFile && file.lastModified() < cutoff) {
                runCatching { file.delete() }
            }
        }
    }

    private fun cleanupSafeShareFiles(files: List<File>) {
        files.forEach { file ->
            runCatching { file.delete() }
        }
    }

    private fun orientationLabel(value: Int): String {
        return when (value) {
            ExifInterface.ORIENTATION_NORMAL -> "Normal"
            ExifInterface.ORIENTATION_ROTATE_90 -> "Rotate 90"
            ExifInterface.ORIENTATION_ROTATE_180 -> "Rotate 180"
            ExifInterface.ORIENTATION_ROTATE_270 -> "Rotate 270"
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> "Flip horizontal"
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> "Flip vertical"
            ExifInterface.ORIENTATION_TRANSPOSE -> "Transpose"
            ExifInterface.ORIENTATION_TRANSVERSE -> "Transverse"
            else -> "Unknown"
        }
    }

    companion object {
        private val SENSITIVE_EXIF_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_MAKE,
        )

        private const val MAX_BITMAP_DIMENSION = 4096
        private const val PRIVACY_SCAN_MAX_DIMENSION = 1280
        private const val SHARE_BLUR_MAX_DIMENSION = 2048
        private const val MIN_PIXEL_BLOCK = 12
        private const val MAX_PIXEL_BLOCK = 40
        private const val JPEG_QUALITY = 97
        private const val SAFE_SHARE_CACHE_DIR = "safe_share"
        private const val SAFE_SHARE_TTL_MS = 24L * 60L * 60L * 1000L
    }
}
