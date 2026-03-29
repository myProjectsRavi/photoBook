package com.photobook.app.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.photobook.app.data.index.IndexBuilder
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ml.TaggingWorker
import com.photobook.app.search.FilterEngine
import com.photobook.app.search.SearchContext
import com.photobook.app.search.SuggestionItem
import com.photobook.app.search.SuggestionEngine
import com.photobook.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexBuilder: IndexBuilder,
    private val photoIndex: PhotoIndex,
    private val indexPersistence: IndexPersistence,
    private val filterEngine: FilterEngine,
    private val suggestionEngine: SuggestionEngine,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    data class UiState(
        val hasPhotoPermission: Boolean = false,
        val isIndexing: Boolean = false,
        val indexProgress: Float = 0f,
        val searchReady: Boolean = false,
        val query: String = "",
        val photoCount: Int = 0,
        val results: List<PhotoRecord> = emptyList(),
        val favoritesOnly: Boolean = false,
        val selectedPhotoIds: Set<Long> = emptySet(),
        val suggestions: List<SuggestionItem> = emptyList(),
        val showSuggestions: Boolean = false,
        val viewerStartIndex: Int? = null,
    )

    val uiState = MutableStateFlow(UiState())

    private val queryFlow = MutableStateFlow("")
    private val focusFlow = MutableStateFlow(false)

    private var hasInitializedIndex = false
    private var mediaObserver: ContentObserver? = null
    private var mediaRebuildJob: Job? = null
    private var latestSearchResults: List<PhotoRecord> = emptyList()

    init {
        observeSearchResults()
        observeSuggestions()
    }

    fun refreshPermissionStatus(granted: Boolean) {
        uiState.update { it.copy(hasPhotoPermission = granted) }
        if (granted && !hasInitializedIndex) {
            hasInitializedIndex = true
            initializeIndex()
        }
    }

    fun onQueryChanged(query: String) {
        queryFlow.value = query
        uiState.update {
            it.copy(
                query = query,
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
            )
        }
    }

    fun onSearchSubmitted() {
        uiState.update { it.copy(showSuggestions = false) }
    }

    fun onSearchFocusChanged(focused: Boolean) {
        focusFlow.value = focused
        uiState.update { state ->
            state.copy(
                showSuggestions = focused && state.suggestions.isNotEmpty(),
            )
        }
    }

    fun onSuggestionSelected(suggestion: SuggestionItem) {
        queryFlow.value = suggestion.text
        uiState.update {
            it.copy(
                query = suggestion.text,
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                showSuggestions = false,
            )
        }
    }

    fun onRemoveHistorySuggestion(@Suppress("UNUSED_PARAMETER") suggestion: String) {
        val updatedSuggestions = suggestionEngine.getSuggestions(queryFlow.value)
        uiState.update {
            it.copy(
                suggestions = updatedSuggestions,
                showSuggestions = focusFlow.value && updatedSuggestions.isNotEmpty(),
            )
        }
    }

    fun onClearQuery() {
        queryFlow.value = ""
        uiState.update {
            it.copy(
                query = "",
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                showSuggestions = focusFlow.value && it.suggestions.isNotEmpty(),
            )
        }
    }

    fun onPhotoClicked(index: Int) {
        uiState.update { state ->
            val photo = state.results.getOrNull(index) ?: return@update state
            if (state.selectedPhotoIds.isNotEmpty()) {
                val nextSelected = state.selectedPhotoIds.toMutableSet().apply {
                    if (!add(photo.id)) remove(photo.id)
                }
                state.copy(selectedPhotoIds = nextSelected)
            } else {
                state.copy(viewerStartIndex = index)
            }
        }
    }

    fun onPhotoLongPressed(index: Int) {
        uiState.update { state ->
            val photo = state.results.getOrNull(index) ?: return@update state
            val nextSelected = state.selectedPhotoIds.toMutableSet().apply {
                if (!add(photo.id)) remove(photo.id)
            }
            state.copy(
                selectedPhotoIds = nextSelected,
                viewerStartIndex = null,
            )
        }
    }

    fun onToggleFavorite(photoId: Long) {
        viewModelScope.launch {
            photoIndex.toggleFavorite(photoId)
            indexPersistence.save(photoIndex.snapshot())
        }
    }

    fun onToggleFavoritesOnly() {
        uiState.update { state ->
            val nextFavoritesOnly = !state.favoritesOnly
            val filtered = applyFavoritesFilter(latestSearchResults, nextFavoritesOnly)
            val nextSelected = clampSelectionToResults(state.selectedPhotoIds, filtered)
            state.copy(
                favoritesOnly = nextFavoritesOnly,
                results = filtered,
                selectedPhotoIds = nextSelected,
                viewerStartIndex = normalizeViewerIndex(state.viewerStartIndex, filtered),
            )
        }
    }

    fun clearSelection() {
        uiState.update { it.copy(selectedPhotoIds = emptySet()) }
    }

    fun onViewerPageChanged(index: Int) {
        uiState.update { it.copy(viewerStartIndex = index) }
    }

    fun closeViewer() {
        uiState.update { it.copy(viewerStartIndex = null) }
    }

    private fun observeSuggestions() {
        viewModelScope.launch {
            combine(
                queryFlow,
                focusFlow,
            ) { query, focused ->
                Pair(query, focused)
            }.collect { (query, focused) ->
                val suggestions = suggestionEngine.getSuggestions(query)

                uiState.update {
                    it.copy(
                        query = query,
                        suggestions = suggestions,
                        showSuggestions = focused && suggestions.isNotEmpty(),
                    )
                }
            }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearchResults() {
        viewModelScope.launch {
            combine(
                queryFlow.debounce(Constants.SEARCH_DEBOUNCE_MS),
                photoIndex.records(),
            ) { query, records ->
                Pair(query, records)
            }.collect { (query, records) ->
                val normalizedQuery = query.trim()
                val searchResult = if (normalizedQuery.isBlank()) {
                    FilterEngine.SearchResult(
                        results = emptyList(),
                        tokens = emptyList(),
                    )
                } else {
                    filterEngine.search(
                        query = query,
                        records = records,
                        context = buildSearchContext(),
                    )
                }
                latestSearchResults = searchResult.results

                uiState.update {
                    val filtered = applyFavoritesFilter(latestSearchResults, it.favoritesOnly)
                    val nextSelected = clampSelectionToResults(it.selectedPhotoIds, filtered)
                    it.copy(
                        photoCount = records.size,
                        results = filtered,
                        selectedPhotoIds = nextSelected,
                        viewerStartIndex = normalizeViewerIndex(it.viewerStartIndex, filtered),
                        searchReady = !it.isIndexing,
                    )
                }
            }
        }
    }

    private fun initializeIndex() {
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    isIndexing = true,
                    indexProgress = 0f,
                    searchReady = false,
                )
            }

            val persisted = indexPersistence.load()
            if (persisted.isNotEmpty()) {
                photoIndex.setRecords(persisted)
            }

            if (photoIndex.snapshot().isEmpty()) {
                val built = indexBuilder.buildIndex { processed, total ->
                    if (total <= 0) return@buildIndex
                    uiState.update {
                        it.copy(indexProgress = processed.toFloat() / total.toFloat())
                    }
                }

                photoIndex.setRecords(built)
                indexPersistence.save(built)
            }

            uiState.update {
                it.copy(
                    isIndexing = false,
                    indexProgress = 1f,
                    searchReady = true,
                )
            }

            TaggingWorker.enqueue(context)
            registerMediaObserver()
        }
    }

    private fun buildSearchContext(): SearchContext {
        return SearchContext(
            homeLatitude = sharedPreferences.getString("home_lat", null)?.toDoubleOrNull(),
            homeLongitude = sharedPreferences.getString("home_lon", null)?.toDoubleOrNull(),
            officeLatitude = sharedPreferences.getString("office_lat", null)?.toDoubleOrNull(),
            officeLongitude = sharedPreferences.getString("office_lon", null)?.toDoubleOrNull(),
            homeCountry = sharedPreferences.getString("home_country", null),
            radiusKm = sharedPreferences.getString("search_radius_km", null)?.toDoubleOrNull() ?: 1.0,
        )
    }

    private fun registerMediaObserver() {
        if (mediaObserver != null) return

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                mediaRebuildJob?.cancel()
                mediaRebuildJob = viewModelScope.launch {
                    delay(Constants.MEDIA_OBSERVER_DEBOUNCE_MS)
                    val rebuilt = indexBuilder.buildIndex().preservingIntelligence(photoIndex.snapshot())
                    photoIndex.setRecords(rebuilt)
                    indexPersistence.save(rebuilt)
                    TaggingWorker.enqueue(context)
                }
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer,
        )
        mediaObserver = observer
    }

    override fun onCleared() {
        mediaRebuildJob?.cancel()
        mediaObserver?.let { observer ->
            context.contentResolver.unregisterContentObserver(observer)
        }
        mediaObserver = null
        super.onCleared()
    }

    private fun List<PhotoRecord>.preservingIntelligence(current: List<PhotoRecord>): List<PhotoRecord> {
        if (isEmpty() || current.isEmpty()) return this
        val byId = current.associateBy { it.id }
        return map { rebuilt ->
            val existing = byId[rebuilt.id] ?: return@map rebuilt
            rebuilt.copy(
                isFavorite = existing.isFavorite,
                mlTags = existing.mlTags,
                isMlProcessed = existing.isMlProcessed,
                ocrText = existing.ocrText,
                isOcrProcessed = existing.isOcrProcessed,
            )
        }
    }

    private fun applyFavoritesFilter(results: List<PhotoRecord>, favoritesOnly: Boolean): List<PhotoRecord> {
        if (!favoritesOnly) return results
        return results.filter { it.isFavorite }
    }

    private fun clampSelectionToResults(selectedPhotoIds: Set<Long>, results: List<PhotoRecord>): Set<Long> {
        if (selectedPhotoIds.isEmpty() || results.isEmpty()) return emptySet()
        val visibleIds = results.asSequence().map { it.id }.toSet()
        return selectedPhotoIds.filterTo(linkedSetOf()) { it in visibleIds }
    }

    private fun normalizeViewerIndex(currentIndex: Int?, results: List<PhotoRecord>): Int? {
        if (currentIndex == null || results.isEmpty()) return null
        return currentIndex.coerceIn(0, results.lastIndex)
    }
}
