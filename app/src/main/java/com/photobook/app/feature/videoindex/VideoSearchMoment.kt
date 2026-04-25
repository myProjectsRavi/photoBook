package com.photobook.app.feature.videoindex

import androidx.compose.runtime.Immutable

@Immutable
data class VideoSearchMoment(
    val videoUriString: String,
    val displayName: String,
    val timestampMs: Long,
    val durationMs: Long,
    val mimeType: String,
    val previewText: String,
)
