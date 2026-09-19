package com.photobook.app.feature.qrshare

import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class QrReceivedImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun saveToDevice(
        bytes: ByteArray,
        preferredFileName: String,
        mimeType: String,
    ): Uri? {
        return withContext(Dispatchers.IO) {
            if (bytes.isEmpty() || bytes.size > QrTransferProtocol.MAX_TRANSFER_BYTES) {
                return@withContext null
            }
            val normalizedMime = mimeType.trim().lowercase()
            if (normalizedMime !in SUPPORTED_MIME_TYPES || !isImage(bytes)) {
                return@withContext null
            }

            val safeName = buildFileName(preferredFileName, normalizedMime)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return@withContext saveWithMediaStore(bytes, safeName, normalizedMime)
            }
            saveLegacy(bytes, safeName, normalizedMime)
        }
    }

    private suspend fun saveWithMediaStore(
        bytes: ByteArray,
        fileName: String,
        mimeType: String,
    ): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PhotoBook")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        currentCoroutineContext().ensureActive()
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        var committed = false
        try {
            currentCoroutineContext().ensureActive()
            val output = resolver.openOutputStream(uri)
                ?: return null
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }

            currentCoroutineContext().ensureActive()
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            if (resolver.update(uri, values, null, null) <= 0) {
                return null
            }

            currentCoroutineContext().ensureActive()
            if (!verifyPublishedBytes(uri, bytes)) {
                return null
            }

            committed = true
            return uri
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        } finally {
            if (!committed) {
                runCatching { resolver.delete(uri, null, null) }
            }
        }
    }

    private suspend fun saveLegacy(bytes: ByteArray, fileName: String, mimeType: String): Uri? {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val targetDir = File(picturesDir, "PhotoBook")
        if (!targetDir.exists() && !targetDir.mkdirs()) return null

        val target = uniqueTarget(File(targetDir, fileName))
        var committed = false
        try {
            currentCoroutineContext().ensureActive()
            FileOutputStream(target).use { output ->
                output.write(bytes)
                output.flush()
            }
            currentCoroutineContext().ensureActive()
            MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf(mimeType),
                null,
            )
            committed = true
            return Uri.fromFile(target)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            return null
        } finally {
            if (!committed) {
                runCatching { target.delete() }
            }
        }
    }

    private fun verifyPublishedBytes(uri: Uri, expectedBytes: ByteArray): Boolean {
        val input = context.contentResolver.openInputStream(uri) ?: return false
        val expectedDigest = MessageDigest.getInstance("SHA-256").digest(expectedBytes)
        val actualDigest = sha256(input)
        return MessageDigest.isEqual(expectedDigest, actualDigest)
    }

    private fun sha256(input: InputStream): ByteArray {
        return input.use { stream ->
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(VERIFY_BUFFER_BYTES)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
            digest.digest()
        }
    }

    private fun buildFileName(input: String, mimeType: String): String {
        val base = input
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .trim('.', ' ')
            .take(100)
            .ifBlank { "PhotoBook_Received_${System.currentTimeMillis()}" }
        val withoutExtension = base.substringBeforeLast('.', base)
        return "$withoutExtension.${extensionFor(mimeType)}"
    }

    private fun uniqueTarget(initial: File): File {
        if (!initial.exists()) return initial
        return File(
            initial.parentFile,
            "${initial.nameWithoutExtension}_${System.currentTimeMillis()}.${initial.extension}",
        )
    }

    private fun isImage(bytes: ByteArray): Boolean {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        return options.outWidth > 0 && options.outHeight > 0
    }

    private fun extensionFor(mimeType: String): String = when (mimeType) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    companion object {
        private const val VERIFY_BUFFER_BYTES = 8 * 1024
        private val SUPPORTED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
    }
}
