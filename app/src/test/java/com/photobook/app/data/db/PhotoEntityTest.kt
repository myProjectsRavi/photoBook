package com.photobook.app.data.db

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.data.model.withRetainedStateFrom
import org.junit.Test

class PhotoEntityTest {

    @Test
    fun archiveFoodCandidate_roundTripsIndependentlyOfSearchTags() {
        val record = sampleRecord(
            isArchiveFoodCandidate = true,
            isMlProcessed = true,
        )

        val restored = record.toPhotoEntity().toPhotoRecord()

        assertThat(restored.mlTags).isEmpty()
        assertThat(restored.isArchiveFoodCandidate).isTrue()
        assertThat(restored.isMlProcessed).isTrue()
    }

    @Test
    fun archiveFoodCandidate_isClearedWhenMlIsNotProcessed() {
        val entity = sampleRecord(
            isArchiveFoodCandidate = true,
            isMlProcessed = false,
            mlStatus = IntelligenceStatus.PENDING,
        ).toPhotoEntity()

        assertThat(entity.isArchiveFoodCandidate).isFalse()
    }

    @Test
    fun sourceRevision_roundTripsWithRecord() {
        val restored = sampleRecord(sourceRevision = 42L).toPhotoEntity().toPhotoRecord()
        assertThat(restored.sourceRevision).isEqualTo(42L)
    }

    @Test
    fun changedRevision_retainsFavoriteButDropsDerivedIntelligence() {
        val current = sampleRecord(
            isMlProcessed = true,
            mlStatus = IntelligenceStatus.PROCESSED,
            sourceRevision = 41L,
        ).copy(
            isFavorite = true,
            perceptualHash = 123L,
            ocrText = "old bytes",
            isOcrProcessed = true,
            ocrStatus = IntelligenceStatus.PROCESSED,
        )
        val rebuilt = sampleRecord(sourceRevision = 42L).withRetainedStateFrom(current)

        assertThat(rebuilt.isFavorite).isTrue()
        assertThat(rebuilt.perceptualHash).isNull()
        assertThat(rebuilt.ocrText).isEmpty()
        assertThat(rebuilt.isOcrProcessed).isFalse()
        assertThat(rebuilt.isMlProcessed).isFalse()
    }

    private fun sampleRecord(
        mlTags: List<MLTag> = emptyList(),
        isArchiveFoodCandidate: Boolean = false,
        isMlProcessed: Boolean = false,
        mlStatus: IntelligenceStatus = if (isMlProcessed) {
            IntelligenceStatus.PROCESSED
        } else {
            IntelligenceStatus.PENDING
        },
        sourceRevision: Long = 1L,
    ): PhotoRecord {
        return PhotoRecord(
            id = 41L,
            uriString = "content://media/external/images/media/41",
            filePath = "/storage/emulated/0/DCIM/Camera/IMG_41.jpg",
            fileName = "IMG_41.jpg",
            dateAdded = 1_783_000_000_000L,
            year = 2026,
            month = 8,
            dayOfMonth = 1,
            dayOfWeek = 6,
            hourOfDay = 10,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 1_024L,
            width = 1_024,
            height = 768,
            mimeType = "image/jpeg",
            folderName = "Camera",
            folderPath = "DCIM/Camera",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            mlTags = mlTags,
            isArchiveFoodCandidate = isArchiveFoodCandidate,
            isMlProcessed = isMlProcessed,
            mlStatus = mlStatus,
            sourceRevision = sourceRevision,
        )
    }
}
