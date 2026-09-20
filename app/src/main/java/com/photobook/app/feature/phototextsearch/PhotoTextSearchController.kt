package com.photobook.app.feature.phototextsearch

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PhotoTextSearchController(
    private val source: PhotoTextLayoutSource,
    private val scope: CoroutineScope,
    private val matcher: PhotoTextMatcher = PhotoTextMatcher(),
) {
    private val _state = MutableStateFlow(PhotoTextSearchState())
    val state: StateFlow<PhotoTextSearchState> = _state.asStateFlow()

    private var activeSource: PhotoTextSourceKey? = null
    private var sessionRevision = 0L
    private var requestRevision = 0L
    private var queryRevision = 0L
    private var searchBlocks: List<SearchBlock> = emptyList()
    private var matchJob: Job? = null
    private var slowJob: Job? = null
    private val loadRequests = Channel<LoadRequest>(capacity = Channel.CONFLATED)
    private val workerJob = scope.launch {
        for (request in loadRequests) {
            processLoad(request)
        }
    }

    fun activate(photoId: Long, uriString: String) {
        val next = PhotoTextSourceKey(photoId = photoId, uriString = uriString)
        if (activeSource == next) return
        activeSource = next
        sessionRevision += 1L
        requestRevision += 1L
        queryRevision += 1L
        matchJob?.cancel()
        slowJob?.cancel()
        searchBlocks = emptyList()
        _state.value = PhotoTextSearchState()
    }

    fun open() {
        val current = activeSource ?: run {
            _state.value = PhotoTextSearchState(phase = PhotoTextSearchPhase.UNAVAILABLE)
            return
        }
        if (_state.value.phase == PhotoTextSearchPhase.PREPARING) return
        if (_state.value.phase == PhotoTextSearchPhase.READY && _state.value.layout != null) return
        enqueueRead(current, preserveQuery = _state.value.query)
    }

    fun retry() {
        val current = activeSource ?: return
        enqueueRead(current, preserveQuery = _state.value.query)
    }

    fun setQuery(text: String) {
        queryRevision += 1L
        val tooLong = text.length > MAX_QUERY_CHARACTERS
        _state.update { current ->
            current.copy(
                query = text,
                queryTooLong = tooLong,
                matches = if (tooLong || text.isBlank()) emptyList() else current.matches,
                activeMatchIndex = if (tooLong || text.isBlank()) -1 else current.activeMatchIndex,
            )
        }
        if (tooLong || text.isBlank()) {
            matchJob?.cancel()
            return
        }
        if (_state.value.phase == PhotoTextSearchPhase.READY) {
            scheduleMatch()
        }
    }

    fun nextMatch() {
        _state.update { current ->
            if (current.matches.isEmpty()) return@update current
            current.copy(
                activeMatchIndex = (current.activeMatchIndex + 1).floorMod(current.matches.size),
            )
        }
    }

    fun previousMatch() {
        _state.update { current ->
            if (current.matches.isEmpty()) return@update current
            current.copy(
                activeMatchIndex = (current.activeMatchIndex - 1).floorMod(current.matches.size),
            )
        }
    }

    fun close() {
        requestRevision += 1L
        queryRevision += 1L
        matchJob?.cancel()
        slowJob?.cancel()
        searchBlocks = emptyList()
        _state.value = PhotoTextSearchState()
    }

    fun dispose() {
        close()
        loadRequests.close()
        workerJob.cancel()
    }

    private fun enqueueRead(
        key: PhotoTextSourceKey,
        preserveQuery: String,
    ) {
        requestRevision += 1L
        val request = LoadRequest(
            sessionRevision = sessionRevision,
            requestRevision = requestRevision,
            source = key,
        )
        queryRevision += 1L
        matchJob?.cancel()
        searchBlocks = emptyList()
        _state.value = PhotoTextSearchState(
            phase = PhotoTextSearchPhase.PREPARING,
            query = preserveQuery,
        )
        slowJob?.cancel()
        slowJob = scope.launch {
            delay(SLOW_READING_MESSAGE_MS)
            if (isCurrent(request) && _state.value.phase == PhotoTextSearchPhase.PREPARING) {
                _state.update { it.copy(isSlow = true) }
            }
        }
        loadRequests.trySend(request)
    }

    private suspend fun processLoad(request: LoadRequest) {
        val result = try {
            source.load(request.source)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            PhotoTextLayoutLoadResult.Failed
        } catch (_: LinkageError) {
            PhotoTextLayoutLoadResult.Failed
        }

        if (!isCurrent(request)) return
        slowJob?.cancel()

        when (result) {
            is PhotoTextLayoutLoadResult.Success -> {
                val blocks = withContext(Dispatchers.Default) {
                    matcher.buildBlocks(result.layout)
                }
                if (!isCurrent(request)) return
                searchBlocks = blocks
                _state.update {
                    it.copy(
                        phase = PhotoTextSearchPhase.READY,
                        layout = result.layout,
                        matches = emptyList(),
                        activeMatchIndex = -1,
                        isSlow = false,
                    )
                }
                if (_state.value.query.isNotBlank() && !_state.value.queryTooLong) {
                    scheduleMatch()
                }
            }

            PhotoTextLayoutLoadResult.Unavailable -> {
                _state.update {
                    it.copy(
                        phase = PhotoTextSearchPhase.UNAVAILABLE,
                        layout = null,
                        matches = emptyList(),
                        activeMatchIndex = -1,
                        isSlow = false,
                    )
                }
            }

            PhotoTextLayoutLoadResult.Failed -> {
                _state.update {
                    it.copy(
                        phase = PhotoTextSearchPhase.FAILED,
                        layout = null,
                        matches = emptyList(),
                        activeMatchIndex = -1,
                        isSlow = false,
                    )
                }
            }
        }
    }

    private fun scheduleMatch() {
        matchJob?.cancel()
        queryRevision += 1L
        val revision = queryRevision
        val request = requestRevision
        val session = sessionRevision
        val rawQuery = _state.value.query
        val blocks = searchBlocks

        matchJob = scope.launch {
            delay(MATCH_DEBOUNCE_MS)
            val normalized = matcher.normalizeQuery(rawQuery)
            val hits = if (normalized.isBlank()) {
                emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    matcher.find(blocks, normalized)
                }
            }
            if (
                revision != queryRevision ||
                request != requestRevision ||
                session != sessionRevision ||
                _state.value.phase != PhotoTextSearchPhase.READY
            ) {
                return@launch
            }
            _state.update {
                it.copy(
                    matches = hits,
                    activeMatchIndex = if (hits.isEmpty()) -1 else 0,
                )
            }
        }
    }

    private fun isCurrent(request: LoadRequest): Boolean {
        return request.sessionRevision == sessionRevision &&
            request.requestRevision == requestRevision &&
            request.source == activeSource
    }

    private data class LoadRequest(
        val sessionRevision: Long,
        val requestRevision: Long,
        val source: PhotoTextSourceKey,
    )

    private fun Int.floorMod(modulus: Int): Int {
        val remainder = this % modulus
        return if (remainder < 0) remainder + modulus else remainder
    }

    companion object {
        const val MAX_QUERY_CHARACTERS = 128
        private const val MATCH_DEBOUNCE_MS = 100L
        private const val SLOW_READING_MESSAGE_MS = 5_000L
    }
}
