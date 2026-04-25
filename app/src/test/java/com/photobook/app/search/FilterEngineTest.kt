package com.photobook.app.search

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.MLTag
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

    @Test
    fun twentyQueries_returnStableResultsWithoutCrashing() = runTest {
        val index = PhotoIndex()
        val records = listOf(
            samplePhoto(
                id = 1,
                year = 2024,
                month = 1,
                folderPath = "/storage/emulated/0/DCIM/Camera",
                folderName = "camera",
                city = "Hyderabad",
                mlTags = listOf(MLTag("selfie", 0.92f)),
                ocrText = "project yes alpha notes",
                isFavorite = true,
            ),
            samplePhoto(
                id = 2,
                year = 2024,
                month = 2,
                folderPath = "/storage/emulated/0/Pictures/Screenshots",
                folderName = "screenshots",
                city = "Bengaluru",
                mlTags = listOf(MLTag("document", 0.95f)),
                ocrText = "invoice paid 2025",
            ),
            samplePhoto(
                id = 3,
                year = 2023,
                month = 12,
                folderPath = "/storage/emulated/0/DCIM/Camera",
                folderName = "camera",
                city = "Mumbai",
                mlTags = listOf(MLTag("food", 0.91f)),
                ocrText = "dinner menu",
            ),
            samplePhoto(
                id = 4,
                year = 2022,
                month = 8,
                folderPath = "/storage/emulated/0/Download",
                folderName = "download",
                city = "Delhi",
                mlTags = listOf(MLTag("car", 0.88f)),
                ocrText = "registration copy",
                isFavorite = true,
            ),
        )
        index.setRecords(records)

        val engine = FilterEngine(
            queryParser = QueryParser(),
            tokenClassifier = TokenClassifier(index),
            filterFactory = FilterFactory(),
        )

        val queries = listOf(
            "camera",
            "screenshots",
            "download",
            "hyderabad",
            "mumbai",
            "selfie",
            "document",
            "food",
            "car",
            "yes",
            "invoice",
            "2024",
            "2023",
            "favorites",
            "camera 2024",
            "selfie hyderabad",
            "document invoice",
            "download favorites",
            "food mumbai",
            "project yes",
        )

        queries.forEach { query ->
            val first = engine.search(query, records)
            val second = engine.search(query, records)
            assertThat(first.results.map { it.id }).isEqualTo(second.results.map { it.id })
        }
    }

    @Test
    fun sourceToken_filtersByKnownAppSource() = runTest {
        val index = PhotoIndex()
        val records = listOf(
            samplePhoto(
                id = 1,
                year = 2024,
                folderPath = "/storage/emulated/0/WhatsApp/Media/WhatsApp Images",
                folderName = "whatsapp images",
            ),
            samplePhoto(
                id = 2,
                year = 2024,
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

        val result = engine.search("source:whatsapp", records)

        assertThat(result.results).hasSize(1)
        assertThat(result.results.first().id).isEqualTo(1L)
    }

    private fun samplePhoto(
        id: Long,
        year: Int,
        month: Int = 1,
        folderPath: String,
        folderName: String,
        city: String? = null,
        mlTags: List<MLTag> = emptyList(),
        ocrText: String = "",
        isFavorite: Boolean = false,
    ): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://$id",
            filePath = "$folderPath/$id.jpg",
            fileName = "$id.jpg",
            dateAdded = System.currentTimeMillis(),
            year = year,
            month = month,
            dayOfMonth = 1,
            dayOfWeek = 1,
            hourOfDay = 10,
            latitude = null,
            longitude = null,
            city = city,
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
            isFavorite = isFavorite,
            mlTags = mlTags,
            ocrText = ocrText,
        )
    }
}
