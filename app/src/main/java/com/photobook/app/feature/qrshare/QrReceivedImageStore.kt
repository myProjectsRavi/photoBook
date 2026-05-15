package com.photobook.app.feature.qrshare

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

class QrReceivedImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    suspend fun saveToDevice(
        bytes: ByteArray,
        preferredFileName: String,
        mimeType: String,
    ): Uri? {
        return withContext(Dispatchers.IO) {
            if (bytes.isEmpty()) return@withContext null

            val safeName = buildFileName(preferredFileName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                return@withContext saveWithMediaStore(bytes, safeName, mimeType)
            }
            saveLegacy(bytes, safeName, mimeType)
        }
    }

    private fun saveWithMediaStore(bytes: ByteArray, fileName: String, mimeType: String): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PhotoBook")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return null
        return runCatching {
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: return null

            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        }.getOrElse {
            context.contentResolver.delete(uri, null, null)
            null
        }
    }

    private fun saveLegacy(bytes: ByteArray, fileName: String, mimeType: String): Uri? {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val targetDir = File(picturesDir, "PhotoBook")
        if (!targetDir.exists() && !targetDir.mkdirs()) return null

        val target = File(targetDir, fileName)
        return runCatching {
            FileOutputStream(target).use { output ->
                output.write(bytes)
                output.flush()
            }
            MediaScannerConnection.scanFile(
                context,
                arrayOf(target.absolutePath),
                arrayOf(mimeType),
                null,
            )
            Uri.fromFile(target)
        }.getOrNull()
    }

    private fun buildFileName(input: String): String {
        val base = input
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .ifBlank { "PhotoBook_Received_${System.currentTimeMillis()}.jpg" }
        return if (base.endsWith(".jpg", ignoreCase = true) || base.endsWith(".jpeg", ignoreCase = true)) {
            base
        } else {
            "$base.jpg"
        }
    }
}
