package com.photobook.app.feature.memories

import androidx.compose.runtime.Immutable

@Immutable
data class MemoryStory(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUriString: String,
    val photoCount: Int,
    val suggestedQuery: String,
)
