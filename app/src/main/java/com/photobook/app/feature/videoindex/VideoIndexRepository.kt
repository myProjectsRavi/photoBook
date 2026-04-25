package com.photobook.app.feature.videoindex

import androidx.room.withTransaction
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.db.VideoFrameEntity
import com.photobook.app.data.db.toVideoSearchMoment
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoIndexRepository @Inject constructor(
    private val database: PhotoBookDatabase,
    private val photoDao: PhotoDao,
) {

    suspend fun replaceFramesForVideo(videoUriString: String, frames: List<VideoFrameEntity>) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                photoDao.deleteVideoFramesForVideo(videoUriString)
                if (frames.isNotEmpty()) {
                    photoDao.upsertVideoFrames(frames)
                }
            }
        }
    }

    suspend fun pruneMissingVideos(validVideoUris: Set<String>) {
        withContext(Dispatchers.IO) {
            if (validVideoUris.isEmpty()) {
                val existing = photoDao.getIndexedVideoUris()
                if (existing.isNotEmpty()) {
                    database.withTransaction {
                        existing.forEach { uri ->
                            photoDao.deleteVideoFramesForVideo(uri)
                        }
                    }
                }
            } else {
                photoDao.deleteVideoFramesNotIn(validVideoUris.toList())
            }
        }
    }

    suspend fun searchMoments(query: String, limit: Int = 24): List<VideoSearchMoment> {
        val normalized = query.lowercase().trim()
        if (normalized.length < 2) return emptyList()

        return withContext(Dispatchers.IO) {
            photoDao.searchVideoFrames(normalized, limit)
                .map { entity -> entity.toVideoSearchMoment(normalized) }
        }
    }
}
