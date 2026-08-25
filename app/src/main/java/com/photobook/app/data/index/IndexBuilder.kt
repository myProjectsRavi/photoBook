package com.photobook.app.data.index

import android.os.SystemClock
import android.util.Log
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
        val scanStartMs = SystemClock.elapsedRealtime()
        val rawPhotos = mediaStoreScanner.scanAll()
        Log.i(
            PHASE4_TAG,
            "stage=media_store_scan elapsedMs=${SystemClock.elapsedRealtime() - scanStartMs} count=${rawPhotos.size}",
        )
        return buildIndexFromRaw(
            rawPhotos = rawPhotos,
            onProgress = onProgress,
        )
    }

    suspend fun buildIndexFromRaw(
        rawPhotos: List<RawPhotoData>,
        onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<PhotoRecord> {
        return withContext(Dispatchers.IO) {
            if (rawPhotos.isEmpty()) {
                Log.i(PHASE4_TAG, "stage=record_build elapsedMs=0 count=0")
                return@withContext emptyList()
            }

            var exifElapsedMs = 0L
            var geocodeElapsedMs = 0L
            var geocodeCount = 0
            val buildStartMs = SystemClock.elapsedRealtime()
            val records = rawPhotos.mapIndexed { index, raw ->
                val record = buildRecord(
                    raw = raw,
                    onExifMeasured = { elapsedMs -> exifElapsedMs += elapsedMs },
                    onGeocodeMeasured = { elapsedMs ->
                        geocodeElapsedMs += elapsedMs
                        geocodeCount += 1
                    },
                )
                onProgress(index + 1, rawPhotos.size)
                record
            }
            Log.i(
                PHASE4_TAG,
                "stage=record_build elapsedMs=${SystemClock.elapsedRealtime() - buildStartMs} " +
                    "count=${rawPhotos.size} exifElapsedMs=$exifElapsedMs " +
                    "geocodeElapsedMs=$geocodeElapsedMs geocodeCount=$geocodeCount",
            )
            records
        }
    }

    private fun buildRecord(
        raw: RawPhotoData,
        onExifMeasured: (Long) -> Unit,
        onGeocodeMeasured: (Long) -> Unit,
    ): PhotoRecord {
        val exifStartMs = SystemClock.elapsedRealtime()
        val exif = exifExtractor.extract(raw.uriString, raw.filePath)
        onExifMeasured(SystemClock.elapsedRealtime() - exifStartMs)

        val geo = if (exif.latitude != null && exif.longitude != null) {
            val geocodeStartMs = SystemClock.elapsedRealtime()
            offlineGeocoder.reverseGeocode(exif.latitude, exif.longitude).also {
                onGeocodeMeasured(SystemClock.elapsedRealtime() - geocodeStartMs)
            }
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

    companion object {
        private const val PHASE4_TAG = "PhotoBookPhase4"
    }
}
