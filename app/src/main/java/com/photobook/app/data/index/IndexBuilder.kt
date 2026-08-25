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
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class IndexBuilder @Inject constructor(
    private val mediaStoreScanner: MediaStoreScanner,
    private val exifExtractor: ExifExtractor,
    private val offlineGeocoder: OfflineGeocoder,
) {

    suspend fun buildIndex(onProgress: (processed: Int, total: Int) -> Unit = { _, _ -> }): List<PhotoRecord> {
        val scanStartMs = SystemClock.elapsedRealtime()
        val rawPhotos = mediaStoreScanner.scanAll()
        val scanElapsedMs = SystemClock.elapsedRealtime() - scanStartMs
        Log.i(
            PHASE4_TAG,
            "stage=media_store_scan elapsedMs=$scanElapsedMs count=${rawPhotos.size}",
        )
        val records = buildIndexFromRaw(
            rawPhotos = rawPhotos,
            onProgress = onProgress,
        )
        // Replay the initial scan timing after record construction so long 50k/100k runs retain
        // this measurement even if Android's finite logcat buffer rotates the early marker out.
        Log.i(
            PHASE4_TAG,
            "stage=media_store_scan elapsedMs=$scanElapsedMs count=${rawPhotos.size} replay=1",
        )
        return records
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
            var processed = 0
            val records = ArrayList<PhotoRecord>(rawPhotos.size)
            val buildStartMs = SystemClock.elapsedRealtime()

            var startIndex = 0
            while (startIndex < rawPhotos.size) {
                val endIndex = (startIndex + RECORD_BUILD_PARALLELISM).coerceAtMost(rawPhotos.size)
                val batchResults = coroutineScope {
                    (startIndex until endIndex)
                        .map { index ->
                            async {
                                buildRecord(rawPhotos[index])
                            }
                        }
                        .awaitAll()
                }

                batchResults.forEach { result ->
                    records += result.record
                    exifElapsedMs += result.exifElapsedMs
                    geocodeElapsedMs += result.geocodeElapsedMs
                    if (result.didGeocode) {
                        geocodeCount += 1
                    }
                    processed += 1
                    onProgress(processed, rawPhotos.size)
                }
                startIndex = endIndex
            }

            Log.i(
                PHASE4_TAG,
                "stage=record_build elapsedMs=${SystemClock.elapsedRealtime() - buildStartMs} " +
                    "count=${rawPhotos.size} exifElapsedMs=$exifElapsedMs " +
                    "geocodeElapsedMs=$geocodeElapsedMs geocodeCount=$geocodeCount " +
                    "parallelism=$RECORD_BUILD_PARALLELISM",
            )
            records
        }
    }

    private fun buildRecord(raw: RawPhotoData): MeasuredPhotoRecord {
        val exifStartMs = SystemClock.elapsedRealtime()
        val exif = exifExtractor.extract(raw.uriString, raw.filePath)
        val exifElapsedMs = SystemClock.elapsedRealtime() - exifStartMs

        var geocodeElapsedMs = 0L
        val shouldGeocode = exif.latitude != null && exif.longitude != null
        val geo = if (shouldGeocode) {
            val geocodeStartMs = SystemClock.elapsedRealtime()
            offlineGeocoder.reverseGeocode(exif.latitude, exif.longitude).also {
                geocodeElapsedMs = SystemClock.elapsedRealtime() - geocodeStartMs
            }
        } else {
            null
        }

        val dateParts = DateUtils.toDateParts(raw.dateAdded)
        val record = PhotoRecord(
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
        return MeasuredPhotoRecord(
            record = record,
            exifElapsedMs = exifElapsedMs,
            geocodeElapsedMs = geocodeElapsedMs,
            didGeocode = shouldGeocode,
        )
    }

    private data class MeasuredPhotoRecord(
        val record: PhotoRecord,
        val exifElapsedMs: Long,
        val geocodeElapsedMs: Long,
        val didGeocode: Boolean,
    )

    companion object {
        private const val RECORD_BUILD_PARALLELISM = 2
        private const val PHASE4_TAG = "PhotoBookPhase4"
    }
}
