package com.photobook.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class MLTag(
    val label: String,
    val confidence: Float,
)
