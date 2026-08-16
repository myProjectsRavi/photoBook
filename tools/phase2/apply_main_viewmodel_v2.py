#!/usr/bin/env python3
from pathlib import Path

PATH = Path("app/src/main/java/com/photobook/app/ui/viewmodel/MainViewModel.kt")
text = PATH.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"expected exactly one match, found {count}: {old[:100]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "import com.photobook.app.search.SearchContext\n",
    "import com.photobook.app.search.SearchContext\nimport com.photobook.app.search.SearchEngineV2\n",
)
replace_once(
    "    private val filterEngine: FilterEngine,\n    private val queryParser: QueryParser,",
    "    private val filterEngine: FilterEngine,\n    private val searchEngineV2: SearchEngineV2,\n    private val queryParser: QueryParser,",
)

replace_once(
'''    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagedResults: kotlinx.coroutines.flow.Flow<PagingData<PhotoRecord>> = combine(
        queryFlow.debounce(Constants.SEARCH_DEBOUNCE_MS),
        photoIndex.records().debounce(RECORDS_UPDATE_DEBOUNCE_MS),
        uiState.map { it.favoritesOnly }.distinctUntilChanged(),
        uiState.map { it.feedMode }.distinctUntilChanged(),
    ) { query, records, favoritesOnly, feedMode ->
        SearchFlowInput(
            query = query,
            records = records,
            favoritesOnly = favoritesOnly,
            feedMode = feedMode,
        )
    }.flatMapLatest { input ->
        val searchResult = runSearch(
            query = input.query,
            records = input.records,
        )
        val filteredRecords = if (input.favoritesOnly) {
            searchResult.results.filter { it.isFavorite }
        } else {
            searchResult.results
        }
        val filteredIds = filteredRecords.map { it.id }

        latestSearchResultIds = searchResult.results.map { it.id }
        latestVisibleResultIds = filteredIds

        val timelineMarks = withContext(Dispatchers.Default) {
            buildTimelineMarks(filteredRecords)
        }
        val visibleIds = filteredIds.toSet()

        uiState.update { state ->
            state.copy(
                photoCount = input.records.size,
                resultCount = filteredIds.size,
                selectedPhotoIds = clampSelectionToResultIds(state.selectedPhotoIds, visibleIds),
                timelineMarks = timelineMarks,
                searchReady = !state.isIndexing,
                viewerStartIndex = normalizeViewerIndex(
                    currentIndex = state.viewerStartIndex,
                    resultSize = if (state.viewerPhotos.isNotEmpty()) state.viewerPhotos.size else filteredIds.size,
                ),
            )
        }
        maybeRefreshMemoryStories(input.records)

        Pager(
            config = PagingConfig(
                pageSize = SEARCH_PAGE_SIZE,
                initialLoadSize = SEARCH_PAGE_SIZE * 2,
                prefetchDistance = SEARCH_PREFETCH_DISTANCE,
                enablePlaceholders = true,
            ),
            pagingSourceFactory = {
                SearchResultsPagingSource(
                    orderedPhotoIds = filteredIds,
                    indexPersistence = indexPersistence,
                )
            },
        ).flow
    }.cachedIn(viewModelScope)
''',
'''    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val pagedResults: kotlinx.coroutines.flow.Flow<PagingData<PhotoRecord>> = combine(
        queryFlow.debounce(Constants.SEARCH_DEBOUNCE_MS),
        photoIndex.changes().debounce(RECORDS_UPDATE_DEBOUNCE_MS),
        uiState.map { it.favoritesOnly }.distinctUntilChanged(),
        uiState.map { it.feedMode }.distinctUntilChanged(),
    ) { query, indexVersion, favoritesOnly, feedMode ->
        SearchFlowInput(
            query = query,
            indexVersion = indexVersion,
            favoritesOnly = favoritesOnly,
            feedMode = feedMode,
        )
    }.flatMapLatest { input ->
        // Read one immutable library view for this generation. Search v2 also receives the
        // generation number and fails closed to the legacy snapshot if the index changes mid-run.
        val records = photoIndex.snapshot()
        val searchResult = runSearch(
            query = input.query,
            records = records,
            expectedIndexVersion = input.indexVersion,
        )
        val filteredIds = if (input.favoritesOnly) {
            searchResult.orderedIds.filter { id -> photoIndex.getById(id)?.isFavorite == true }
        } else {
            searchResult.orderedIds
        }

        latestSearchResultIds = searchResult.orderedIds
        latestVisibleResultIds = filteredIds

        val timelineMarks = withContext(Dispatchers.Default) {
            buildTimelineMarks(filteredIds)
        }
        val visibleIds = filteredIds.toSet()

        uiState.update { state ->
            state.copy(
                photoCount = records.size,
                resultCount = filteredIds.size,
                selectedPhotoIds = clampSelectionToResultIds(state.selectedPhotoIds, visibleIds),
                timelineMarks = timelineMarks,
                searchReady = !state.isIndexing,
                viewerStartIndex = normalizeViewerIndex(
                    currentIndex = state.viewerStartIndex,
                    resultSize = if (state.viewerPhotos.isNotEmpty()) state.viewerPhotos.size else filteredIds.size,
                ),
            )
        }
        maybeRefreshMemoryStories(records)

        Pager(
            config = PagingConfig(
                pageSize = SEARCH_PAGE_SIZE,
                initialLoadSize = SEARCH_PAGE_SIZE * 2,
                prefetchDistance = SEARCH_PREFETCH_DISTANCE,
                enablePlaceholders = true,
            ),
            pagingSourceFactory = {
                SearchResultsPagingSource(
                    orderedPhotoIds = filteredIds,
                    indexPersistence = indexPersistence,
                )
            },
        ).flow
    }.cachedIn(viewModelScope)
''',
)

replace_once(
'''    private data class SearchFlowInput(
        val query: String,
        val records: List<PhotoRecord>,
        val favoritesOnly: Boolean,
        val feedMode: HomeFeedMode,
    )
''',
'''    private data class SearchFlowInput(
        val query: String,
        val indexVersion: Long,
        val favoritesOnly: Boolean,
        val feedMode: HomeFeedMode,
    )

    private data class SearchIdResult(
        val orderedIds: List<Long>,
        val tokens: List<QueryToken>,
    )

    private enum class SearchRuntimeStrategy {
        LEGACY,
        V2,
    }
''',
)

replace_once(
'''    fun openPhotoById(photoId: Long) {
        viewModelScope.launch {
            val photo = photoIndex.snapshot().firstOrNull { it.id == photoId } ?: return@launch
            clearSelection()
            onPhotoClicked(photo)
        }
    }
''',
'''    fun openPhotoById(photoId: Long) {
        viewModelScope.launch {
            val photo = photoIndex.getById(photoId) ?: return@launch
            clearSelection()
            onPhotoClicked(photo)
        }
    }
''',
)

replace_once(
'''    fun resolvePhotosByIds(photoIds: Set<Long>): List<PhotoRecord> {
        if (photoIds.isEmpty()) return emptyList()
        val byId = photoIndex.snapshot().associateBy { record -> record.id }
        val orderedVisible = latestVisibleResultIds.filter { id -> id in photoIds }
        val orderedIds = linkedSetOf<Long>()
        orderedVisible.forEach(orderedIds::add)
        photoIds.forEach(orderedIds::add)
        return orderedIds.mapNotNull(byId::get)
    }
''',
'''    fun resolvePhotosByIds(photoIds: Set<Long>): List<PhotoRecord> {
        if (photoIds.isEmpty()) return emptyList()
        val orderedVisible = latestVisibleResultIds.filter { id -> id in photoIds }
        val orderedIds = linkedSetOf<Long>()
        orderedVisible.forEach(orderedIds::add)
        photoIds.forEach(orderedIds::add)
        return photoIndex.getByIdsOrdered(orderedIds.toList())
    }
''',
)

replace_once(
'''    private fun resolveVisiblePhotos(): List<PhotoRecord> {
        if (latestVisibleResultIds.isEmpty()) return emptyList()
        val byId = photoIndex.snapshot().associateBy { record -> record.id }
        return latestVisibleResultIds.mapNotNull(byId::get)
    }
''',
'''    private fun resolveVisiblePhotos(): List<PhotoRecord> {
        return photoIndex.getByIdsOrdered(latestVisibleResultIds)
    }
''',
)

replace_once(
'''        val windowIds = latestVisibleResultIds.subList(start, endExclusive)
        val byId = photoIndex.snapshot().associateBy { record -> record.id }
        val photos = windowIds.mapNotNull(byId::get)
''',
'''        val windowIds = latestVisibleResultIds.subList(start, endExclusive)
        val photos = photoIndex.getByIdsOrdered(windowIds)
''',
)

replace_once(
'''    private suspend fun runSearch(
        query: String,
        records: List<PhotoRecord>,
    ): FilterEngine.SearchResult {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            // Timeline is the single feed mode now — shows all photos (incl. screenshots) chronologically.
            return FilterEngine.SearchResult(
                results = records,
                tokens = emptyList(),
            )
        }

        val typedTokens = queryParser.tokenize(normalizedQuery).map(tokenClassifier::classify)
        val shouldUseDao = shouldUseDaoCandidateSearch(typedTokens)
        val daoCandidates = if (shouldUseDao) {
            indexPersistence.searchByQueryText(normalizedQuery)
        } else {
            emptyList()
        }
        val recordsToSearch = if (daoCandidates.isNotEmpty()) daoCandidates else records

        return withContext(Dispatchers.Default) {
            filterEngine.search(
                query = query,
                records = recordsToSearch,
                context = buildSearchContext(),
            )
        }
    }
''',
'''    private suspend fun runSearch(
        query: String,
        records: List<PhotoRecord>,
        expectedIndexVersion: Long,
    ): SearchIdResult {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            // Timeline is the single feed mode now — shows all photos (incl. screenshots) chronologically.
            return SearchIdResult(
                orderedIds = records.map { record -> record.id },
                tokens = emptyList(),
            )
        }

        val typedTokens = queryParser.tokenize(normalizedQuery).map(tokenClassifier::classify)
        val shouldUseDao = shouldUseDaoCandidateSearch(typedTokens)
        val context = buildSearchContext()

        if (SEARCH_RUNTIME_STRATEGY == SearchRuntimeStrategy.V2) {
            val daoCandidateIds = if (shouldUseDao) {
                indexPersistence.searchIdsByQueryText(normalizedQuery)
            } else {
                emptyList()
            }
            // Preserve the legacy empty-FTS fallback: an empty candidate set means search the
            // current in-memory library rather than incorrectly returning no results.
            val candidateIds = daoCandidateIds.takeIf { it.isNotEmpty() }
            val v2Result = withContext(Dispatchers.Default) {
                searchEngineV2.search(
                    query = query,
                    candidateIds = candidateIds,
                    context = context,
                    expectedIndexVersion = expectedIndexVersion,
                )
            }
            if (v2Result.complete) {
                return SearchIdResult(
                    orderedIds = v2Result.orderedIds,
                    tokens = v2Result.tokens,
                )
            }
        }

        // Temporary internal rollback path. Keep this until 10k/50k/100k parity and release
        // verification are green; it is not a user-facing or permanent dual architecture.
        val daoCandidates = if (shouldUseDao) {
            indexPersistence.searchByQueryText(normalizedQuery)
        } else {
            emptyList()
        }
        val recordsToSearch = if (daoCandidates.isNotEmpty()) daoCandidates else records
        val legacyResult = withContext(Dispatchers.Default) {
            filterEngine.search(
                query = query,
                records = recordsToSearch,
                context = context,
            )
        }
        return SearchIdResult(
            orderedIds = legacyResult.results.map { record -> record.id },
            tokens = legacyResult.tokens,
        )
    }
''',
)

replace_once(
'''    private fun buildTimelineMarks(
        orderedRecords: List<PhotoRecord>,
    ): List<TimelineMark> {
        if (orderedRecords.isEmpty()) return emptyList()
        val marks = ArrayList<TimelineMark>()
        var lastYear = Int.MIN_VALUE
        var lastMonth = Int.MIN_VALUE

        orderedRecords.forEachIndexed { index, record ->
            if (record.year != lastYear || record.month != lastMonth) {
                marks += TimelineMark(
                    index = index,
                    label = "${monthLabel(record.month)} ${record.year}",
                )
                lastYear = record.year
                lastMonth = record.month
            }
        }
        return marks
    }
''',
'''    private fun buildTimelineMarks(
        orderedIds: List<Long>,
    ): List<TimelineMark> {
        if (orderedIds.isEmpty()) return emptyList()
        val marks = ArrayList<TimelineMark>()
        var lastYear = Int.MIN_VALUE
        var lastMonth = Int.MIN_VALUE

        orderedIds.forEachIndexed { index, id ->
            val record = photoIndex.getById(id) ?: return@forEachIndexed
            if (record.year != lastYear || record.month != lastMonth) {
                marks += TimelineMark(
                    index = index,
                    label = "${monthLabel(record.month)} ${record.year}",
                )
                lastYear = record.year
                lastMonth = record.month
            }
        }
        return marks
    }
''',
)

replace_once(
'''    private fun openStoryFromIdsInternal(photoIds: List<Long>, title: String): Boolean {
        if (photoIds.isEmpty()) return false
        val snapshot = photoIndex.snapshot()
        if (snapshot.isEmpty()) return false
        val byId = snapshot.associateBy { it.id }
        val photos = photoIds.mapNotNull(byId::get)
        if (photos.isEmpty()) return false
''',
'''    private fun openStoryFromIdsInternal(photoIds: List<Long>, title: String): Boolean {
        if (photoIds.isEmpty() || photoIndex.size() == 0) return false
        val photos = photoIndex.getByIdsOrdered(photoIds)
        if (photos.isEmpty()) return false
''',
)

replace_once(
    '        private const val REELS_ENABLED_KEY = "reels_enabled_v1"\n',
    '        private const val REELS_ENABLED_KEY = "reels_enabled_v1"\n        private val SEARCH_RUNTIME_STRATEGY = SearchRuntimeStrategy.V2\n',
)

PATH.write_text(text)
print(f"patched {PATH}")
