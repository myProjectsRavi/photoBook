package com.photobook.app.feature.copytext

interface PhotoTextExtractor {
    suspend fun extract(photoUri: String): ExtractedTextResult

    suspend fun extractRegion(
        photoUri: String,
        region: NormalizedTextRegion,
    ): ExtractedTextResult {
        return extract(photoUri)
    }
}
