package com.photobook.app.feature.trash

import android.content.ContentUris
import android.content.ContentResolver
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

sealed interface TrashListResult {
    data class Success(
        val photos: List<TrashedPhoto>,
        val nextOffset: Int? = null,
    ) : TrashListResult
    data object UnsupportedAndroid : TrashListResult
    data class Error(val throwable: Throwable? = null) : TrashListResult
}

enum class TrashListUiState {
    READY,
    UNSUPPORTED,
    ERROR,
}

fun TrashListResult.toTrashListUiState(): TrashListUiState = when (this) {
    is TrashListResult.Success -> TrashListUiState.READY
    TrashListResult.UnsupportedAndroid -> TrashListUiState.UNSUPPORTED
    is TrashListResult.Error -> TrashListUiState.ERROR
}

internal fun nextTrashOffset(
    offset: Int,
    pageSize: Int,
    fetchedCount: Int,
): Int? = if (fetchedCount > pageSize) offset + pageSize else null

data class TrashedPhoto(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val expiresAtMillis: Long?,
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

    /** Lists all media currently in the trash (Android 11+), preserving provider failures. */
    suspend fun listTrashed(
        offset: Int = 0,
        pageSize: Int = DEFAULT_TRASH_PAGE_SIZE,
    ): TrashListResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return@withContext TrashListResult.UnsupportedAndroid
        }

        runCatching {
            require(offset >= 0) { "Trash offset must be non-negative" }
            val boundedPageSize = pageSize.coerceIn(1, MAX_TRASH_PAGE_SIZE)
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
                putInt(ContentResolver.QUERY_ARG_LIMIT, boundedPageSize + 1)
                putInt(ContentResolver.QUERY_ARG_OFFSET, offset)
                putString(
                    ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                    "${MediaStore.Images.Media.DATE_EXPIRES} DESC, ${MediaStore.Images.Media._ID} DESC",
                )
            }

            val results = mutableListOf<TrashedPhoto>()
            val cursor = context.contentResolver.query(collection, projection, queryArgs, null)
                ?: error("MediaStore trash query returned no cursor")
            cursor.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val expiresCol = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_EXPIRES)
                while (it.moveToNext() && results.size <= boundedPageSize) {
                    val id = it.getLong(idCol)
                    val expiresSeconds = if (it.isNull(expiresCol)) null else it.getLong(expiresCol)
                    results += TrashedPhoto(
                        id = id,
                        uri = ContentUris.withAppendedId(collection, id),
                        displayName = it.getString(nameCol).orEmpty(),
                        mimeType = it.getString(mimeCol).orEmpty().ifBlank { "image/*" },
                        sizeBytes = it.getLong(sizeCol),
                        expiresAtMillis = expiresSeconds?.takeIf { value -> value > 0L }?.times(1000L),
                    )
                }
            }
            TrashListResult.Success(
                photos = results.take(boundedPageSize),
                nextOffset = nextTrashOffset(
                    offset = offset,
                    pageSize = boundedPageSize,
                    fetchedCount = results.size,
                ),
            )
        }.getOrElse { TrashListResult.Error(it) }
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

    private companion object {
        const val DEFAULT_TRASH_PAGE_SIZE = 120
        const val MAX_TRASH_PAGE_SIZE = 200
    }
}
