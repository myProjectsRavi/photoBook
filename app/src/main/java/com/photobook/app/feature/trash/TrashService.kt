package com.photobook.app.feature.trash

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface TrashRequestResult {
    data class Ready(val intentSender: IntentSender) : TrashRequestResult
    data object UnsupportedAndroid : TrashRequestResult
    data class Error(val throwable: Throwable? = null) : TrashRequestResult
}

data class TrashedPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val dateTrashedMillis: Long,
)

class TrashService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun createTrashRequest(photos: List<PhotoRecord>): TrashRequestResult {
        if (photos.isEmpty()) return TrashRequestResult.Error()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return TrashRequestResult.UnsupportedAndroid
        }

        val uris = photos.asSequence()
            .mapNotNull { record ->
                runCatching { Uri.parse(record.uriString) }.getOrNull()
            }
            .distinct()
            .toList()
        if (uris.isEmpty()) return TrashRequestResult.Error()

        return runCatching {
            val pendingIntent = MediaStore.createTrashRequest(
                context.contentResolver,
                uris,
                true,
            )
            TrashRequestResult.Ready(intentSender = pendingIntent.intentSender)
        }.getOrElse { error ->
            TrashRequestResult.Error(error)
        }
    }

    /** Lists media that is currently in the trash (Android 11+). Empty list on older OS. */
    suspend fun listTrashed(limit: Int = 200): List<TrashedPhoto> = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return@withContext emptyList()

        runCatching {
            val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_EXPIRES,
            )
            val queryArgs = Bundle().apply {
                putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
                putString(
                    android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.Images.Media.DATE_EXPIRES} DESC",
                )
                putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, limit)
            }

            val results = mutableListOf<TrashedPhoto>()
            context.contentResolver.query(collection, projection, queryArgs, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val expiresCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_EXPIRES)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    results += TrashedPhoto(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = cursor.getString(nameCol).orEmpty(),
                        mimeType = cursor.getString(mimeCol).orEmpty().ifBlank { "image/*" },
                        sizeBytes = cursor.getLong(sizeCol),
                        dateTrashedMillis = cursor.getLong(expiresCol) * 1000L,
                    )
                }
            }
            results
        }.getOrDefault(emptyList())
    }

    /** Creates a system-managed PendingIntent to restore trashed items. */
    fun createRestoreRequest(uris: List<Uri>): TrashRequestResult {
        if (uris.isEmpty()) return TrashRequestResult.Error()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return TrashRequestResult.UnsupportedAndroid
        return runCatching {
            val pi = MediaStore.createTrashRequest(context.contentResolver, uris, false)
            TrashRequestResult.Ready(pi.intentSender)
        }.getOrElse { TrashRequestResult.Error(it) }
    }

    /** Creates a system-managed PendingIntent to permanently delete items. */
    fun createDeleteRequest(uris: List<Uri>): TrashRequestResult {
        if (uris.isEmpty()) return TrashRequestResult.Error()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return TrashRequestResult.UnsupportedAndroid
        return runCatching {
            val pi = MediaStore.createDeleteRequest(context.contentResolver, uris)
            TrashRequestResult.Ready(pi.intentSender)
        }.getOrElse { TrashRequestResult.Error(it) }
    }
}
