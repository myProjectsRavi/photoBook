package com.photobook.app.feature.metadata

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ml.CompactLocalIntelligence
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val cleanCopyJournal by lazy {
        context.getSharedPreferences(CLEAN_COPY_JOURNAL_PREFS, Context.MODE_PRIVATE)
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
                    if (details != null) ExifDetailsResult.Success(details) else ExifDetailsResult.Error()
                },
                onFailure = { error -> ExifDetailsResult.Error(error) },
            )
        }
    }

    suspend fun createCleanCopy(photo: PhotoRecord): MetadataCleanResult {
        return withContext(Dispatchers.IO) {
            CLEAN_COPY_MUTEX.withLock {
                if (!reconcilePendingCleanCopyJournal()) {
                    return@withLock MetadataCleanResult.Error(
                        IllegalStateException("Unable to reconcile a previous pending clean copy"),
                    )
                }

                val sourceUri = runCatching { Uri.parse(photo.uriString) }.getOrNull()
                    ?: return@withLock MetadataCleanResult.Error()
                val mimeType = normalizeImageMime(photo.mimeType)
                val operationId = UUID.randomUUID().toString().take(16)
                val cleanedName = cleanedFileName(photo.fileName, mimeType, operationId)

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    !recordPendingCleanCopyIntent(cleanedName)
                ) {
                    return@withLock MetadataCleanResult.Error(
                        IllegalStateException("Unable to journal clean-copy intent"),
                    )
                }

                var outputUri: Uri? = null
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, cleanedName)
                        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.MediaColumns.RELATIVE_PATH, CLEAN_COPY_RELATIVE_PATH)
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                    }

                    val insertedUri = context.contentResolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values,
                    ) ?: run {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            clearPendingCleanCopyJournal()
                        }
                        return@withLock MetadataCleanResult.Error()
                    }
                    outputUri = insertedUri

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                        !recordPendingCleanCopyUri(insertedUri)
                    ) {
                        throw IllegalStateException("Unable to journal pending clean-copy URI")
                    }

                    check(copyImageBytes(sourceUri, insertedUri)) { "Failed to copy source image" }
                    check(sanitizeMetadata(sourceUri, insertedUri, mimeType)) {
                        "Unable to prove sensitive metadata removal"
                    }
                    check(verifyReadableImage(insertedUri)) { "Clean copy is not a readable image" }

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val publishedRows = context.contentResolver.update(
                            insertedUri,
                            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                            null,
                            null,
                        )
                        check(publishedRows > 0) { "Unable to publish clean copy" }
                        clearPendingCleanCopyJournal()
                    }

                    MetadataCleanResult.Success(uri = insertedUri, fileName = cleanedName)
                } catch (t: Throwable) {
                    val deleted = outputUri?.let { uri ->
                        runCatching { context.contentResolver.delete(uri, null, null) }
                            .getOrDefault(0) > 0
                    } ?: true
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && deleted) {
                        clearPendingCleanCopyJournal()
                    }
                    MetadataCleanResult.Error(t)
                }
            }
        }
    }

    suspend fun scanSharePrivacy(photos: List<PhotoRecord>): SharePrivacyScanResult {
        if (photos.isEmpty()) {
            return SharePrivacyScanResult.Success(
                SharePrivacySummary(photoCount = 0, faceCount = 0, metadataRiskCount = 0),
            )
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                var totalFaceCount = 0
                for (chunk in photos.chunked(8)) {
                    totalFaceCount += coroutineScope {
                        chunk.map { photo ->
                            async {
                                val uri = runCatching { Uri.parse(photo.uriString) }.getOrNull()
                                    ?: error("Invalid photo URI during privacy scan")
                                val bitmap = decodeOrientedSampledBitmap(uri, PRIVACY_SCAN_MAX_DIMENSION)
                                    ?: error("Unreadable photo during privacy scan")
                                try {
                                    detectFaces(bitmap).size
                                } finally {
                                    bitmap.recycle()
                                }
                            }
                        }.awaitAll().sum()
                    }
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
            val safeShareDir = File(context.cacheDir, SAFE_SHARE_CACHE_DIR)
            if (!safeShareDir.exists() && !safeShareDir.mkdirs()) {
                return@withContext SafeShareResult.Error(
                    IllegalStateException("Unable to create Safe Share cache directory"),
                )
            }
            cleanupStaleSafeShareFiles(safeShareDir)

            val createdFiles = mutableListOf<File>()
            val prepared = mutableListOf<SafeShareItem>()
            for (photo in photos) {
                var failedFile: File? = null
                val resultPair = runCatching {
                    val sourceUri = Uri.parse(photo.uriString)
                    val mimeType = normalizeImageMime(photo.mimeType)
                    val outputFile = File(
                        safeShareDir,
                        safeShareFileName(photo.fileName, extensionForMime(mimeType)),
                    )
                    failedFile = outputFile

                    check(copyImageBytesToFile(sourceUri, outputFile)) {
                        "Failed to copy image to Safe Share cache"
                    }
                    if (options.blurFaces) {
                        check(
                            blurFacesInSafeShareCopy(
                                sourceUri = sourceUri,
                                targetFile = outputFile,
                                mimeType = mimeType,
                            ) && verifyNoDetectableFaces(outputFile),
                        ) { "Unable to prove face blurring" }
                    }
                    if (options.stripMetadata) {
                        check(sanitizeMetadata(outputFile, mimeType)) {
                            "Unable to prove sensitive metadata removal"
                        }
                    }
                    check(verifyReadableImage(outputFile)) {
                        "Safe Share output is not a readable image"
                    }

                    val shareUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        outputFile,
                    )
                    SafeShareItem(
                        uri = shareUri,
                        mimeType = mimeType,
                        label = photo.fileName.ifBlank { outputFile.name },
                    ) to outputFile
                }

                val pair = resultPair.getOrElse { error ->
                    failedFile?.let { file -> runCatching { file.delete() } }
                    cleanupSafeShareFiles(createdFiles)
                    return@withContext SafeShareResult.Error(error)
                }
                prepared += pair.first
                createdFiles += pair.second
            }
            SafeShareResult.Success(prepared)
        }
    }

    private fun copyImageBytes(sourceUri: Uri, targetUri: Uri): Boolean {
        return runCatching {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching false
            input.use { src ->
                val output = context.contentResolver.openOutputStream(targetUri, "w")
                    ?: return@runCatching false
                output.use { dst -> src.copyTo(dst) }
            }
            true
        }.getOrDefault(false)
    }

    private fun sanitizeMetadata(sourceUri: Uri, targetUri: Uri, mimeType: String): Boolean {
        val xmpState = hasXmp(targetUri)
        if (xmpState == false && stripSensitiveExif(targetUri) && verifySensitiveMetadataRemoved(targetUri)) {
            return true
        }
        if (!rewriteImageWithoutMetadata(sourceUri, targetUri, mimeType)) return false
        return verifySensitiveMetadataRemoved(targetUri)
    }

    private fun sanitizeMetadata(targetFile: File, mimeType: String): Boolean {
        val xmpState = hasXmp(targetFile)
        if (xmpState == false && stripSensitiveExif(targetFile) && verifySensitiveMetadataRemoved(targetFile)) {
            return true
        }
        if (!rewriteImageWithoutMetadataFromFile(targetFile, mimeType)) return false
        return verifySensitiveMetadataRemoved(targetFile)
    }

    private fun stripSensitiveExif(targetUri: Uri): Boolean {
        val descriptor = context.contentResolver.openFileDescriptor(targetUri, "rw") ?: return false
        return runCatching {
            descriptor.use { pfd ->
                val exif = ExifInterface(pfd.fileDescriptor)
                SENSITIVE_EXIF_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
                exif.saveAttributes()
            }
            true
        }.getOrDefault(false)
    }

    private fun stripSensitiveExif(targetFile: File): Boolean {
        return runCatching {
            val exif = ExifInterface(targetFile.absolutePath)
            SENSITIVE_EXIF_TAGS.forEach { tag -> exif.setAttribute(tag, null) }
            exif.saveAttributes()
            true
        }.getOrDefault(false)
    }

    private fun hasXmp(targetUri: Uri): Boolean? {
        return runCatching {
            context.contentResolver.openInputStream(targetUri)?.use { input ->
                ExifInterface(input).getAttributeBytes(ExifInterface.TAG_XMP) != null
            }
        }.getOrNull()
    }

    private fun hasXmp(targetFile: File): Boolean? {
        return runCatching {
            ExifInterface(targetFile.absolutePath).getAttributeBytes(ExifInterface.TAG_XMP) != null
        }.getOrNull()
    }

    private fun verifySensitiveMetadataRemoved(targetUri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(targetUri)?.use { input ->
                val exif = ExifInterface(input)
                SENSITIVE_EXIF_TAGS.all { tag -> exif.getAttribute(tag).isNullOrBlank() } &&
                    exif.getAttributeBytes(ExifInterface.TAG_XMP) == null
            } ?: false
        }.getOrDefault(false)
    }

    private fun verifySensitiveMetadataRemoved(targetFile: File): Boolean {
        return runCatching {
            val exif = ExifInterface(targetFile.absolutePath)
            SENSITIVE_EXIF_TAGS.all { tag -> exif.getAttribute(tag).isNullOrBlank() } &&
                exif.getAttributeBytes(ExifInterface.TAG_XMP) == null
        }.getOrDefault(false)
    }

    private fun rewriteImageWithoutMetadata(sourceUri: Uri, targetUri: Uri, mimeType: String): Boolean {
        val bitmap = decodeOrientedSampledBitmap(sourceUri, MAX_BITMAP_DIMENSION) ?: return false
        val result = runCatching {
            val output = context.contentResolver.openOutputStream(targetUri, "w")
                ?: return@runCatching false
            output.use { stream -> bitmap.compress(compressFormatForMime(mimeType), JPEG_QUALITY, stream) }
        }.getOrDefault(false)
        bitmap.recycle()
        return result
    }

    private fun rewriteImageWithoutMetadataFromFile(sourceFile: File, mimeType: String): Boolean {
        val bitmap = decodeOrientedSampledBitmap(sourceFile, MAX_BITMAP_DIMENSION) ?: return false
        val result = runCatching {
            FileOutputStream(sourceFile, false).use { stream ->
                bitmap.compress(compressFormatForMime(mimeType), JPEG_QUALITY, stream)
            }
        }.getOrDefault(false)
        bitmap.recycle()
        return result
    }

    private suspend fun blurFacesInSafeShareCopy(
        sourceUri: Uri,
        targetFile: File,
        mimeType: String,
    ): Boolean {
        val bitmap = decodeOrientedSampledBitmap(sourceUri, SHARE_BLUR_MAX_DIMENSION) ?: return false
        val faces = runCatching { detectFaces(bitmap) }.getOrElse {
            bitmap.recycle()
            return false
        }
        if (faces.isEmpty()) {
            bitmap.recycle()
            return true
        }

        val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true) ?: bitmap
        if (mutable !== bitmap) bitmap.recycle()
        val canvas = Canvas(mutable)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        faces.forEach { face ->
            pixelateRect(bitmap = mutable, canvas = canvas, paint = paint, bounds = face)
        }

        val result = runCatching {
            FileOutputStream(targetFile, false).use { output ->
                mutable.compress(compressFormatForMime(mimeType), JPEG_QUALITY, output)
            }
        }.getOrDefault(false)
        mutable.recycle()
        return result
    }

    private fun verifyNoDetectableFaces(targetFile: File): Boolean {
        val bitmap = decodeOrientedSampledBitmap(targetFile, SHARE_BLUR_MAX_DIMENSION) ?: return false
        return try {
            runCatching { detectFaces(bitmap).isEmpty() }.getOrDefault(false)
        } finally {
            bitmap.recycle()
        }
    }

    private fun copyImageBytesToFile(sourceUri: Uri, targetFile: File): Boolean {
        return runCatching {
            val input = context.contentResolver.openInputStream(sourceUri) ?: return@runCatching false
            input.use { src ->
                FileOutputStream(targetFile, false).use { dst -> src.copyTo(dst) }
            }
            true
        }.getOrDefault(false)
    }

    private fun verifyReadableImage(targetUri: Uri): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return runCatching {
            val input = context.contentResolver.openInputStream(targetUri) ?: return@runCatching false
            input.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
            bounds.outWidth > 0 && bounds.outHeight > 0
        }.getOrDefault(false)
    }

    private fun verifyReadableImage(targetFile: File): Boolean {
        if (!targetFile.isFile || targetFile.length() <= 0L) return false
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        return runCatching {
            BitmapFactory.decodeFile(targetFile.absolutePath, bounds)
            bounds.outWidth > 0 && bounds.outHeight > 0
        }.getOrDefault(false)
    }

    private fun decodeOrientedSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val orientation = readExifOrientation(uri)
        val bitmap = decodeSampledBitmap(uri, maxDimensionPx) ?: return null
        return applyExifOrientation(bitmap, orientation)
    }

    private fun decodeOrientedSampledBitmap(file: File, maxDimensionPx: Int): Bitmap? {
        val orientation = readExifOrientation(file)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { BitmapFactory.decodeFile(file.absolutePath, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx || bounds.outHeight / sample > maxDimensionPx) {
            sample *= 2
        }
        val bitmap = runCatching {
            BitmapFactory.decodeFile(
                file.absolutePath,
                BitmapFactory.Options().apply {
                    inSampleSize = sample.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }.getOrNull() ?: return null
        return applyExifOrientation(bitmap, orientation)
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val boundsInput = context.contentResolver.openInputStream(uri) ?: return null
        boundsInput.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx || bounds.outHeight / sample > maxDimensionPx) {
            sample *= 2
        }
        val input = context.contentResolver.openInputStream(uri) ?: return null
        return input.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                BitmapFactory.Options().apply {
                    inSampleSize = sample.coerceAtLeast(1)
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                },
            )
        }
    }

    private fun readExifOrientation(uri: Uri): Int {
        return runCatching {
            val input = context.contentResolver.openInputStream(uri)
                ?: return@runCatching ExifInterface.ORIENTATION_UNDEFINED
            input.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                )
            }
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
    }

    private fun readExifOrientation(file: File): Int {
        return runCatching {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_UNDEFINED,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
    }

    private fun applyExifOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return bitmap
        }

        return runCatching {
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }.getOrNull()?.also { transformed ->
            if (transformed !== bitmap) bitmap.recycle()
        } ?: bitmap
    }

    private fun detectFaces(bitmap: Bitmap): List<Rect> {
        return CompactLocalIntelligence.detectFacesStrict(bitmap)
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

        val block = (minOf(clipped.width(), clipped.height()) / 8)
            .coerceIn(MIN_PIXEL_BLOCK, MAX_PIXEL_BLOCK)
        var y = clipped.top
        while (y < clipped.bottom) {
            var x = clipped.left
            while (x < clipped.right) {
                val sampleX = (x + block / 2).coerceIn(0, imageWidth - 1)
                val sampleY = (y + block / 2).coerceIn(0, imageHeight - 1)
                paint.color = bitmap.getPixel(sampleX, sampleY)
                canvas.drawRect(
                    x.toFloat(),
                    y.toFloat(),
                    (x + block).coerceAtMost(clipped.right).toFloat(),
                    (y + block).coerceAtMost(clipped.bottom).toFloat(),
                    paint,
                )
                x += block
            }
            y += block
        }
    }

    private fun reconcilePendingCleanCopyJournal(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        val pendingUri = cleanCopyJournal.getString(KEY_PENDING_CLEAN_COPY_URI, null)
        if (!pendingUri.isNullOrBlank()) {
            val uri = runCatching { Uri.parse(pendingUri) }.getOrNull() ?: return false
            val pendingState = queryPendingState(uri) ?: return false
            return when (pendingState) {
                PENDING_ROW_MISSING, 0 -> clearPendingCleanCopyJournal()
                1 -> deletePendingUri(uri) && clearPendingCleanCopyJournal()
                else -> false
            }
        }

        val pendingName = cleanCopyJournal.getString(KEY_PENDING_CLEAN_COPY_NAME, null)
            ?: return true
        return reconcilePendingCleanCopyByName(pendingName)
    }

    private fun queryPendingState(uri: Uri): Int? {
        return runCatching {
            val cursor = context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.IS_PENDING),
                null,
                null,
                null,
            ) ?: error("MediaStore returned no cursor for pending-row query")
            cursor.use {
                if (!it.moveToFirst()) PENDING_ROW_MISSING else it.getInt(0)
            }
        }.getOrNull()
    }

    private fun reconcilePendingCleanCopyByName(displayName: String): Boolean {
        val pendingUris = runCatching {
            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.IS_PENDING} = 1",
                arrayOf(displayName),
                null,
            ) ?: error("MediaStore returned no cursor for pending-name reconciliation")
            cursor.use {
                buildList {
                    while (it.moveToNext()) {
                        add(
                            ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                it.getLong(0),
                            ),
                        )
                    }
                }
            }
        }.getOrNull() ?: return false

        if (pendingUris.any { uri -> !deletePendingUri(uri) }) return false
        return clearPendingCleanCopyJournal()
    }

    private fun deletePendingUri(uri: Uri): Boolean {
        val deleted = runCatching {
            context.contentResolver.delete(uri, null, null)
        }.getOrDefault(0)
        if (deleted > 0) return true
        return queryPendingState(uri) == PENDING_ROW_MISSING
    }

    private fun recordPendingCleanCopyIntent(displayName: String): Boolean {
        return cleanCopyJournal.edit()
            .putString(KEY_PENDING_CLEAN_COPY_NAME, displayName)
            .remove(KEY_PENDING_CLEAN_COPY_URI)
            .commit()
    }

    private fun recordPendingCleanCopyUri(uri: Uri): Boolean {
        return cleanCopyJournal.edit()
            .putString(KEY_PENDING_CLEAN_COPY_URI, uri.toString())
            .commit()
    }

    private fun clearPendingCleanCopyJournal(): Boolean {
        return cleanCopyJournal.edit()
            .remove(KEY_PENDING_CLEAN_COPY_NAME)
            .remove(KEY_PENDING_CLEAN_COPY_URI)
            .commit()
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

    @Suppress("DEPRECATION")
    private fun compressFormatForMime(mimeType: String): Bitmap.CompressFormat {
        return when {
            mimeType.contains("png") -> Bitmap.CompressFormat.PNG
            mimeType.contains("webp") && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
                Bitmap.CompressFormat.WEBP_LOSSY
            mimeType.contains("webp") -> Bitmap.CompressFormat.WEBP
            else -> Bitmap.CompressFormat.JPEG
        }
    }

    private fun cleanedFileName(originalName: String, mimeType: String, operationId: String): String {
        val base = originalName.substringBeforeLast('.', missingDelimiterValue = originalName)
            .ifBlank { "PhotoBook" }
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .take(64)
        return "${base}_clean_$operationId.${extensionForMime(mimeType)}"
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
            if (file.isFile && file.lastModified() < cutoff) runCatching { file.delete() }
        }
    }

    private fun cleanupSafeShareFiles(files: List<File>) {
        files.forEach { file -> runCatching { file.delete() } }
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
        // The recovery journal is process-wide, so serialization must also be process-wide even
        // when multiple UI surfaces construct independent ExifMetadataService instances.
        private val CLEAN_COPY_MUTEX = Mutex()

        private val SENSITIVE_EXIF_TAGS = listOf(
            ExifInterface.TAG_GPS_VERSION_ID,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE,
            ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_GPS_SATELLITES,
            ExifInterface.TAG_GPS_STATUS,
            ExifInterface.TAG_GPS_MEASURE_MODE,
            ExifInterface.TAG_GPS_DOP,
            ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_SPEED,
            ExifInterface.TAG_GPS_TRACK_REF,
            ExifInterface.TAG_GPS_TRACK,
            ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION,
            ExifInterface.TAG_GPS_MAP_DATUM,
            ExifInterface.TAG_GPS_DEST_LATITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE_REF,
            ExifInterface.TAG_GPS_DEST_LONGITUDE,
            ExifInterface.TAG_GPS_DEST_BEARING_REF,
            ExifInterface.TAG_GPS_DEST_BEARING,
            ExifInterface.TAG_GPS_DEST_DISTANCE_REF,
            ExifInterface.TAG_GPS_DEST_DISTANCE,
            ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION,
            ExifInterface.TAG_GPS_DATESTAMP,
            ExifInterface.TAG_GPS_DIFFERENTIAL,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_CAMERA_OWNER_NAME,
            ExifInterface.TAG_BODY_SERIAL_NUMBER,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_LENS_MAKE,
            ExifInterface.TAG_LENS_SERIAL_NUMBER,
            ExifInterface.TAG_IMAGE_UNIQUE_ID,
            ExifInterface.TAG_MAKER_NOTE,
            ExifInterface.TAG_DATETIME,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_DATETIME_DIGITIZED,
            ExifInterface.TAG_SUBSEC_TIME,
            ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
            ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
            ExifInterface.TAG_OFFSET_TIME,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
            ExifInterface.TAG_SOFTWARE,
            ExifInterface.TAG_IMAGE_DESCRIPTION,
            ExifInterface.TAG_ARTIST,
            ExifInterface.TAG_COPYRIGHT,
            ExifInterface.TAG_USER_COMMENT,
        )

        private const val MAX_BITMAP_DIMENSION = 4096
        private const val PRIVACY_SCAN_MAX_DIMENSION = 1280
        private const val SHARE_BLUR_MAX_DIMENSION = 2048
        private const val MIN_PIXEL_BLOCK = 12
        private const val MAX_PIXEL_BLOCK = 40
        private const val JPEG_QUALITY = 97
        private const val SAFE_SHARE_CACHE_DIR = "safe_share"
        private const val SAFE_SHARE_TTL_MS = 24L * 60L * 60L * 1000L
        private val CLEAN_COPY_RELATIVE_PATH = Environment.DIRECTORY_PICTURES + "/PhotoBook/Clean"
        private const val CLEAN_COPY_JOURNAL_PREFS = "metadata_clean_copy_journal_v2"
        private const val KEY_PENDING_CLEAN_COPY_NAME = "pending_name"
        private const val KEY_PENDING_CLEAN_COPY_URI = "pending_uri"
        private const val PENDING_ROW_MISSING = -1
    }
}
