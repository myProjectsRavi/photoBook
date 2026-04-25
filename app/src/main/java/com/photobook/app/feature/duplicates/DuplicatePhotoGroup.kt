package com.photobook.app.feature.duplicates

import androidx.compose.runtime.Immutable
import com.photobook.app.data.model.PhotoRecord

@Immutable
enum class DuplicateMatchKind {
    Exact,
    Similar,
    Burst,
    Blurry,
}

@Immutable
data class DuplicatePhotoGroup(
    val id: String,
    val kind: DuplicateMatchKind,
    val photos: List<PhotoRecord>,
    val heroPhotoId: Long? = null,
) {
    val totalBytes: Long = photos.sumOf { it.fileSize }
}
