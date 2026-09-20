package com.photobook.app.feature.phototextsearch

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class PhotoTextMatcher {

    fun normalizeQuery(raw: String): String = canonicalize(raw).trim()

    internal fun buildBlocks(layout: PhotoTextLayout): List<SearchBlock> {
        if (layout.elements.isEmpty()) return emptyList()
        val grouped = LinkedHashMap<Int, MutableList<TextElement>>()
        layout.elements.forEach { element ->
            grouped.getOrPut(element.blockId) { mutableListOf() }.add(element)
        }

        return grouped.map { (blockId, elements) ->
            val text = StringBuilder()
            val owners = ArrayList<Int>()
            elements.forEachIndexed { index, element ->
                if (index > 0 && (text.isEmpty() || text.last() != ' ')) {
                    text.append(' ')
                    owners += NO_OWNER
                }
                val canonical = canonicalize(element.text)
                canonical.forEach { char ->
                    text.append(char)
                    owners += element.id
                }
            }
            SearchBlock(
                id = blockId,
                text = text.toString().trim(),
                ownerElementId = trimOwners(text.toString(), owners.toIntArray()),
            )
        }
    }

    internal suspend fun find(
        blocks: List<SearchBlock>,
        normalizedQuery: String,
    ): List<SearchOccurrence> {
        if (normalizedQuery.isEmpty()) return emptyList()
        val hits = ArrayList<SearchOccurrence>()
        var occurrenceCounter = 0

        blocks.forEach { block ->
            if (block.text.length < normalizedQuery.length) return@forEach
            var from = 0
            while (from <= block.text.length - normalizedQuery.length) {
                if (occurrenceCounter % CANCELLATION_INTERVAL == 0) {
                    currentCoroutineContext().ensureActive()
                }
                val start = block.text.indexOf(normalizedQuery, from)
                if (start < 0) break
                val end = start + normalizedQuery.length
                val elementIds = LinkedHashSet<Int>()
                for (index in start until end) {
                    block.ownerElementId.getOrNull(index)
                        ?.takeIf { it >= 0 }
                        ?.let(elementIds::add)
                }
                if (elementIds.isNotEmpty()) {
                    hits += SearchOccurrence(
                        blockId = block.id,
                        startInclusive = start,
                        endExclusive = end,
                        elementIds = elementIds.toList(),
                    )
                }
                occurrenceCounter += 1
                from = end
            }
        }
        return hits
    }

    private fun canonicalize(raw: String): String {
        if (raw.isEmpty()) return raw
        val output = StringBuilder(raw.length)
        var lastWasSpace = false
        raw.forEach { input ->
            val isSpace = input.isWhitespace()
            if (isSpace) {
                if (!lastWasSpace) {
                    output.append(' ')
                    lastWasSpace = true
                }
            } else {
                output.append(
                    if (input in 'A'..'Z') {
                        input.lowercaseChar()
                    } else {
                        input
                    },
                )
                lastWasSpace = false
            }
        }
        return output.toString()
    }

    private fun trimOwners(text: String, owner: IntArray): IntArray {
        var start = 0
        var end = text.length
        while (start < end && text[start].isWhitespace()) start += 1
        while (end > start && text[end - 1].isWhitespace()) end -= 1
        return owner.copyOfRange(start, end)
    }

    private companion object {
        private const val NO_OWNER = -1
        private const val CANCELLATION_INTERVAL = 64
    }
}
