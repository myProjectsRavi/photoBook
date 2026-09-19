package com.photobook.app.search

import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.PhotoRecord
import javax.inject.Inject

class FilterEngine @Inject constructor(
    private val index: PhotoIndex,
    private val queryParser: QueryParser,
    private val tokenClassifier: TokenClassifier,
    private val filterFactory: FilterFactory,
    private val searchRanker: SearchRanker,
) {

    constructor(
        index: PhotoIndex,
        queryParser: QueryParser,
        tokenClassifier: TokenClassifier,
        filterFactory: FilterFactory,
    ) : this(index, queryParser, tokenClassifier, filterFactory, SearchRanker())

    data class SearchResult(
        val results: List<PhotoRecord>,
        val tokens: List<QueryToken>,
    )

    private val cache = object : LinkedHashMap<String, SearchResult>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SearchResult>?): Boolean {
            return size > 10
        }
    }

    fun search(
        query: String,
        records: List<PhotoRecord>,
        context: SearchContext = SearchContext(),
        cancellationCheck: (() -> Unit)? = null,
    ): SearchResult {
        cancellationCheck?.invoke()
        val normalized = queryParser.normalize(query)
        val key = "$normalized|${index.version()}|${records.size}"
        cache[key]?.let { cached -> return cached }

        if (normalized.isBlank()) {
            return SearchResult(
                results = records.sortedByDescending { it.dateAdded },
                tokens = emptyList(),
            ).also { cache[key] = it }
        }

        val tokens = queryParser.tokenize(normalized).map(tokenClassifier::classify)
        val filters = tokens.mapNotNull { token -> filterFactory.create(token, context) }

        val filtered = ArrayList<PhotoRecord>()
        records.forEachIndexed { index, photo ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) cancellationCheck?.invoke()
            val smartMatch = filters.isNotEmpty() && filters.all { filter -> filter(photo) }
            val literalOcrMatch = OcrQueryMatcher.matches(photo.ocrText, query)
            if (smartMatch || literalOcrMatch) {
                filtered += photo
            }
        }

        cancellationCheck?.invoke()
        val sorted = searchRanker.rank(
            records = filtered,
            tokens = tokens,
            normalizedQuery = normalized,
            context = context,
        )

        cancellationCheck?.invoke()
        val finalResults = if (tokens.any { it is TemporalToken && it.keyword == "recent" }) {
            sorted.take(50)
        } else {
            sorted
        }

        cancellationCheck?.invoke()
        return SearchResult(finalResults, tokens).also {
            cache[key] = it
        }
    }

    private companion object {
        private const val CANCELLATION_CHECK_INTERVAL = 64
    }
}
