package com.photobook.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.photobook.app.feature.videoindex.VideoSearchMoment

@Entity(
    tableName = "video_frames",
    indices = [
        Index(value = ["videoUriString", "timestampMs"], unique = true),
        Index(value = ["videoDateModifiedMs"]),
    ],
)
data class VideoFrameEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoUriString: String,
    val displayName: String,
    val timestampMs: Long,
    val durationMs: Long,
    val videoDateModifiedMs: Long,
    val mimeType: String,
    val searchableText: String,
)

fun VideoFrameEntity.toVideoSearchMoment(query: String): VideoSearchMoment {
    val sanitized = query.lowercase().trim()
    val preview = searchableText
        .replace(Regex("\\s+"), " ")
        .trim()
        .let { text ->
            if (sanitized.isBlank()) {
                text.take(PREVIEW_MAX_CHARS)
            } else {
                val idx = text.indexOf(sanitized, ignoreCase = true)
                if (idx < 0) {
                    text.take(PREVIEW_MAX_CHARS)
                } else {
                    val start = (idx - PREVIEW_CONTEXT).coerceAtLeast(0)
                    val end = (idx + sanitized.length + PREVIEW_CONTEXT).coerceAtMost(text.length)
                    text.substring(start, end)
                }
            }
        }

    return VideoSearchMoment(
        videoUriString = videoUriString,
        displayName = displayName,
        timestampMs = timestampMs,
        durationMs = durationMs,
        mimeType = mimeType,
        previewText = preview,
    )
}

private const val PREVIEW_CONTEXT = 28
private const val PREVIEW_MAX_CHARS = 90
