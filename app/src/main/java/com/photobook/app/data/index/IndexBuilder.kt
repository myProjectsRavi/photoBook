package com.photobook.app.data.index

import com.photobook.app.data.geo.OfflineGeocoder
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.data.model.RawPhotoData
import com.photobook.app.data.source.ExifExtractor
import com.photobook.app.data.source.MediaStoreScanner
import com.photobook.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class IndexBuilder @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
    private val exifExtractor: ExifExtractor,
    private val offlineGeocoder: OfflineGeocoder,
) {

    suspend fun buildIndex(onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }): List<PhotoRecord> {
        return buildIndexFromRaw(
            rawPhotos = mediaStoreScanner.scanAll(),
            onProgress = onProgress,
        )
    }

    suspend fun buildIndexFromRaw(
        rawPhotos: List<RawPhotoData>,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<PhotoRecord> {
        return withContext(Dispatchers.IO) {
            if (rawPhotos.isEmpty()) {
                return@withContext emptyList()
            }

            rawPhotos.mapIndexed { index, raw ->
                val record = buildRecord(raw)
                onProgress(index + 1, rawPhotos.size)
                record
            }
        }
    }

    private fun buildRecord(raw: RawPhotoData): PhotoRecord {
        val exif = exifExtractor.extract(raw.uriString, raw.filePath)
        val geo = if (exif.latitude != null && exif.longitude != null) {
            offlineGeocoder.reverseGeocode(exif.latitude, exif.longitude)
        } else {
            null
        }

        val dateParts = DateUtils.toDateParts(raw.dateAdded)
        return PhotoRecord(
            id = raw.id,
            uriString = raw.uriString,
            filePath = raw.filePath,
            fileName = raw.fileName,
            dateAdded = raw.dateAdded,
            year = dateParts.year,
            month = dateParts.month,
            dayOfMonth = dateParts.dayOfMonth,
            dayOfWeek = dateParts.dayOfWeekIso,
            hourOfDay = dateParts.hourOfDay,
            latitude = exif.latitude,
            longitude = exif.longitude,
            city = geo?.city,
            state = geo?.state,
            country = geo?.country,
            fileSize = raw.fileSize,
            width = raw.width,
            height = raw.height,
            mimeType = raw.mimeType,
            folderName = raw.folderName.lowercase(),
            folderPath = raw.folderPath.lowercase(),
            cameraModel = exif.cameraModel,
            isFrontCamera = exif.isFrontCamera,
            isHdr = exif.isHdr,
            isFavorite = false,
            isMlProcessed = false,
            mlStatus = IntelligenceStatus.PENDING,
            ocrText = "",
            isOcrProcessed = false,
            ocrStatus = IntelligenceStatus.PENDING,
        )
    }
}
