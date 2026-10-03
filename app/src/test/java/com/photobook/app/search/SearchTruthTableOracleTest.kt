package com.photobook.app.search

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.index.PhotoIndexStrategy
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SearchTruthTableOracleTest {
    @Test
    fun authoritativeQueries_matchHardCodedEligibilityAndOrder() = runBlocking {
        val records = listOf(
            photo(
                id = 1L,
                dateAdded = 300L,
                year = 2024,
                folder = "camera",
                city = "Hyderabad",
                favorite = true,
                tags = listOf(MLTag("document", 0.95f)),
                ocr = "invoice paid",
            ),
            photo(
                id = 2L,
                dateAdded = 200L,
                year = 2024,
                folder = "screenshots",
                city = "Bengaluru",
                tags = listOf(MLTag("food", 0.94f)),
            ),
            photo(
                id = 3L,
                dateAdded = 100L,
                year = 2023,
                folder = "download",
                city = "Hyderabad",
                tags = listOf(MLTag("document", 0.93f)),
                ocr = "invoice archive",
            ),
        )
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(records)
        val parser = QueryParser()
        val classifier = TokenClassifier(index)
        val filters = FilterFactory()
        val ranker = SearchRanker()
        val legacy = FilterEngine(index, parser, classifier, filters, ranker)
        val v2 = SearchEngineV2(index, parser, classifier, filters, ranker)
        val context = SearchContext(nowMillis = 1_786_900_000_000L)

        val oracle = linkedMapOf(
            "2024" to listOf(1L, 2L),
            "source:camera" to listOf(1L),
            "favorites" to listOf(1L),
            "hyderabad" to listOf(1L, 3L),
            "invoice" to listOf(1L, 3L),
            "document" to listOf(1L, 3L),
            "food" to listOf(2L),
            "2024 camera" to listOf(1L),
            "recent" to listOf(1L, 2L, 3L),
            "oldest" to listOf(3L, 2L, 1L),
        )

        oracle.forEach { (query, expectedIds) ->
            val legacyIds = legacy.search(query, records, context).results.map { it.id }
            val v2Result = v2.search(
                query = query,
                candidateIds = null,
                context = context,
                expectedIndexVersion = index.version(),
            )

            assertThat(legacyIds).named("legacy $query").containsExactlyElementsIn(expectedIds).inOrder()
            assertThat(v2Result.complete).named("complete $query").isTrue()
            assertThat(v2Result.orderedIds).named("v2 $query").containsExactlyElementsIn(expectedIds).inOrder()
        }
    }

    private fun photo(
        id: Long,
        dateAdded: Long,
        year: Int,
        folder: String,
        city: String,
        favorite: Boolean = false,
        tags: List<MLTag> = emptyList(),
        ocr: String = "",
    ) = PhotoRecord(
        id = id,
        uriString = "content://truth/$id",
        filePath = "/Pictures/$folder/$id.jpg",
        fileName = "$id.jpg",
        dateAdded = dateAdded,
        year = year,
        month = 1,
        dayOfMonth = 1,
        dayOfWeek = 1,
        hourOfDay = 10,
        latitude = null,
        longitude = null,
        city = city,
        state = null,
        country = "India",
        fileSize = 1024L,
        width = 1000,
        height = 1000,
        mimeType = "image/jpeg",
        folderName = folder,
        folderPath = "/pictures/$folder",
        cameraModel = null,
        isFrontCamera = false,
        isHdr = false,
        isFavorite = favorite,
        mlTags = tags,
        isMlProcessed = tags.isNotEmpty(),
        mlStatus = if (tags.isNotEmpty()) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
        ocrText = ocr,
        isOcrProcessed = ocr.isNotBlank(),
        ocrStatus = if (ocr.isNotBlank()) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
    )
}
