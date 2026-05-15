package com.photobook.app.ui.model

import androidx.compose.runtime.Immutable

@Immutable
data class TimelineMark(
    val index: Int,
    val label: String,
)
