package com.photobook.app.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class PhotoRecord(
    val id: Long,
    val uriString: String,
    val filePath: String,
    val fileName: String,
    val dateAdded: Long,
    val year: Int,
    val month: Int,
    val dayOfMonth: Int,
    val dayOfWeek: Int,
    val hourOfDay: Int,
    val latitude: Double?,
    val longitude: Double?,
    val city: String?,
    val state: String?,
    val country: String?,
    val fileSize: Long,
    val width: Int,
    val height: Int,
    val mimeType: String,
    val folderName: String,
    val folderPath: String,
    val cameraModel: String?,
    val isFrontCamera: Boolean,
    val isHdr: Boolean,
    val isFavorite: Boolean = false,
    val perceptualHash: Long? = null,
    val blurScore: Double? = null,
    val mlTags: List<MLTag> = emptyList(),
    val isArchiveFoodCandidate: Boolean = false,
    val isMlProcessed: Boolean = false,
    val mlStatus: IntelligenceStatus = if (isMlProcessed) {
        IntelligenceStatus.PROCESSED
    } else {
        IntelligenceStatus.PENDING
    },
    val ocrText: String = "",
    val isOcrProcessed: Boolean = false,
    val ocrStatus: IntelligenceStatus = if (isOcrProcessed) {
        IntelligenceStatus.PROCESSED
    } else {
        IntelligenceStatus.PENDING
    },
    val sourceRevision: Long = -1L,
) {
    val aspectRatio: Float
        get() = if (height == 0) 1f else width.toFloat() / height.toFloat()

    fun hasMlTag(keyword: String, threshold: Float): Boolean {
        return mlTags.any {
            it.confidence >= threshold && it.label.contains(keyword, ignoreCase = true)
        }
    }

    fun hasOcrToken(keyword: String): Boolean {
        if (keyword.isBlank()) return false
        return ocrText.contains(keyword, ignoreCase = true)
    }
}


fun PhotoRecord.withRetainedStateFrom(current: PhotoRecord?): PhotoRecord {
    if (current == null) return this
    val sameIdentity =
        id == current.id &&
            uriString == current.uriString &&
            dateAdded == current.dateAdded
    if (!sameIdentity) return this

    val userState = copy(isFavorite = current.isFavorite)
    val sameContent =
        sourceRevision > 0L &&
            current.sourceRevision > 0L &&
            sourceRevision == current.sourceRevision
    if (!sameContent) return userState

    return userState.copy(
        perceptualHash = current.perceptualHash,
        blurScore = current.blurScore,
        mlTags = current.mlTags,
        isArchiveFoodCandidate = current.isArchiveFoodCandidate,
        isMlProcessed = current.isMlProcessed,
        mlStatus = current.mlStatus,
        ocrText = current.ocrText,
        isOcrProcessed = current.isOcrProcessed,
        ocrStatus = current.ocrStatus,
    )
}
