package com.photobook.app.feature.memories

data class MemoryStory(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUriString: String,
    val photoCount: Int,
    val suggestedQuery: String,
)
