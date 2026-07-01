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
            index = index,
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
            index = index,
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
            index = index,
            queryParser = QueryParser(),
            tokenClassifier = TokenClassifier(index),
            filterFactory = FilterFactory(),
        )

        val result = engine.search("source:whatsapp", records)

        assertThat(result.results).hasSize(1)
        assertThat(result.results.first().id).isEqualTo(1L)
    }

    @Test
    fun ranking_prioritizesExactOcrPhraseWithoutChangingEligibility() = runTest {
        val index = PhotoIndex()
        val now = 1_700_000_000_000L
        val records = listOf(
            samplePhoto(
                id = 1,
                year = 2024,
                month = 3,
                folderPath = "/storage/emulated/0/Download",
                folderName = "download",
                ocrText = "paid from zomato by card",
                dateAdded = now - 30_000L,
            ),
            samplePhoto(
                id = 2,
                year = 2024,
                month = 3,
                folderPath = "/storage/emulated/0/Pictures/Screenshots",
                folderName = "screenshots",
                ocrText = "zomato paid march order total",
                dateAdded = now - 10_000_000L,
            ),
        )
        index.setRecords(records)

        val engine = FilterEngine(
            index = index,
            queryParser = QueryParser(),
            tokenClassifier = TokenClassifier(index),
            filterFactory = FilterFactory(),
        )

        val result = engine.search(
            query = "zomato paid march",
            records = records,
            context = SearchContext(nowMillis = now),
        )

        assertThat(result.results.map { it.id }).containsExactly(2L, 1L).inOrder()
        assertThat(result.results.map { it.id }.toSet()).isEqualTo(records.map { it.id }.toSet())
    }

    @Test
    fun smartAlbumPropertyTokens_filterUsingExistingMetadata() = runTest {
        val index = PhotoIndex()
        val records = listOf(
            samplePhoto(
                id = 1,
                year = 2024,
                folderPath = "/storage/emulated/0/Pictures/Screenshots",
                folderName = "screenshots",
                ocrText = "paid to ravi using upi via gpay",
                blurScore = 42.0,
                fileSize = 7L * 1024L * 1024L,
            ),
            samplePhoto(
                id = 2,
                year = 2024,
                folderPath = "/storage/emulated/0/DCIM/Camera",
                folderName = "camera",
                latitude = 17.4,
                longitude = 78.4,
            ),
            samplePhoto(
                id = 3,
                year = 2024,
                folderPath = "/storage/emulated/0/Download",
                folderName = "download",
                ocrText = "boarding pass",
            ),
        )
        index.setRecords(records)

        val engine = FilterEngine(
            index = index,
            queryParser = QueryParser(),
            tokenClassifier = TokenClassifier(index),
            filterFactory = FilterFactory(),
        )

        assertThat(engine.search("with_text", records).results.map { it.id }).containsExactly(1L, 3L)
        assertThat(engine.search("with_location", records).results.map { it.id }).containsExactly(2L)
        assertThat(engine.search("without_location", records).results.map { it.id }).containsExactly(1L, 3L)
        assertThat(engine.search("large", records).results.map { it.id }).containsExactly(1L)
        assertThat(engine.search("blurry", records).results.map { it.id }).containsExactly(1L)
        assertThat(engine.search("payment", records).results.map { it.id }).containsExactly(1L)
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
        dateAdded: Long = System.currentTimeMillis(),
        latitude: Double? = null,
        longitude: Double? = null,
        fileSize: Long = 1024L,
        blurScore: Double? = null,
    ): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://$id",
            filePath = "$folderPath/$id.jpg",
            fileName = "$id.jpg",
            dateAdded = dateAdded,
            year = year,
            month = month,
            dayOfMonth = 1,
            dayOfWeek = 1,
            hourOfDay = 10,
            latitude = latitude,
            longitude = longitude,
            city = city,
            state = null,
            country = null,
            fileSize = fileSize,
            width = 1000,
            height = 1000,
            mimeType = "image/jpeg",
            folderName = folderName,
            folderPath = folderPath.lowercase(),
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            isFavorite = isFavorite,
            blurScore = blurScore,
            mlTags = mlTags,
            ocrText = ocrText,
        )
    }
}
