package com.photobook.app.search

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FilterEngineTest {

    @Test
    fun compoundQuery_usesAndLogic() = runTest {
        val index = PhotoIndex()
        val records = listOf(
            samplePhoto(
                id = 1,
                year = 2024,
                folderPath = "/storage/emulated/0/DCIM/Camera",
                folderName = "camera",
            ),
            samplePhoto(
                id = 2,
                year = 2024,
                folderPath = "/storage/emulated/0/Pictures/Screenshots",
                folderName = "screenshots",
            ),
            samplePhoto(
                id = 3,
                year = 2023,
                folderPath = "/storage/emulated/0/DCIM/Camera",
                folderName = "camera",
            ),
        )
        index.setRecords(records)

        val engine = FilterEngine(
            queryParser = QueryParser(),
            tokenClassifier = TokenClassifier(index),
            filterFactory = FilterFactory(),
        )

        val result = engine.search("2024 camera", records)

        assertThat(result.results).hasSize(1)
        assertThat(result.results.first().id).isEqualTo(1L)
    }

    private fun samplePhoto(
        id: Long,
        year: Int,
        folderPath: String,
        folderName: String,
    ): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://$id",
            filePath = "$folderPath/$id.jpg",
            fileName = "$id.jpg",
            dateAdded = System.currentTimeMillis(),
            year = year,
            month = 1,
            dayOfMonth = 1,
            dayOfWeek = 1,
            hourOfDay = 10,
            latitude = null,
            longitude = null,
            city = null,
            state = null,
            country = null,
            fileSize = 1024L,
            width = 1000,
            height = 1000,
            mimeType = "image/jpeg",
            folderName = folderName,
            folderPath = folderPath.lowercase(),
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            mlTags = emptyList(),
        )
    }
}
