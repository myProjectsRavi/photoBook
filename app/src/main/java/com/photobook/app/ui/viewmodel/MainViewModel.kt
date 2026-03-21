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
import com.photobook.app.search.SuggestionEngine
import com.photobook.app.util.Constants
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
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
        val suggestions: List<String> = emptyList(),
        val showSuggestions: Boolean = false,
        val viewerStartIndex: Int? = null,
    )

    val uiState = MutableStateFlow(UiState())

    private val queryFlow = MutableStateFlow("")
    private val focusFlow = MutableStateFlow(false)

    private var hasInitializedIndex = false
    private var mediaObserver: ContentObserver? = null

    init {
        observeSearch()
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
        uiState.update { it.copy(query = query) }
    }

    fun onSearchSubmitted() {
        addToHistory(queryFlow.value)
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

    fun onSuggestionSelected(suggestion: String) {
        queryFlow.value = suggestion
        addToHistory(suggestion)
        uiState.update {
            it.copy(
                query = suggestion,
                showSuggestions = false,
            )
        }
    }

    fun onClearQuery() {
        queryFlow.value = ""
        uiState.update {
            it.copy(
                query = "",
                showSuggestions = focusFlow.value && it.suggestions.isNotEmpty(),
            )
        }
    }

    fun onPhotoClicked(index: Int) {
        uiState.update { it.copy(viewerStartIndex = index) }
    }

    fun onViewerPageChanged(index: Int) {
        uiState.update { it.copy(viewerStartIndex = index) }
    }

    fun closeViewer() {
        uiState.update { it.copy(viewerStartIndex = null) }
    }

    @OptIn(FlowPreview::class)
    private fun observeSearch() {
        viewModelScope.launch {
            combine(
                queryFlow.debounce(Constants.SEARCH_DEBOUNCE_MS),
                photoIndex.records(),
                focusFlow,
            ) { query, records, focused ->
                Triple(query, records, focused)
            }.collect { (query, records, focused) ->
                val searchResult = filterEngine.search(
                    query = query,
                    records = records,
                    context = buildSearchContext(),
                )
                val suggestions = suggestionEngine.getSuggestions(query, readHistory())

                uiState.update {
                    it.copy(
                        query = query,
                        photoCount = records.size,
                        results = searchResult.results,
                        suggestions = suggestions,
                        showSuggestions = focused && suggestions.isNotEmpty(),
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

    private fun readHistory(): List<String> {
        val raw = sharedPreferences.getString(Constants.SEARCH_HISTORY_KEY, "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val value = array.optString(i)
                    if (value.isNotBlank()) add(value)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun addToHistory(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return

        val current = readHistory().toMutableList()
        current.removeAll { it.equals(normalized, ignoreCase = true) }
        current.add(0, normalized)
        val next = current.take(20)

        val array = JSONArray()
        next.forEach { array.put(it) }
        sharedPreferences.edit().putString(Constants.SEARCH_HISTORY_KEY, array.toString()).apply()
    }

    private fun registerMediaObserver() {
        if (mediaObserver != null) return

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                super.onChange(selfChange)
                viewModelScope.launch {
                    val rebuilt = indexBuilder.buildIndex()
                    photoIndex.setRecords(rebuilt)
                    indexPersistence.save(rebuilt)
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
        mediaObserver?.let { observer ->
            context.contentResolver.unregisterContentObserver(observer)
        }
        mediaObserver = null
        super.onCleared()
    }
}
