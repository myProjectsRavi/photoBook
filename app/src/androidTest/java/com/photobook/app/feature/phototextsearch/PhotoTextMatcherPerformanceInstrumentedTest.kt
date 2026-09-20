package com.photobook.app.feature.phototextsearch

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoTextMatcherPerformanceInstrumentedTest {

    @Test
    fun warm100kCharacterMatching_p95StaysWithinViewerBudget() = runBlocking {
        val matcher = PhotoTextMatcher()
        val elements = List(ELEMENT_COUNT) { index ->
            TextElement(
                id = index,
                blockId = 0,
                lineId = index / 10,
                text = if (index == TARGET_INDEX) TARGET_TEXT else "item%05d".format(index),
                corners = listOf(
                    UnitPoint(0.1f, 0.1f),
                    UnitPoint(0.2f, 0.1f),
                    UnitPoint(0.2f, 0.2f),
                    UnitPoint(0.1f, 0.2f),
                ),
            )
        }
        val layout = PhotoTextLayout(
            uprightWidth = 1600,
            uprightHeight = 1200,
            elements = elements,
            completeness = PhotoTextCompleteness.COMPLETE,
        )
        val blocks = matcher.buildBlocks(layout)
        val query = matcher.normalizeQuery(TARGET_TEXT)

        repeat(WARMUP_ITERATIONS) {
            assertEquals(listOf(TARGET_INDEX), matcher.find(blocks, query).single().elementIds)
        }

        val samples = ArrayList<Long>(MEASURED_ITERATIONS)
        repeat(MEASURED_ITERATIONS) {
            val startedNs = SystemClock.elapsedRealtimeNanos()
            val hits = matcher.find(blocks, query)
            val elapsedMs = (SystemClock.elapsedRealtimeNanos() - startedNs) / 1_000_000L
            assertEquals(listOf(TARGET_INDEX), hits.single().elementIds)
            samples += elapsedMs
        }

        val sorted = samples.sorted()
        val p95 = sorted[((sorted.size * 95 + 99) / 100 - 1).coerceIn(0, sorted.lastIndex)]
        val max = sorted.last()
        Log.i(LOG_TAG, "elements=$ELEMENT_COUNT p95Ms=$p95 maxMs=$max samples=$sorted")
        assertTrue(
            "Warm literal match p95 exceeded ${P95_BUDGET_MS}ms: p95=$p95 samples=$sorted",
            p95 <= P95_BUDGET_MS,
        )
    }

    private companion object {
        private const val LOG_TAG = "PhotoTextSearchPerf"
        private const val ELEMENT_COUNT = 10_000
        private const val TARGET_INDEX = 9_999
        private const val TARGET_TEXT = "invoice001234"
        private const val WARMUP_ITERATIONS = 5
        private const val MEASURED_ITERATIONS = 20
        private const val P95_BUDGET_MS = 50L
    }
}
