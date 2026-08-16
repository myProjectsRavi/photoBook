package com.photobook.app.search

import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.index.PhotoIndexStrategy
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SearchEngineV2ParityTest {

    @Test
    fun v2_matchesLegacyIdOrderOnDeterministic10k50k100kCorpora() = runBlocking {
        listOf(10_000, 50_000, 100_000).forEach { count ->
            val records = deterministicRecords(count)
            val index = PhotoIndex(PhotoIndexStrategy.V2)
            index.setRecords(records)

            val parser = QueryParser()
            val classifier = TokenClassifier(index)
            val filters = FilterFactory()
            val ranker = SearchRanker()
            val legacy = FilterEngine(index, parser, classifier, filters, ranker)
            val v2 = SearchEngineV2(index, parser, classifier, filters, ranker)
            val context = SearchContext(nowMillis = NOW)

            QUERIES.forEach { query ->
                val legacyIds = legacy.search(query, index.snapshot(), context).results.map { it.id }
                val v2Result = v2.search(query, candidateIds = null, context = context)
                assertEquals("v2 unexpectedly incomplete for '$query' at $count", true, v2Result.complete)
                assertEquals("ID/order mismatch for '$query' at $count", legacyIds, v2Result.orderedIds)
            }
        }
    }

    @Test
    fun v2_matchesLegacyForPreorderedCandidateIdsAndStableTies() = runBlocking {
        val records = deterministicRecords(10_000)
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(records)
        val parser = QueryParser()
        val classifier = TokenClassifier(index)
        val filters = FilterFactory()
        val ranker = SearchRanker()
        val legacy = FilterEngine(index, parser, classifier, filters, ranker)
        val v2 = SearchEngineV2(index, parser, classifier, filters, ranker)
        val context = SearchContext(nowMillis = NOW)

        // FTS candidate order is an input contract. Use a deliberately non-chronological order and
        // tied timestamps so stable-sort behavior is observable.
        val candidateIds = buildList {
            for (id in 9_999L downTo 1L step 3) add(id)
        }
        val candidateRecords = index.getByIdsOrdered(candidateIds)

        listOf("invoice", "document", "food", "recent", "oldest", "hyderabad").forEach { query ->
            val legacyIds = legacy.search(query, candidateRecords, context).results.map { it.id }
            val v2Result = v2.search(query, candidateIds, context)
            assertEquals("candidate v2 incomplete for '$query'", true, v2Result.complete)
            assertEquals("candidate ID/order mismatch for '$query'", legacyIds, v2Result.orderedIds)
        }
    }

    @Test
    fun missingCandidateForcesRollbackInsteadOfReturningPartialResults() = runBlocking {
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(deterministicRecords(100))
        val parser = QueryParser()
        val classifier = TokenClassifier(index)
        val filters = FilterFactory()
        val ranker = SearchRanker()
        val v2 = SearchEngineV2(index, parser, classifier, filters, ranker)

        val result = v2.search(
            query = "invoice",
            candidateIds = listOf(1L, 2L, 999_999L),
            context = SearchContext(nowMillis = NOW),
        )

        assertFalse(result.complete)
        assertEquals(emptyList<Long>(), result.orderedIds)
    }

    @Test
    fun staleExpectedGenerationForcesRollbackInsteadOfMixingSnapshots() = runBlocking {
        val index = PhotoIndex(PhotoIndexStrategy.V2)
        index.setRecords(deterministicRecords(1_000))
        val parser = QueryParser()
        val classifier = TokenClassifier(index)
        val filters = FilterFactory()
        val ranker = SearchRanker()
        val v2 = SearchEngineV2(index, parser, classifier, filters, ranker)
        val staleVersion = index.changes().value

        index.toggleFavorite(1L)

        val staleResult = v2.search(
            query = "invoice",
            context = SearchContext(nowMillis = NOW),
            expectedIndexVersion = staleVersion,
        )
        assertFalse(staleResult.complete)
        assertEquals(emptyList<Long>(), staleResult.orderedIds)

        val currentVersion = index.changes().value
        val currentResult = v2.search(
            query = "invoice",
            context = SearchContext(nowMillis = NOW),
            expectedIndexVersion = currentVersion,
        )
        assertEquals(true, currentResult.complete)
    }

    private fun deterministicRecords(count: Int): List<PhotoRecord> {
        return List(count) { offset ->
            val id = offset.toLong() + 1L
            val folder = when (offset % 5) {
                0 -> "Camera"
                1 -> "Screenshots"
                2 -> "Downloads"
                3 -> "WhatsApp Images"
                else -> "Receipts"
            }
            val tags = buildList {
                when (offset % 6) {
                    0 -> add(MLTag("document", 0.90f))
                    1 -> add(MLTag("food", 0.86f))
                    2 -> add(MLTag("receipt", 0.91f))
                }
                if (offset % 17 == 0) add(MLTag("cat", 0.88f))
            }
            PhotoRecord(
                id = id,
                uriString = "content://phase2-search/$id",
                filePath = "/storage/emulated/0/Pictures/$folder/$id.jpg",
                fileName = when (offset % 7) {
                    0 -> "invoice_${id}.jpg"
                    1 -> "birthday_${id}.jpg"
                    else -> "IMG_${id}.jpg"
                },
                // Four-way ties make stable ordinal behavior part of the test.
                dateAdded = NOW - (offset / 4) * 60_000L,
                year = 2022 + (offset % 5),
                month = (offset % 12) + 1,
                dayOfMonth = (offset % 28) + 1,
                dayOfWeek = (offset % 7) + 1,
                hourOfDay = offset % 24,
                latitude = if (offset % 13 == 0) 17.3850 else null,
                longitude = if (offset % 13 == 0) 78.4867 else null,
                city = if (offset % 4 == 0) "Hyderabad" else if (offset % 4 == 1) "Bengaluru" else null,
                state = if (offset % 4 <= 1) "Telangana" else null,
                country = if (offset % 4 <= 1) "India" else null,
                fileSize = 250_000L + offset * 50L,
                width = if (offset % 3 == 0) 4032 else 1080,
                height = if (offset % 3 == 0) 3024 else 1920,
                mimeType = if (offset % 19 == 0) "image/png" else "image/jpeg",
                folderName = folder,
                folderPath = "/Pictures/$folder",
                cameraModel = if (folder == "Camera") "Phase2 Camera" else null,
                isFrontCamera = offset % 29 == 0,
                isHdr = offset % 31 == 0,
                isFavorite = offset % 23 == 0,
                perceptualHash = id * 17L,
                blurScore = (offset % 10) / 10.0,
                mlTags = tags,
                isMlProcessed = true,
                mlStatus = IntelligenceStatus.PROCESSED,
                ocrText = when (offset % 8) {
                    0 -> "invoice payment total 499 order phase2"
                    1 -> "birthday dinner menu celebration"
                    2 -> "document account statement"
                    else -> ""
                },
                isOcrProcessed = true,
                ocrStatus = IntelligenceStatus.PROCESSED,
            )
        }
    }

    private companion object {
        private const val NOW = 1_786_900_000_000L
        private val QUERIES = listOf(
            "invoice",
            "document",
            "food",
            "screenshots",
            "favorite",
            "hyderabad",
            "recent",
            "oldest",
            "2026",
            "monday",
            "morning",
            "invoice document",
        )
    }
}
