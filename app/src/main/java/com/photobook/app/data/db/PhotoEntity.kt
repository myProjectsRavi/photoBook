package com.photobook.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.PhotoRecord

@Entity(
    tableName = "photos",
    indices = [
        Index(value = ["dateAdded"]),
        Index(value = ["isFavorite", "dateAdded"]),
        Index(value = ["folderName"]),
        Index(value = ["city", "state", "country"]),
        Index(value = ["fileSize", "width", "height"]),
        Index(value = ["isArchiveScreenshotCandidate", "dateAdded"]),
        Index(value = ["isArchiveFoodCandidate", "dateAdded"]),
    ],
)
data class PhotoEntity(
    @PrimaryKey
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
    val isFavorite: Boolean,
    val perceptualHash: Long?,
    val blurScore: Double?,
    val mlTagsPayload: String,
    val isMlProcessed: Boolean,
    val mlStatus: String,
    val ocrText: String,
    val isOcrProcessed: Boolean,
    val ocrStatus: String,
    val isArchiveScreenshotCandidate: Boolean,
    val isArchiveFoodCandidate: Boolean,
)

fun PhotoEntity.toPhotoRecord(): PhotoRecord {
    return PhotoRecord(
        id = id,
        uriString = uriString,
        filePath = filePath,
        fileName = fileName,
        dateAdded = dateAdded,
        year = year,
        month = month,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeek,
        hourOfDay = hourOfDay,
        latitude = latitude,
        longitude = longitude,
        city = city,
        state = state,
        country = country,
        fileSize = fileSize,
        width = width,
        height = height,
        mimeType = mimeType,
        folderName = folderName,
        folderPath = folderPath,
        cameraModel = cameraModel,
        isFrontCamera = isFrontCamera,
        isHdr = isHdr,
        isFavorite = isFavorite,
        perceptualHash = perceptualHash,
        blurScore = blurScore,
        mlTags = PhotoTagCodec.decode(mlTagsPayload),
        isMlProcessed = isMlProcessed,
        mlStatus = IntelligenceStatus.fromStored(mlStatus, isMlProcessed),
        ocrText = ocrText,
        isOcrProcessed = isOcrProcessed,
        ocrStatus = IntelligenceStatus.fromStored(ocrStatus, isOcrProcessed),
    )
}

fun PhotoRecord.toPhotoEntity(): PhotoEntity {
    return PhotoEntity(
        id = id,
        uriString = uriString,
        filePath = filePath,
        fileName = fileName,
        dateAdded = dateAdded,
        year = year,
        month = month,
        dayOfMonth = dayOfMonth,
        dayOfWeek = dayOfWeek,
        hourOfDay = hourOfDay,
        latitude = latitude,
        longitude = longitude,
        city = city,
        state = state,
        country = country,
        fileSize = fileSize,
        width = width,
        height = height,
        mimeType = mimeType,
        folderName = folderName,
        folderPath = folderPath,
        cameraModel = cameraModel,
        isFrontCamera = isFrontCamera,
        isHdr = isHdr,
        isFavorite = isFavorite,
        perceptualHash = perceptualHash,
        blurScore = blurScore,
        mlTagsPayload = PhotoTagCodec.encode(mlTags),
        isMlProcessed = isMlProcessed,
        mlStatus = mlStatus.name,
        ocrText = ocrText,
        isOcrProcessed = isOcrProcessed,
        ocrStatus = ocrStatus.name,
        isArchiveScreenshotCandidate = buildString {
            append(folderName)
            append(' ')
            append(folderPath)
            append(' ')
            append(filePath)
            append(' ')
            append(fileName)
        }.containsScreenshotCue(),
        isArchiveFoodCandidate = mlTags.any { tag ->
            tag.label.equals("food", ignoreCase = true)
        },
    )
}

private fun String.containsScreenshotCue(): Boolean {
    val normalized = lowercase()
    return normalized.contains("screenshot") ||
        normalized.contains("screen_shot") ||
        normalized.contains("screen-shot")
}
