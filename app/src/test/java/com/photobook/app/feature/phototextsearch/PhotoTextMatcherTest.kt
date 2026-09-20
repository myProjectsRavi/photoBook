package com.photobook.app.feature.phototextsearch

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test

class PhotoTextMatcherTest {

    private val matcher = PhotoTextMatcher()

    @Test
    fun matching_isAsciiCaseInsensitiveAndLiteral() {
        runBlocking {
            val layout = layout(
                element(1, 0, 0, "Invoice"),
                element(2, 0, 0, "001234"),
                element(3, 0, 0, "AB-1234"),
            )
            val blocks = matcher.buildBlocks(layout)

            assertThat(matcher.find(blocks, matcher.normalizeQuery("InVoIcE")).single().elementIds)
                .containsExactly(1)
            assertThat(matcher.find(blocks, matcher.normalizeQuery("1234")).map { it.elementIds })
                .containsExactly(listOf(2), listOf(3))
            assertThat(matcher.find(blocks, matcher.normalizeQuery("AB1234"))).isEmpty()
        }
    }

    @Test
    fun matching_preservesLeadingZerosAndPunctuation() {
        runBlocking {
            val blocks = matcher.buildBlocks(
                layout(
                    element(1, 0, 0, "Reference"),
                    element(2, 0, 0, "001234"),
                    element(3, 0, 0, "12.50"),
                ),
            )

            assertThat(matcher.find(blocks, matcher.normalizeQuery("001234")).single().elementIds)
                .containsExactly(2)
            assertThat(matcher.find(blocks, matcher.normalizeQuery("12.50")).single().elementIds)
                .containsExactly(3)
        }
    }

    @Test
    fun phrase_canCrossLineBreakInsideSameBlock() {
        runBlocking {
            val blocks = matcher.buildBlocks(
                layout(
                    element(1, 7, 0, "INVOICE"),
                    element(2, 7, 1, "NUMBER"),
                ),
            )

            val hits = matcher.find(blocks, matcher.normalizeQuery("invoice number"))

            assertThat(hits).hasSize(1)
            assertThat(hits.single().elementIds).containsExactly(1, 2).inOrder()
        }
    }

    @Test
    fun phrase_neverCrossesIndependentBlocks() {
        runBlocking {
            val blocks = matcher.buildBlocks(
                layout(
                    element(1, 1, 0, "INVOICE"),
                    element(2, 2, 0, "NUMBER"),
                ),
            )

            assertThat(matcher.find(blocks, matcher.normalizeQuery("invoice number"))).isEmpty()
        }
    }

    @Test
    fun repeatedOccurrences_areNonOverlappingAndDeterministic() {
        runBlocking {
            val blocks = matcher.buildBlocks(layout(element(1, 0, 0, "PAN and company pan")))

            val hits = matcher.find(blocks, matcher.normalizeQuery("pan"))

            assertThat(hits).hasSize(2)
            assertThat(hits.map { it.elementIds }).containsExactly(listOf(1), listOf(1)).inOrder()
        }
    }

    @Test
    fun substringHighlightsContainingElement() {
        runBlocking {
            val blocks = matcher.buildBlocks(layout(element(8, 0, 0, "001234")))

            val hit = matcher.find(blocks, matcher.normalizeQuery("1234")).single()

            assertThat(hit.elementIds).containsExactly(8)
        }
    }

    @Test
    fun whitespaceIsCollapsedButPunctuationIsNotRemoved() {
        runBlocking {
            val blocks = matcher.buildBlocks(
                layout(
                    element(1, 0, 0, "INVOICE"),
                    element(2, 0, 1, "NUMBER"),
                ),
            )

            assertThat(matcher.normalizeQuery("  invoice   number  ")).isEqualTo("invoice number")
            assertThat(matcher.find(blocks, matcher.normalizeQuery("invoice   number"))).hasSize(1)
            assertThat(matcher.normalizeQuery("AB-1234")).isEqualTo("ab-1234")
        }
    }

    private fun layout(vararg elements: TextElement): PhotoTextLayout {
        return PhotoTextLayout(
            uprightWidth = 1200,
            uprightHeight = 800,
            elements = elements.toList(),
            completeness = PhotoTextCompleteness.COMPLETE,
        )
    }

    private fun element(id: Int, blockId: Int, lineId: Int, text: String): TextElement {
        return TextElement(
            id = id,
            blockId = blockId,
            lineId = lineId,
            text = text,
            corners = listOf(
                UnitPoint(0.1f, 0.1f),
                UnitPoint(0.2f, 0.1f),
                UnitPoint(0.2f, 0.2f),
                UnitPoint(0.1f, 0.2f),
            ),
        )
    }
}
