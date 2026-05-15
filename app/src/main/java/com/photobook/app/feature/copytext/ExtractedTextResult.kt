package com.photobook.app.feature.copytext

sealed interface ExtractedTextResult {
    data class Success(val text: String) : ExtractedTextResult
    data object Empty : ExtractedTextResult
    data class Error(val throwable: Throwable? = null) : ExtractedTextResult
}
