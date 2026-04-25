package com.photobook.app.feature.duplicates

import com.photobook.app.data.model.PhotoRecord

enum class DuplicateMatchKind {
    Exact,
    Similar,
    Burst,
    Blurry,
}

data class DuplicatePhotoGroup(
    val id: String,
    val kind: DuplicateMatchKind,
    val photos: List<PhotoRecord>,
) {
    val totalBytes: Long = photos.sumOf { it.fileSize }
}
