package com.photobook.app.feature.trash

import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.photobook.app.data.model.PhotoRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

sealed interface TrashRequestResult {
    data class Ready(val intentSender: IntentSender) : TrashRequestResult
    data object UnsupportedAndroid : TrashRequestResult
    data class Error(val throwable: Throwable? = null) : TrashRequestResult
}

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
}
