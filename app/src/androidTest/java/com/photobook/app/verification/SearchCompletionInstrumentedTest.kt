package com.photobook.app.verification

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.notes.PhotoNoteStore
import com.photobook.app.search.FilterFactory
import com.photobook.app.search.QueryParser
import com.photobook.app.search.SearchContext
import com.photobook.app.search.SearchEngineV2
import com.photobook.app.search.SearchRanker
import com.photobook.app.search.TokenClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchCompletionInstrumentedTest {

    @Test
    fun representative100kQueries_haveExactResultsAndReportCompletionP95() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val noteStore = PhotoNoteStore(context)
        noteStore.deleteNote(NOTE_ID)
        assertTrue("Encrypted private-note store must be writable", noteStore.saveNote(NOTE_ID, NOTE_TOKEN))

        try {
            val index = PhotoIndex()
            index.setRecords(buildRecords(LIBRARY_SIZE))

            val engine = SearchEngineV2(
                index = index,
                queryParser = QueryParser(),
                tokenClassifier = TokenClassifier(index),
                filterFactory = FilterFactory(noteStore),
                searchRanker = SearchRanker(),
            )
            val searchContext = SearchContext(nowMillis = FIXED_NOW_MS)

            val cases = listOf(
                SearchCase(
                    label = "ocr",
                    query = OCR_TOKEN,
                    expectedIds = listOf(OCR_ID),
                ),
                SearchCase(
                    label = "filename",
                    query = FILE_NAME_TOKEN,
                    expectedIds = listOf(FILE_NAME_ID),
                ),
                SearchCase(
                    label = "date",
                    query = "2024",
                    expectedIds = listOf(DATE_ID),
                ),
                SearchCase(
                    label = "synonym",
                    query = "dog",
                    expectedIds = listOf(SYNONYM_ID),
                ),
                SearchCase(
                    label = "notes",
                    query = NOTE_TOKEN,
                    expectedIds = listOf(NOTE_ID),
                ),
            )

            cases.forEach { searchCase ->
                val warmup = engine.search(
                    query = searchCase.query,
                    candidateIds = null,
                    context = searchContext,
                    expectedIndexVersion = index.version(),
                )
                assertTrue("Warmup result incomplete for ${searchCase.label}", warmup.complete)
                assertEquals("Warmup exact IDs for ${searchCase.label}", searchCase.expectedIds, warmup.orderedIds)

                val samplesMs = ArrayList<Long>(MEASURED_ITERATIONS)
                repeat(MEASURED_ITERATIONS) {
                    val startedNs = SystemClock.elapsedRealtimeNanos()
                    val result = engine.search(
                        query = searchCase.query,
                        candidateIds = null,
                        context = searchContext,
                        expectedIndexVersion = index.version(),
                    )
                    val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L
                    assertTrue("Measured result incomplete for ${searchCase.label}", result.complete)
                    assertEquals("Measured exact IDs for ${searchCase.label}", searchCase.expectedIds, result.orderedIds)
                    samplesMs += elapsedMs
                }

                val sorted = samplesMs.sorted()
                val p50Ms = percentile(sorted, 50)
                val p95Ms = percentile(sorted, 95)
                val maxMs = sorted.last()
                Log.i(
                    LOG_TAG,
                    "label=${searchCase.label} librarySize=$LIBRARY_SIZE iterations=$MEASURED_ITERATIONS " +
                        "p50Ms=$p50Ms p95Ms=$p95Ms maxMs=$maxMs exactIds=${searchCase.expectedIds.joinToString(",")}",
                )
                println(
                    "[search-cert] label=${searchCase.label} librarySize=$LIBRARY_SIZE " +
                        "p50Ms=$p50Ms p95Ms=$p95Ms maxMs=$maxMs " +
                        "exactIds=${searchCase.expectedIds.joinToString(",")}",
                )
                assertTrue(
                    "100k search p95 exceeded ${SEARCH_P95_BUDGET_MS}ms for ${searchCase.label}: " +
                        "p95=$p95Ms samples=$sorted",
                    p95Ms <= SEARCH_P95_BUDGET_MS,
                )
            }
        } finally {
            noteStore.deleteNote(NOTE_ID)
        }
    }

    private fun buildRecords(count: Int): List<PhotoRecord> {
        return List(count) { offset ->
            val id = offset.toLong() + 1L
            val isOcrTarget = id == OCR_ID
            val isFileNameTarget = id == FILE_NAME_ID
            val isDateTarget = id == DATE_ID
            val isSynonymTarget = id == SYNONYM_ID

            PhotoRecord(
                id = id,
                uriString = "content://search-cert/$id",
                filePath = "/storage/emulated/0/DCIM/Camera/${if (isFileNameTarget) FILE_NAME_TOKEN else "IMG_$id"}.jpg",
                fileName = "${if (isFileNameTarget) FILE_NAME_TOKEN else "IMG_$id"}.jpg",
                dateAdded = FIXED_NOW_MS - id * 60_000L,
                year = if (isDateTarget) 2024 else 2023,
                month = 6,
                dayOfMonth = 15,
                dayOfWeek = 4,
                hourOfDay = 12,
                latitude = null,
                longitude = null,
                city = null,
                state = null,
                country = null,
                fileSize = 250_000L + id,
                width = 1920,
                height = 1080,
                mimeType = "image/jpeg",
                folderName = "camera",
                folderPath = "dcim/camera",
                cameraModel = null,
                isFrontCamera = false,
                isHdr = false,
                mlTags = if (isSynonymTarget) listOf(MLTag("pet", 0.95f)) else emptyList(),
                isMlProcessed = isSynonymTarget,
                mlStatus = if (isSynonymTarget) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
                ocrText = if (isOcrTarget) "reference $OCR_TOKEN confirmed" else "",
                isOcrProcessed = isOcrTarget,
                ocrStatus = if (isOcrTarget) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
            )
        }
    }

    private fun percentile(sortedSamples: List<Long>, percentile: Int): Long {
        check(sortedSamples.isNotEmpty())
        val index = (((sortedSamples.size * percentile) + 99) / 100 - 1)
            .coerceIn(0, sortedSamples.lastIndex)
        return sortedSamples[index]
    }

    private data class SearchCase(
        val label: String,
        val query: String,
        val expectedIds: List<Long>,
    )

    private companion object {
        private const val LOG_TAG = "PhotoBookSearchCert"
        private const val LIBRARY_SIZE = 100_000
        private const val MEASURED_ITERATIONS = 10
        private const val SEARCH_P95_BUDGET_MS = 300L
        private const val FIXED_NOW_MS = 1_786_900_000_000L

        private const val OCR_ID = 101L
        private const val FILE_NAME_ID = 202L
        private const val DATE_ID = 303L
        private const val SYNONYM_ID = 404L
        private const val NOTE_ID = 505L

        private const val OCR_TOKEN = "zxocrneedle"
        private const val FILE_NAME_TOKEN = "specialfilename"
        private const val NOTE_TOKEN = "privatenoteneedle"
    }
}
