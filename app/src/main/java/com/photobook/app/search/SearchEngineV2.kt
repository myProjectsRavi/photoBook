package com.photobook.app.search

import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.PhotoRecord
import javax.inject.Inject

/**
 * Search v2 keeps the Phase-1 parser/filter/ranking semantics but returns compact ordered IDs.
 * Full PhotoRecord materialization is deferred to PagingSource pages.
 */
class SearchEngineV2 @Inject constructor(
    private val index: PhotoIndex,
    private val queryParser: QueryParser,
    private val tokenClassifier: TokenClassifier,
    private val filterFactory: FilterFactory,
    private val searchRanker: SearchRanker,
) {
    data class SearchResult(
        val orderedIds: List<Long>,
        val tokens: List<QueryToken>,
        /** False means the v2 result cannot be proven complete/consistent; callers fall back. */
        val complete: Boolean = true,
    )

    fun search(
        query: String,
        candidateIds: List<Long>? = null,
        context: SearchContext = SearchContext(),
        expectedIndexVersion: Long? = null,
    ): SearchResult {
        val sourceVersion = index.version()
        if (!generationMatches(sourceVersion, expectedIndexVersion)) {
            return SearchResult(emptyList(), emptyList(), complete = false)
        }

        // Capture exactly one immutable generation. A writer advances version before changing any
        // exposed keyword/snapshot state and advances changeFlow only after publication is complete.
        val sourceSnapshot = index.snapshot()
        if (!generationMatches(sourceVersion, expectedIndexVersion)) {
            return SearchResult(emptyList(), emptyList(), complete = false)
        }

        val normalized = queryParser.normalize(query)
        if (normalized.isBlank()) {
            val orderedIds = if (candidateIds == null) {
                sourceSnapshot.map { record -> record.id }
            } else {
                if (candidateIds.any { id -> index.getByIdFromSnapshot(sourceSnapshot, id) == null }) {
                    return SearchResult(emptyList(), emptyList(), complete = false)
                }
                candidateIds.toList()
            }
            return if (generationMatches(sourceVersion, expectedIndexVersion)) {
                SearchResult(orderedIds = orderedIds, tokens = emptyList())
            } else {
                SearchResult(emptyList(), emptyList(), complete = false)
            }
        }

        val tokens = queryParser.tokenize(normalized).map(tokenClassifier::classify)
        val filters = tokens.mapNotNull { token -> filterFactory.create(token, context) }
        val isOldest = tokens.any { it is TemporalToken && it.keyword == "oldest" }
        val isRecent = tokens.any { it is TemporalToken && it.keyword == "recent" }
        val hitCapacity = (candidateIds?.size ?: sourceSnapshot.size).coerceAtMost(MAX_INITIAL_HIT_CAPACITY)
        val hits = ArrayList<SearchHit>(hitCapacity)

        val complete = forEachSource(sourceSnapshot, candidateIds) { ordinal, photo ->
            val smartMatch = filters.isNotEmpty() && filters.all { filter -> filter(photo) }
            val literalOcrMatch = OcrQueryMatcher.matches(photo.ocrText, query)
            if (smartMatch || literalOcrMatch) {
                hits += SearchHit(
                    id = photo.id,
                    dateAdded = photo.dateAdded,
                    score = if (tokens.isEmpty() || isOldest || isRecent) {
                        0.0
                    } else {
                        searchRanker.score(photo, tokens, normalized, context)
                    },
                    ordinal = ordinal,
                )
            }
        }
        if (!complete || !generationMatches(sourceVersion, expectedIndexVersion)) {
            return SearchResult(emptyList(), tokens, complete = false)
        }

        if (hits.size > 1 && tokens.isNotEmpty()) {
            when {
                isOldest -> hits.sortWith(
                    compareBy<SearchHit> { hit -> hit.dateAdded }
                        .thenBy { hit -> hit.ordinal },
                )

                isRecent -> hits.sortWith(
                    compareByDescending<SearchHit> { hit -> hit.dateAdded }
                        .thenBy { hit -> hit.ordinal },
                )

                else -> hits.sortWith(
                    compareByDescending<SearchHit> { hit -> hit.score }
                        .thenByDescending { hit -> hit.dateAdded }
                        .thenBy { hit -> hit.ordinal },
                )
            }
        }

        if (!generationMatches(sourceVersion, expectedIndexVersion)) {
            return SearchResult(emptyList(), tokens, complete = false)
        }
        val orderedIds = if (isRecent && hits.size > RECENT_RESULT_LIMIT) {
            List(RECENT_RESULT_LIMIT) { index -> hits[index].id }
        } else {
            hits.map { hit -> hit.id }
        }
        return SearchResult(orderedIds = orderedIds, tokens = tokens)
    }

    /** Candidate IDs are resolved from the captured snapshot, never from mutable writer state. */
    private inline fun forEachSource(
        sourceSnapshot: List<PhotoRecord>,
        candidateIds: List<Long>?,
        action: (ordinal: Int, photo: PhotoRecord) -> Unit,
    ): Boolean {
        if (candidateIds == null) {
            sourceSnapshot.forEachIndexed(action)
            return true
        }
        candidateIds.forEachIndexed { ordinal, id ->
            val photo = index.getByIdFromSnapshot(sourceSnapshot, id) ?: return false
            action(ordinal, photo)
        }
        return true
    }

    private fun generationMatches(sourceVersion: Long, expectedIndexVersion: Long?): Boolean {
        return index.version() == sourceVersion &&
            index.changes().value == sourceVersion &&
            (expectedIndexVersion == null || expectedIndexVersion == sourceVersion)
    }

    private data class SearchHit(
        val id: Long,
        val dateAdded: Long,
        val score: Double,
        val ordinal: Int,
    )

    private companion object {
        private const val RECENT_RESULT_LIMIT = 50
        private const val MAX_INITIAL_HIT_CAPACITY = 16_384
    }
}
