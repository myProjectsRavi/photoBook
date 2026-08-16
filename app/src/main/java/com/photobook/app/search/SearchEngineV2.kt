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
        /** False means a supplied candidate ID was missing from the in-memory index; callers fall back. */
        val complete: Boolean = true,
    )

    fun search(
        query: String,
        candidateIds: List<Long>? = null,
        context: SearchContext = SearchContext(),
    ): SearchResult {
        val normalized = queryParser.normalize(query)
        val source = sourceRecords(candidateIds)
        if (!source.complete) {
            return SearchResult(emptyList(), emptyList(), complete = false)
        }

        if (normalized.isBlank()) {
            return SearchResult(
                orderedIds = source.records.map { record -> record.id },
                tokens = emptyList(),
            )
        }

        val tokens = queryParser.tokenize(normalized).map(tokenClassifier::classify)
        val filters = tokens.mapNotNull { token -> filterFactory.create(token, context) }
        val isOldest = tokens.any { it is TemporalToken && it.keyword == "oldest" }
        val isRecent = tokens.any { it is TemporalToken && it.keyword == "recent" }

        val hits = ArrayList<SearchHit>()
        source.records.forEachIndexed { ordinal, photo ->
            if (filters.isNotEmpty() && !filters.all { filter -> filter(photo) }) {
                return@forEachIndexed
            }
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

        val orderedIds = if (isRecent && hits.size > RECENT_RESULT_LIMIT) {
            List(RECENT_RESULT_LIMIT) { index -> hits[index].id }
        } else {
            hits.map { hit -> hit.id }
        }
        return SearchResult(orderedIds = orderedIds, tokens = tokens)
    }

    private fun sourceRecords(candidateIds: List<Long>?): SearchSource {
        if (candidateIds == null) {
            return SearchSource(index.snapshot(), complete = true)
        }
        if (candidateIds.isEmpty()) {
            return SearchSource(emptyList(), complete = true)
        }

        val records = ArrayList<PhotoRecord>(candidateIds.size)
        candidateIds.forEach { id ->
            val record = index.getById(id)
                ?: return SearchSource(emptyList(), complete = false)
            records += record
        }
        return SearchSource(records, complete = true)
    }

    private data class SearchSource(
        val records: List<PhotoRecord>,
        val complete: Boolean,
    )

    private data class SearchHit(
        val id: Long,
        val dateAdded: Long,
        val score: Double,
        val ordinal: Int,
    )

    private companion object {
        private const val RECENT_RESULT_LIMIT = 50
    }
}
