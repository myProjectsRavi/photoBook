package com.photobook.app.feature.videoindex

import androidx.room.withTransaction
import com.photobook.app.data.db.PhotoBookDatabase
import com.photobook.app.data.db.PhotoDao
import com.photobook.app.data.db.VideoFrameEntity
import com.photobook.app.data.db.toFtsEntity
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
                val staleIds = photoDao.getVideoFrameIdsForVideo(videoUriString)
                if (staleIds.isNotEmpty()) {
                    photoDao.deleteVideoFrameFtsByRowIds(staleIds)
                }
                photoDao.deleteVideoFramesForVideo(videoUriString)
                if (frames.isNotEmpty()) {
                    photoDao.upsertVideoFrames(frames)
                    val persisted = photoDao.getVideoFramesForVideo(videoUriString)
                    if (persisted.isNotEmpty()) {
                        photoDao.upsertVideoFrameFtsRows(persisted.map { frame -> frame.toFtsEntity() })
                    }
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
                            val staleIds = photoDao.getVideoFrameIdsForVideo(uri)
                            if (staleIds.isNotEmpty()) {
                                photoDao.deleteVideoFrameFtsByRowIds(staleIds)
                            }
                            photoDao.deleteVideoFramesForVideo(uri)
                        }
                    }
                }
            } else {
                database.withTransaction {
                    val staleIds = photoDao.getVideoFrameIdsNotIn(validVideoUris.toList())
                    if (staleIds.isNotEmpty()) {
                        photoDao.deleteVideoFrameFtsByRowIds(staleIds)
                    }
                    photoDao.deleteVideoFramesNotIn(validVideoUris.toList())
                }
            }
        }
    }

    suspend fun searchMoments(query: String, limit: Int = 24): List<VideoSearchMoment> {
        val normalized = query.lowercase().trim()
        if (normalized.length < 2) return emptyList()
        val matchQuery = toFtsMatchQuery(normalized) ?: return emptyList()

        return withContext(Dispatchers.IO) {
            val ids = runCatching {
                photoDao.searchVideoFrameIdsByText(matchQuery, limit)
            }.getOrDefault(emptyList())
            if (ids.isEmpty()) {
                return@withContext emptyList()
            }
            val entities = photoDao.getVideoFramesByIds(ids)
            if (entities.isEmpty()) {
                return@withContext emptyList()
            }
            val byId = entities.associateBy { entity -> entity.id }
            ids.mapNotNull { id -> byId[id]?.toVideoSearchMoment(normalized) }
        }
    }

    private fun toFtsMatchQuery(rawQuery: String): String? {
        val tokens = rawQuery
            .split(Regex("[^\\p{L}\\p{N}_]+"))
            .map { token -> token.trim() }
            .filter { token -> token.length >= 2 }
            .map(::sanitizeToken)
            .filter { token -> token.length >= 2 }
            .distinct()
            .take(MAX_QUERY_TOKENS)
        if (tokens.isEmpty()) return null
        return tokens.joinToString(" AND ") { token -> "$token*" }
    }

    private fun sanitizeToken(token: String): String {
        return token.replace("\"", "")
            .replace("'", "")
            .replace("`", "")
    }

    companion object {
        private const val MAX_QUERY_TOKENS = 8
    }
}
