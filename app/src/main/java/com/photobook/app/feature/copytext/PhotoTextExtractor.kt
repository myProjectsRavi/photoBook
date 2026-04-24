package com.photobook.app.feature.copytext

interface PhotoTextExtractor {
    suspend fun extract(photoUri: String): ExtractedTextResult
}
