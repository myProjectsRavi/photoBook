package com.photobook.app.search

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.PhotoRecord
import org.junit.Test

class OcrIndexReadinessTest {

    @Test
    fun processableStates_keepTextIndexInProgress() {
        listOf(
            IntelligenceStatus.PENDING,
            IntelligenceStatus.MODEL_PREPARING,
            IntelligenceStatus.FAILED_RETRYABLE,
        ).forEach { status ->
            assertThat(OcrIndexReadiness.hasProcessableWork(listOf(photo(status)))).isTrue()
        }
    }

    @Test
    fun terminalStates_doNotPretendBackgroundWorkIsStillRunning() {
        listOf(
            IntelligenceStatus.PROCESSED,
            IntelligenceStatus.FAILED_PERMANENT,
        ).forEach { status ->
            assertThat(OcrIndexReadiness.hasProcessableWork(listOf(photo(status)))).isFalse()
        }
        assertThat(OcrIndexReadiness.hasProcessableWork(emptyList())).isFalse()
    }

    @Test
    fun onePendingPhoto_keepsMixedLibraryIncomplete() {
        val records = listOf(
            photo(IntelligenceStatus.PROCESSED, 1L),
            photo(IntelligenceStatus.FAILED_PERMANENT, 2L),
            photo(IntelligenceStatus.PENDING, 3L),
        )

        assertThat(OcrIndexReadiness.hasProcessableWork(records)).isTrue()
    }

    private fun photo(status: IntelligenceStatus, id: Long = 1L): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://ocr-readiness/$id",
            filePath = "/Pictures/$id.jpg",
            fileName = "$id.jpg",
            dateAdded = 1L,
            year = 2026,
            month = 9,
            dayOfMonth = 1,
            dayOfWeek = 2,
            hourOfDay = 12,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 1L,
            width = 100,
            height = 100,
            mimeType = "image/jpeg",
            folderName = "Pictures",
            folderPath = "/Pictures",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            isOcrProcessed = status == IntelligenceStatus.PROCESSED,
            ocrStatus = status,
        )
    }
}
