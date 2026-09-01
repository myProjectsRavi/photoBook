package com.photobook.app.search

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.index.PhotoIndexStrategy
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.runBlocking
import org.junit.Test

class OcrTextSearchTest {

    @Test
    fun mixedCaseQueries_findSameIndexedOcrPhoto() = runBlocking {
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(
            listOf(
                photo(1L, "photobook invoice abc123 total 500"),
                photo(2L, "unrelated holiday picture"),
            ),
        )
        val engine = searchEngine(index)

        listOf("photobook", "PHOTOBOOK", "PhotoBook", "ABC123", "abc123", "500").forEach { query ->
            val result = engine.search(query)
            assertThat(result.complete).isTrue()
            assertThat(result.orderedIds).containsExactly(1L)
        }
    }

    @Test
    fun reservedSmartKeywords_stillReturnLiteralVisibleText() = runBlocking {
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(
            listOf(
                photo(1L, "payment food today"),
                photo(2L, "unrelated holiday picture"),
            ),
        )
        val engine = searchEngine(index)

        listOf("payment", "PAYMENT", "food", "FOOD", "today", "TODAY").forEach { query ->
            val result = engine.search(
                query = query,
                context = SearchContext(nowMillis = 2_000_000_000_000L),
            )
            assertThat(result.complete).isTrue()
            assertThat(result.orderedIds).containsExactly(1L)
        }
    }

    @Test
    fun singleDigitQuery_remainsSearchable() = runBlocking {
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(
            listOf(
                photo(1L, "table number 7"),
                photo(2L, "table number 8"),
            ),
        )

        val result = searchEngine(index).search("7")

        assertThat(result.complete).isTrue()
        assertThat(result.orderedIds).containsExactly(1L)
        Unit
    }

    @Test
    fun singleLetterNoise_doesNotReturnWholeLibrary() = runBlocking {
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(
            listOf(
                photo(1L, "alpha"),
                photo(2L, "beta"),
            ),
        )

        val result = searchEngine(index).search("a")

        assertThat(result.complete).isTrue()
        assertThat(result.orderedIds).isEmpty()
    }

    private fun searchEngine(index: PhotoIndex): SearchEngineV2 {
        val parser = QueryParser()
        val classifier = TokenClassifier(index)
        val filters = FilterFactory()
        return SearchEngineV2(
            index = index,
            queryParser = parser,
            tokenClassifier = classifier,
            filterFactory = filters,
            searchRanker = SearchRanker(),
        )
    }

    private fun photo(id: Long, ocrText: String): PhotoRecord {
        return PhotoRecord(
            id = id,
            uriString = "content://ocr-search/$id",
            filePath = "/storage/emulated/0/Pictures/Other/$id.jpg",
            fileName = "$id.jpg",
            dateAdded = 1_786_900_000_000L - id,
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
            fileSize = 100_000L,
            width = 1200,
            height = 900,
            mimeType = "image/jpeg",
            folderName = "Other",
            folderPath = "Pictures/Other",
            cameraModel = null,
            isFrontCamera = false,
            isHdr = false,
            ocrText = ocrText,
            isOcrProcessed = true,
            ocrStatus = IntelligenceStatus.PROCESSED,
        )
    }
}
