package com.photobook.app.data.index

import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoIndexOrderingTest {

    @Test
    fun setRecords_preservesAlreadyDescendingStableOrderForBothBackends() = runBlocking {
        val records = listOf(
            record(id = 1L, dateAdded = 300L),
            record(id = 2L, dateAdded = 200L),
            record(id = 3L, dateAdded = 200L),
            record(id = 4L, dateAdded = 100L),
        )

        listOf(PhotoIndexStrategy.LEGACY, PhotoIndexStrategy.V2).forEach { strategy ->
            val index = PhotoIndex(strategy)
            index.setRecords(records)
            assertEquals(listOf(1L, 2L, 3L, 4L), index.snapshot().map { it.id })
        }
    }

    @Test
    fun setRecords_fallsBackToHistoricalStableSortWhenInputIsUnsorted() = runBlocking {
        val records = listOf(
            record(id = 4L, dateAdded = 100L),
            record(id = 2L, dateAdded = 200L),
            record(id = 1L, dateAdded = 300L),
            record(id = 3L, dateAdded = 200L),
        )

        listOf(PhotoIndexStrategy.LEGACY, PhotoIndexStrategy.V2).forEach { strategy ->
            val index = PhotoIndex(strategy)
            index.setRecords(records)
            // Kotlin's sortedByDescending is stable, so equal-date records must retain input order.
            assertEquals(listOf(1L, 2L, 3L, 4L), index.snapshot().map { it.id })
        }
    }

    private fun record(id: Long, dateAdded: Long): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://phase6/$id",
            filePath = "/storage/emulated/0/Pictures/$id.jpg",
            fileName = "$id.jpg",
            dateAdded = dateAdded,
            year = 2026,
            month = 8,
            dayOfMonth = 26,
            dayOfWeek = 4,
            hourOfDay = 12,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 1_024L,
            width = 100,
            height = 100,
            mimeType = "image/jpeg",
            folderName = "Pictures",
            folderPath = "/Pictures",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            isFavorite = false,
        )
    }
}
