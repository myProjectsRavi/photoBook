package com.photobook.app.data.source

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.photobook.app.data.model.RawPhotoData
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class MediaStoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Suppress("DEPRECATION")
    fun scanAll(): List<RawPhotoData> {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATA,
        )

        val photos = mutableListOf<RawPhotoData>()
        context.contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_ADDED} DESC",
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateAddedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
            val bucketNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val relativePathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH)
            val dataPathIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIndex)
                val displayName = cursor.getString(displayNameIndex).orEmpty()
                val uriString = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                ).toString()
                val relativePath = if (relativePathIndex >= 0) cursor.getString(relativePathIndex).orEmpty() else ""
                val dataPath = if (dataPathIndex >= 0) cursor.getString(dataPathIndex).orEmpty() else ""
                val filePath = when {
                    dataPath.isNotBlank() -> dataPath
                    relativePath.isNotBlank() -> "$relativePath$displayName"
                    else -> displayName
                }
                val bucketName = if (bucketNameIndex >= 0) cursor.getString(bucketNameIndex).orEmpty() else ""
                val folderName = bucketName.ifBlank {
                    relativePath
                        .split('/')
                        .filter { it.isNotBlank() }
                        .lastOrNull()
                        .orEmpty()
                }
                val folderPath = if (relativePath.isNotBlank()) relativePath else filePath.substringBeforeLast('/')

                val dateSeconds = cursor.getLong(dateAddedIndex)
                val dateMillis = if (dateSeconds < 10_000_000_000L) {
                    dateSeconds * 1000L
                } else {
                    dateSeconds
                }

                photos += RawPhotoData(
                    id = id,
                    uriString = uriString,
                    filePath = filePath,
                    fileName = displayName,
                    dateAdded = dateMillis,
                    fileSize = cursor.getLong(sizeIndex),
                    width = cursor.getInt(widthIndex),
                    height = cursor.getInt(heightIndex),
                    mimeType = cursor.getString(mimeIndex).orEmpty().lowercase(),
                    folderName = folderName,
                    folderPath = folderPath,
                )
            }
        }
        return photos
    }
}
