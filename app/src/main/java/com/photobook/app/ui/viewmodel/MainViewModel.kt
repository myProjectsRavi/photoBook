package com.photobook.app.ui.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.photobook.app.data.index.IndexBuilder
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.data.model.RawPhotoData
import com.photobook.app.data.source.MediaStoreScanner
import com.photobook.app.feature.archive.ArchiveCandidate
import com.photobook.app.feature.archive.ArchiveDueDeleteItem
import com.photobook.app.feature.archive.ArchiveService
import com.photobook.app.feature.declutter.DeclutterCandidate
import com.photobook.app.feature.declutter.DeclutterReason
import com.photobook.app.feature.declutter.DeclutterSession
import com.photobook.app.feature.duplicates.DuplicatePhotoFinder
import com.photobook.app.feature.duplicates.DuplicatePhotoGroup
import com.photobook.app.feature.duplicates.DuplicateMatchKind
import com.photobook.app.feature.memories.MemoryCurator
import com.photobook.app.feature.memories.MemoryStory
import com.photobook.app.ml.TaggingWorker
import com.photobook.app.search.FilterEngine
import com.photobook.app.search.FolderToken
import com.photobook.app.search.LocationToken
import com.photobook.app.search.MLTagToken
import com.photobook.app.search.PhotoSource
import com.photobook.app.search.QueryParser
import com.photobook.app.search.QueryToken
import com.photobook.app.search.SearchContext
import com.photobook.app.search.SearchEngineV2
import com.photobook.app.search.SuggestionEngine
import com.photobook.app.search.SuggestionItem
import com.photobook.app.search.TextToken
import com.photobook.app.search.TokenClassifier
import com.photobook.app.search.UtilityKind
import com.photobook.app.search.isUtilityPhoto
import com.photobook.app.search.utilityKind
import com.photobook.app.ui.model.HomeFeedMode
import com.photobook.app.ui.model.TimelineMark
import com.photobook.app.util.Constants
import com.photobook.app.util.PermissionUtils
import com.photobook.app.widget.OnThisDayWidgetProvider
import com.photobook.app.worker.ArchiveRetentionWorker
import com.photobook.app.worker.ArchiveScanWorker
import com.photobook.app.worker.TrashPurgeWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val indexBuilder: IndexBuilder,
    private val mediaStoreScanner: MediaStoreScanner,
    private val photoIndex: PhotoIndex,
    private val indexPersistence: IndexPersistence,
    private val filterEngine: FilterEngine,
    private val searchEngineV2: SearchEngineV2,
    private val queryParser: QueryParser,
    private val tokenClassifier: TokenClassifier,
    private val suggestionEngine: SuggestionEngine,
    private val duplicatePhotoFinder: DuplicatePhotoFinder,
    private val archiveService: ArchiveService,
    private val memoryCurator: MemoryCurator,
    private val sharedPreferences: SharedPreferences,
) : ViewModel() {

    data class UiState(
        val hasPhotoPermission: Boolean = false,
        val photoAccessMode: PermissionUtils.PhotoAccessMode = PermissionUtils.PhotoAccessMode.None,
        val isIndexing: Boolean = false,
        val indexProgress: Float = 0f,
        val searchReady: Boolean = false,
        val query: String = "",
        val photoCount: Int = 0,
        val resultCount: Int = 0,
        val favoritesOnly: Boolean = false,
        val selectedPhotoIds: Set<Long> = emptySet(),
        val feedMode: HomeFeedMode = HomeFeedMode.Timeline,
        val reelsEnabled: Boolean = false,
        val suggestions: List<SuggestionItem> = emptyList(),
        val showSuggestions: Boolean = false,
        val onThisDayStory: MemoryStory? = null,
        val memoryStories: List<MemoryStory> = emptyList(),
        val viewerStartIndex: Int? = null,
        val viewerPhotos: List<PhotoRecord> = emptyList(),
        val viewerUsesVisibleWindow: Boolean = false,
        val reelsStartIndex: Int? = null,
        val reelsPhotos: List<PhotoRecord> = emptyList(),
        val storyViewerTitle: String = "",
        val storyViewerPhotos: List<PhotoRecord> = emptyList(),
        val timelineMarks: List<TimelineMark> = emptyList(),
        val duplicateGroups: List<DuplicatePhotoGroup> = emptyList(),
        val isFindingDuplicates: Boolean = false,
        val showDuplicateFinder: Boolean = false,
        val isDeclutterLoading: Boolean = false,
        val declutterSession: DeclutterSession? = null,
        val declutterCurrentPhoto: PhotoRecord? = null,
        val showArchives: Boolean = false,
        val isArchivesLoading: Boolean = false,
        val archiveCandidates: List<ArchiveCandidate> = emptyList(),
        val archiveSelectedPhotoIds: Set<Long> = emptySet(),
        val archiveRetentionDays: Int = 30,
        val archiveDueDeleteCount: Int = 0,
        val archivesEnabled: Boolean = false,
        val archivePaymentsEnabled: Boolean = true,
        val archiveFoodEnabled: Boolean = false,
    )

    val uiState = MutableStateFlow(UiState())

    private val queryFlow = MutableStateFlow("")
    private val focusFlow = MutableStateFlow(false)

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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
            searchResult.orderedIds.filter { id ->
                photoIndex.getByIdFromSnapshot(records, id)?.isFavorite == true
            }
        } else {
            searchResult.orderedIds
        }

        latestSearchResultIds = searchResult.orderedIds
        latestVisibleResultIds = filteredIds

        val timelineMarks = withContext(Dispatchers.Default) {
            buildTimelineMarks(filteredIds, records)
        }

        uiState.update { state ->
            state.copy(
                photoCount = records.size,
                resultCount = filteredIds.size,
                selectedPhotoIds = clampSelectionToResultIds(state.selectedPhotoIds, filteredIds),
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

    private var hasInitializedIndex = false
    private var mediaObserver: ContentObserver? = null
    private var mediaRebuildJob: Job? = null
    private val memoryRefreshRequests = Channel<List<PhotoRecord>>(capacity = Channel.CONFLATED)
    private val memoryRefreshJob: Job = viewModelScope.launch(Dispatchers.Default) {
        for (records in memoryRefreshRequests) {
            val curated = memoryCurator.curate(records)
            val onThisDay = memoryCurator.curateOnThisDay(records)
            uiState.update { state ->
                state.copy(
                    memoryStories = curated,
                    onThisDayStory = onThisDay,
                )
            }
            OnThisDayWidgetProvider.cacheStory(context, onThisDay)
        }
    }
    private var latestSearchResultIds: List<Long> = emptyList()
    private var latestVisibleResultIds: List<Long> = emptyList()
    private var lastMemoryRecordsIdentity: Int = 0
    private var pendingStoryLaunch: PendingStoryLaunch? = null

    private data class SearchFlowInput(
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

    private data class PendingStoryLaunch(
        val photoIds: List<Long>,
        val title: String,
    )

    init {
        uiState.update {
            it.copy(reelsEnabled = sharedPreferences.getBoolean(REELS_ENABLED_KEY, false))
        }
        observeSuggestions()
    }

    fun refreshPermissionStatus(accessMode: PermissionUtils.PhotoAccessMode) {
        val granted = accessMode != PermissionUtils.PhotoAccessMode.None
        uiState.update {
            it.copy(
                hasPhotoPermission = granted,
                photoAccessMode = accessMode,
            )
        }
        if (granted && !hasInitializedIndex) {
            hasInitializedIndex = true
            initializeIndex()
        }
    }

    fun applyExternalQuery(query: String) {
        val normalized = query.trim()
        if (normalized.isBlank()) return
        queryFlow.value = normalized
        uiState.update { state ->
            state.copy(
                query = normalized,
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                viewerUsesVisibleWindow = false,
                showSuggestions = false,
            )
        }
    }

    fun openStoryFromPhotoIds(photoIds: List<Long>, title: String) {
        if (!openStoryFromIdsInternal(photoIds, title)) {
            pendingStoryLaunch = PendingStoryLaunch(photoIds = photoIds, title = title)
        }
    }

    fun onQueryChanged(query: String) {
        queryFlow.value = query
        uiState.update {
            it.copy(
                query = query,
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                viewerUsesVisibleWindow = false,
                storyViewerPhotos = emptyList(),
                storyViewerTitle = "",
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

    fun onSelectFeedMode(mode: HomeFeedMode) {
        uiState.update { state ->
            state.copy(
                feedMode = mode,
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                viewerUsesVisibleWindow = false,
            )
        }
    }

    fun onSourceSelected(source: PhotoSource) {
        val query = "source:${source.token}"
        queryFlow.value = query
        uiState.update {
            it.copy(
                query = query,
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                viewerUsesVisibleWindow = false,
                showSuggestions = false,
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
                viewerUsesVisibleWindow = false,
                showSuggestions = false,
            )
        }
    }

    fun onMemoryStorySelected(story: MemoryStory) {
        if (openStoryFromIdsInternal(story.photoIds, story.title)) return
        val query = story.suggestedQuery.trim()
        if (query.isBlank()) return
        applyExternalQuery(query)
    }

    fun onOpenOnThisDayStory() {
        val story = uiState.value.onThisDayStory ?: return
        onMemoryStorySelected(story)
    }

    fun onClearQuery() {
        queryFlow.value = ""
        uiState.update {
            it.copy(
                query = "",
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                viewerUsesVisibleWindow = false,
                showSuggestions = focusFlow.value && it.suggestions.isNotEmpty(),
            )
        }
    }

    fun onPhotoClicked(photo: PhotoRecord) {
        var openedViewer = false
        viewModelScope.launch {
            val viewerWindow = withContext(Dispatchers.Default) {
                resolveVisiblePhotoWindow(photo.id)
            }

            uiState.update { state ->
                if (state.selectedPhotoIds.isNotEmpty()) {
                    val nextSelected = state.selectedPhotoIds.toMutableSet().apply {
                        if (!add(photo.id)) remove(photo.id)
                    }
                    state.copy(selectedPhotoIds = nextSelected)
                } else {
                    if (viewerWindow == null) {
                        state
                    } else {
                        openedViewer = true
                        state.copy(
                            viewerStartIndex = viewerWindow.startIndex,
                            viewerPhotos = viewerWindow.photos,
                            viewerUsesVisibleWindow = true,
                        )
                    }
                }
            }

            if (openedViewer) {
                runCatching { TaggingWorker.enqueueFocusedPhoto(context, photo.id) }
            }
        }
    }

    fun openPhotoById(photoId: Long) {
        viewModelScope.launch {
            val photo = photoIndex.getById(photoId) ?: return@launch
            clearSelection()
            onPhotoClicked(photo)
        }
    }

    fun onPhotoLongPressed(photo: PhotoRecord) {
        uiState.update { state ->
            val nextSelected = state.selectedPhotoIds.toMutableSet().apply {
                if (!add(photo.id)) remove(photo.id)
            }
            state.copy(
                selectedPhotoIds = nextSelected,
                viewerStartIndex = null,
                viewerUsesVisibleWindow = false,
            )
        }
    }

    fun onToggleFavorite(photoId: Long) {
        viewModelScope.launch {
            val isFavorite = photoIndex.toggleFavorite(photoId)
            indexPersistence.setFavorite(photoId, isFavorite)
            uiState.update { state ->
                state.copy(
                    viewerPhotos = state.viewerPhotos.map { photo ->
                        if (photo.id == photoId) photo.copy(isFavorite = isFavorite) else photo
                    },
                    duplicateGroups = state.duplicateGroups.map { group ->
                        group.copy(
                            photos = group.photos.map { photo ->
                                if (photo.id == photoId) photo.copy(isFavorite = isFavorite) else photo
                            }
                        )
                    },
                    archiveCandidates = if (isFavorite) {
                        state.archiveCandidates.filterNot { candidate -> candidate.photo.id == photoId }
                    } else {
                        state.archiveCandidates.map { candidate ->
                            if (candidate.photo.id == photoId) {
                                candidate.copy(photo = candidate.photo.copy(isFavorite = false))
                            } else {
                                candidate
                            }
                        }
                    },
                    archiveSelectedPhotoIds = if (isFavorite) {
                        state.archiveSelectedPhotoIds - photoId
                    } else {
                        state.archiveSelectedPhotoIds
                    },
                )
            }
        }
    }

    fun onToggleFavoritesOnly() {
        uiState.update { it.copy(favoritesOnly = !it.favoritesOnly) }
    }

    /** Enables/disables Instagram Reels-style vertical photo browsing in the viewer. */
    fun onToggleReelsEnabled() {
        val next = !uiState.value.reelsEnabled
        uiState.update { it.copy(reelsEnabled = next) }
        runCatching {
            sharedPreferences.edit().putBoolean(REELS_ENABLED_KEY, next).apply()
        }
    }

    /**
     * Returns the home feed to its pristine default: timeline view, no query,
     * no favorites filter, no selection. Wired to the PhotoBook logo for instant
     * "take me home" navigation.
     */
    fun resetToDefaultView() {
        queryFlow.value = ""
        uiState.update {
            it.copy(
                query = "",
                favoritesOnly = false,
                feedMode = HomeFeedMode.Timeline,
                selectedPhotoIds = emptySet(),
                viewerStartIndex = null,
                viewerPhotos = emptyList(),
                viewerUsesVisibleWindow = false,
                showSuggestions = false,
                showDuplicateFinder = false,
                showArchives = false,
                archiveSelectedPhotoIds = emptySet(),
            )
        }
    }

    fun clearSelection() {
        uiState.update { it.copy(selectedPhotoIds = emptySet()) }
    }

    fun onPhotosMovedToTrash(photoIds: Set<Long>) {
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            removePhotosAfterTrash(photoIds)
        }
    }

    fun onArchivePhotosMovedToTrash(photoIds: Set<Long>, retentionDays: Int) {
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            archiveService.markTrashed(photoIds, retentionDays)
            removePhotosAfterTrash(photoIds)
            loadArchiveSummary(refreshCandidates = false)
        }
    }

    fun onViewerPhotoChanged(photoId: Long) {
        val state = uiState.value
        val currentIndex = state.viewerPhotos.indexOfFirst { photo -> photo.id == photoId }
        if (currentIndex < 0) return
        runCatching { TaggingWorker.enqueueFocusedPhoto(context, photoId) }
        if (
            state.viewerUsesVisibleWindow &&
            shouldRecentreViewerWindow(currentIndex, state.viewerPhotos.size)
        ) {
            val window = resolveVisiblePhotoWindow(photoId)
            if (window != null) {
                uiState.update {
                    it.copy(
                        viewerStartIndex = window.startIndex,
                        viewerPhotos = window.photos,
                        viewerUsesVisibleWindow = true,
                    )
                }
                return
            }
        }
        uiState.update { it.copy(viewerStartIndex = currentIndex) }
    }

    fun closeViewer() {
        uiState.update {
            it.copy(
                viewerStartIndex = null,
                viewerPhotos = emptyList(),
                viewerUsesVisibleWindow = false,
            )
        }
    }

    fun openReels(startIndex: Int = 0) {
        viewModelScope.launch {
            val photos = withContext(Dispatchers.Default) { resolveVisiblePhotos() }
            if (photos.isEmpty()) return@launch
            uiState.update {
                it.copy(
                    reelsStartIndex = startIndex.coerceIn(0, photos.lastIndex),
                    reelsPhotos = photos,
                )
            }
        }
    }

    fun closeReels() {
        uiState.update {
            it.copy(
                reelsStartIndex = null,
                reelsPhotos = emptyList(),
            )
        }
    }

    fun closeStoryViewer() {
        uiState.update { state ->
            state.copy(
                storyViewerPhotos = emptyList(),
                storyViewerTitle = "",
            )
        }
    }

    fun openStoryPhoto(photo: PhotoRecord) {
        closeStoryViewer()
        onPhotoClicked(photo)
    }

    fun openArchives() {
        uiState.update {
            it.copy(
                showArchives = true,
                isArchivesLoading = true,
                archiveSelectedPhotoIds = emptySet(),
            )
        }
        viewModelScope.launch {
            loadArchiveSummary(
                refreshCandidates = true,
                preselectCandidates = true,
            )
        }
    }

    fun refreshArchives() {
        viewModelScope.launch {
            uiState.update { it.copy(isArchivesLoading = true) }
            runCatching {
                // The explicit refresh action is the user's request for a complete local scan.
                // Keep all MediaStore, EXIF, Room, and classifier work off the main thread.
                syncMediaStoreIncremental(forceFullSync = true)
                loadArchiveSummary(
                    refreshCandidates = true,
                    preselectCandidates = true,
                    fullLibraryScan = true,
                )
            }.onFailure {
                // A revoked/partial MediaStore permission must leave the dialog usable.
                uiState.update { state -> state.copy(isArchivesLoading = false) }
            }
        }
    }

    fun dismissArchives() {
        uiState.update {
            it.copy(
                showArchives = false,
                isArchivesLoading = false,
                archiveSelectedPhotoIds = emptySet(),
            )
        }
    }

    fun setArchiveRetentionDays(days: Int) {
        viewModelScope.launch {
            val normalized = archiveService.setRetentionDays(days)
            uiState.update { it.copy(archiveRetentionDays = normalized) }
        }
    }

    fun setArchivesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            uiState.update { state ->
                state.copy(
                    archivesEnabled = enabled,
                    isArchivesLoading = enabled,
                    archiveCandidates = if (enabled) state.archiveCandidates else emptyList(),
                    archiveSelectedPhotoIds = emptySet(),
                    archiveDueDeleteCount = if (enabled) state.archiveDueDeleteCount else 0,
                )
            }
            val summary = archiveService.setEnabled(enabled)
            if (enabled) {
                ArchiveScanWorker.enqueueDaily(context)
                ArchiveRetentionWorker.enqueueDaily(context)
            } else {
                ArchiveScanWorker.cancel(context)
                ArchiveRetentionWorker.cancel(context)
            }
            applyArchiveSummary(
                summary = summary,
                preselectCandidates = enabled,
            )
        }
    }

    fun setArchivePaymentsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            uiState.update { state ->
                state.copy(
                    archivePaymentsEnabled = enabled,
                    isArchivesLoading = state.archivesEnabled,
                    archiveSelectedPhotoIds = emptySet(),
                )
            }
            val summary = archiveService.setPaymentsEnabled(enabled)
            applyArchiveSummary(
                summary = summary,
                preselectCandidates = summary.enabled,
            )
        }
    }

    fun setArchiveFoodEnabled(enabled: Boolean) {
        viewModelScope.launch {
            uiState.update { state ->
                state.copy(
                    archiveFoodEnabled = enabled,
                    isArchivesLoading = state.archivesEnabled,
                    archiveSelectedPhotoIds = emptySet(),
                )
            }
            val summary = archiveService.setFoodEnabled(enabled)
            applyArchiveSummary(
                summary = summary,
                preselectCandidates = summary.enabled,
            )
        }
    }

    fun toggleArchiveCandidateSelection(photoId: Long) {
        uiState.update { state ->
            val nextSelected = state.archiveSelectedPhotoIds.toMutableSet().apply {
                if (!add(photoId)) remove(photoId)
            }
            state.copy(archiveSelectedPhotoIds = nextSelected)
        }
    }

    fun selectAllArchiveCandidates() {
        uiState.update { state ->
            state.copy(archiveSelectedPhotoIds = state.archiveCandidates.map { candidate -> candidate.photo.id }.toSet())
        }
    }

    fun clearArchiveCandidateSelection() {
        uiState.update { it.copy(archiveSelectedPhotoIds = emptySet()) }
    }

    fun keepSelectedArchiveCandidates() {
        val selectedIds = uiState.value.archiveSelectedPhotoIds
        if (selectedIds.isEmpty()) return
        viewModelScope.launch {
            archiveService.markKept(selectedIds)
            loadArchiveSummary(refreshCandidates = false)
        }
    }

    fun keepArchiveCandidate(photoId: Long) {
        viewModelScope.launch {
            archiveService.markKept(setOf(photoId))
            loadArchiveSummary(refreshCandidates = false)
        }
    }

    fun resolveArchivePhotosByIds(photoIds: Set<Long>): List<PhotoRecord> {
        if (photoIds.isEmpty()) return emptyList()
        val byId = uiState.value.archiveCandidates.associateBy { candidate -> candidate.photo.id }
        return photoIds.mapNotNull { id -> byId[id]?.photo }
    }

    suspend fun archiveManagedTrashPhotoIds(): Set<Long> {
        return archiveService.archivedTrashPhotoIds()
    }

    suspend fun resolveArchiveDueDeleteItems(): List<ArchiveDueDeleteItem> {
        return archiveService.dueDeleteItems()
    }

    fun onArchiveDueItemsDeleted(photoIds: Set<Long>) {
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            archiveService.markDueDeleted(photoIds)
            loadArchiveSummary(refreshCandidates = false)
        }
    }

    fun openDeclutterSwipe() {
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    isDeclutterLoading = true,
                    declutterSession = null,
                    declutterCurrentPhoto = null,
                )
            }

            val records = photoIndex.snapshot()
            val groups = if (uiState.value.duplicateGroups.isNotEmpty()) {
                uiState.value.duplicateGroups
            } else {
                duplicatePhotoFinder.findDuplicates(records)
            }

            val candidates = withContext(Dispatchers.Default) {
                buildDeclutterCandidates(records, groups)
            }
            val session = DeclutterSession(candidates = candidates)
            uiState.update { state ->
                state.copy(
                    duplicateGroups = if (state.duplicateGroups.isEmpty()) groups else state.duplicateGroups,
                    isDeclutterLoading = false,
                    declutterSession = session,
                    declutterCurrentPhoto = resolveDeclutterCurrentPhoto(session, records),
                )
            }
        }
    }

    fun dismissDeclutterSwipe() {
        uiState.update {
            it.copy(
                isDeclutterLoading = false,
                declutterSession = null,
                declutterCurrentPhoto = null,
            )
        }
    }

    fun onDeclutterKeepCurrent() {
        mutateDeclutterSession(markTrash = false)
    }

    fun onDeclutterTrashCurrent() {
        mutateDeclutterSession(markTrash = true)
    }

    fun onDeclutterUndo() {
        uiState.update { state ->
            val session = state.declutterSession ?: return@update state
            if (session.currentIndex <= 0 || session.candidates.isEmpty()) return@update state
            val previousIndex = session.currentIndex - 1
            val previousCandidate = session.candidates.getOrNull(previousIndex) ?: return@update state
            val nextSession = session.copy(
                currentIndex = previousIndex,
                markedTrashIds = session.markedTrashIds - previousCandidate.photoId,
                keptIds = session.keptIds - previousCandidate.photoId,
            )
            state.copy(
                declutterSession = nextSession,
                declutterCurrentPhoto = resolveDeclutterCurrentPhoto(nextSession, photoIndex.snapshot()),
            )
        }
    }

    fun openDuplicateFinder() {
        uiState.update { it.copy(showDuplicateFinder = true) }
        if (uiState.value.duplicateGroups.isEmpty() && !uiState.value.isFindingDuplicates) {
            refreshDuplicateGroups()
        }
    }

    fun refreshDuplicateGroups() {
        viewModelScope.launch {
            uiState.update {
                it.copy(
                    isFindingDuplicates = true,
                    showDuplicateFinder = true,
                )
            }
            val groups = duplicatePhotoFinder.findDuplicates(photoIndex.snapshot())
            uiState.update {
                it.copy(
                    duplicateGroups = groups,
                    isFindingDuplicates = false,
                )
            }
        }
    }

    fun dismissDuplicateFinder() {
        uiState.update { it.copy(showDuplicateFinder = false) }
    }

    fun openDuplicatePhoto(groupId: String, index: Int) {
        uiState.update { state ->
            val group = state.duplicateGroups.firstOrNull { it.id == groupId } ?: return@update state
            if (index !in group.photos.indices) return@update state
            state.copy(
                viewerStartIndex = index,
                viewerPhotos = group.photos,
                viewerUsesVisibleWindow = false,
                showDuplicateFinder = false,
            )
        }
    }

    fun resolvePhotosByIds(photoIds: Set<Long>): List<PhotoRecord> {
        if (photoIds.isEmpty()) return emptyList()
        val orderedVisible = latestVisibleResultIds.filter { id -> id in photoIds }
        val orderedIds = linkedSetOf<Long>()
        orderedVisible.forEach(orderedIds::add)
        photoIds.forEach(orderedIds::add)
        return photoIndex.getByIdsOrdered(orderedIds.toList())
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

            syncMediaStoreIncremental(forceFullSync = photoIndex.snapshot().isEmpty())

            uiState.update {
                it.copy(
                    isIndexing = false,
                    indexProgress = 1f,
                    searchReady = true,
                )
            }
            tryConsumePendingStoryLaunch()

            TaggingWorker.enqueueLibraryMaintenance(context)
            TrashPurgeWorker.enqueueDaily(context)
            if (archiveService.isEnabled()) {
                ArchiveScanWorker.enqueueDaily(context)
                ArchiveRetentionWorker.enqueueDaily(context)
            } else {
                ArchiveScanWorker.cancel(context)
                ArchiveRetentionWorker.cancel(context)
            }
            loadArchiveSummary(refreshCandidates = true)
            registerMediaObserver()
        }
    }

    private suspend fun loadArchiveSummary(
        refreshCandidates: Boolean,
        preselectCandidates: Boolean = false,
        fullLibraryScan: Boolean = false,
    ) {
        val summary = if (refreshCandidates) {
            if (fullLibraryScan) {
                val finalSummary = archiveService.refreshAllCandidates { partialSummary ->
                    applyArchiveSummary(
                        summary = partialSummary,
                        preselectCandidates = preselectCandidates,
                        isLoading = true,
                    )
                }
                applyArchiveSummary(
                    summary = finalSummary,
                    preselectCandidates = preselectCandidates,
                    isLoading = false,
                )
                return
            } else {
                archiveService.refreshCandidates()
            }
        } else {
            archiveService.loadSummary()
        }
        applyArchiveSummary(
            summary = summary,
            preselectCandidates = preselectCandidates,
            isLoading = false,
        )
    }

    private fun applyArchiveSummary(
        summary: com.photobook.app.feature.archive.ArchiveSummary,
        preselectCandidates: Boolean,
        isLoading: Boolean = false,
    ) {
        val candidateIds = summary.candidates.map { candidate -> candidate.photo.id }.toSet()
        uiState.update { state ->
            val clampedSelection = state.archiveSelectedPhotoIds.intersect(candidateIds)
            state.copy(
                isArchivesLoading = isLoading,
                archiveCandidates = summary.candidates,
                archiveSelectedPhotoIds = if (preselectCandidates && summary.enabled) {
                    candidateIds
                } else {
                    clampedSelection
                },
                archiveRetentionDays = summary.retentionDays,
                archiveDueDeleteCount = summary.dueDeleteCount,
                archivesEnabled = summary.enabled,
                archivePaymentsEnabled = summary.paymentsEnabled,
                archiveFoodEnabled = summary.foodEnabled,
            )
        }
    }

    private suspend fun removePhotosAfterTrash(photoIds: Set<Long>) {
        photoIndex.removeRecords(photoIds)
        indexPersistence.removeByIds(photoIds)
        latestSearchResultIds = latestSearchResultIds.filterNot { id -> id in photoIds }
        latestVisibleResultIds = latestVisibleResultIds.filterNot { id -> id in photoIds }

        uiState.update { state ->
            val nextDeclutterSession = state.declutterSession?.let { session ->
                val filteredCandidates = session.candidates.filterNot { candidate -> candidate.photoId in photoIds }
                if (filteredCandidates.isEmpty()) {
                    null
                } else {
                    val clampedIndex = session.currentIndex.coerceAtMost(filteredCandidates.lastIndex.coerceAtLeast(0))
                    session.copy(
                        candidates = filteredCandidates,
                        currentIndex = clampedIndex,
                        markedTrashIds = session.markedTrashIds - photoIds,
                        keptIds = session.keptIds - photoIds,
                    )
                }
            }
            state.copy(
                selectedPhotoIds = state.selectedPhotoIds - photoIds,
                archiveCandidates = state.archiveCandidates.filterNot { candidate -> candidate.photo.id in photoIds },
                archiveSelectedPhotoIds = state.archiveSelectedPhotoIds - photoIds,
                viewerStartIndex = null,
                viewerPhotos = emptyList(),
                viewerUsesVisibleWindow = false,
                duplicateGroups = state.duplicateGroups
                    .map { group ->
                        group.copy(photos = group.photos.filterNot { photo -> photo.id in photoIds })
                    }
                    .filter { group -> group.photos.size > 1 },
                declutterSession = nextDeclutterSession,
                declutterCurrentPhoto = nextDeclutterSession?.let { session ->
                    resolveDeclutterCurrentPhoto(session, photoIndex.snapshot())
                },
            )
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
                    syncMediaStoreIncremental(forceFullSync = false)
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

    private suspend fun syncMediaStoreIncremental(forceFullSync: Boolean) {
        withContext(Dispatchers.IO) {
            val existing = photoIndex.snapshot()
            val currentVersion = mediaStoreScanner.currentMediaStoreVersion()
            val currentGeneration = mediaStoreScanner.currentGenerationOrNull()
            val lastVersion = sharedPreferences.getString(Constants.MEDIA_STORE_VERSION_KEY, null)
            val lastGeneration = sharedPreferences.getLong(Constants.MEDIA_STORE_GENERATION_KEY, -1L)
                .takeIf { value -> value >= 0L }

            val shouldFullSync = forceFullSync || existing.isEmpty() || lastVersion == null || currentVersion != lastVersion
            if (shouldFullSync) {
                rebuildEntireIndex(existing)
                persistMediaStoreSyncState(currentVersion, currentGeneration)
                return@withContext
            }

            if (!shouldFullSync && lastGeneration != null && currentGeneration != null) {
                if (currentGeneration > lastGeneration) {
                    processGenerationDelta(existing, lastGeneration)
                }
                persistMediaStoreSyncState(currentVersion, currentGeneration)
                return@withContext
            }

            if (!shouldFullSync) {
                processLegacyDelta(existing)
            }
            persistMediaStoreSyncState(currentVersion, currentGeneration)
        }
    }

    private suspend fun rebuildEntireIndex(existing: List<PhotoRecord>) {
        val rebuilt = indexBuilder.buildIndex { processed, total ->
            if (total <= 0) return@buildIndex
            uiState.update {
                it.copy(indexProgress = processed.toFloat() / total.toFloat())
            }
        }.preservingIntelligence(existing)

        if (existing.isEmpty()) {
            // On a first build, keep full Room/FTS persistence from overlapping structural
            // PhotoIndex publication and the search/memory flows that publication wakes up.
            indexPersistence.save(rebuilt)
            photoIndex.setRecords(rebuilt)
        } else {
            // Preserve the established full-resync publication ordering for existing libraries.
            photoIndex.setRecords(rebuilt)
            indexPersistence.save(rebuilt)
        }
    }

    private suspend fun processGenerationDelta(
        existing: List<PhotoRecord>,
        lastGeneration: Long,
    ) {
        val changedRaw = mediaStoreScanner.scanChangedSince(lastGeneration)
        val allMediaIds = mediaStoreScanner.scanAllIds()
        applyDelta(existing, changedRaw, allMediaIds)
    }

    private suspend fun processLegacyDelta(existing: List<PhotoRecord>) {
        val allRaw = mediaStoreScanner.scanAll()
        val changedRaw = changedRawPhotosForLegacySync(allRaw, existing)
        val allMediaIds = allRaw.asSequence().map { raw -> raw.id }.toSet()
        applyDelta(existing, changedRaw, allMediaIds)
    }

    private suspend fun applyDelta(
        existing: List<PhotoRecord>,
        changedRaw: List<RawPhotoData>,
        allMediaIds: Set<Long>,
    ) {
        val existingById = existing.associateBy { record -> record.id }
        val removedIds = existingById.keys - allMediaIds

        val changedRebuilt = if (changedRaw.isNotEmpty()) {
            indexBuilder.buildIndexFromRaw(changedRaw).preservingIntelligence(existing)
        } else {
            emptyList()
        }

        if (changedRebuilt.isEmpty() && removedIds.isEmpty()) {
            return
        }

        val merged = existingById.toMutableMap()
        changedRebuilt.forEach { record ->
            merged[record.id] = record
        }
        removedIds.forEach { id ->
            merged.remove(id)
        }

        val updatedRecords = merged.values.sortedByDescending { record -> record.dateAdded }
        photoIndex.setRecords(updatedRecords)

        if (changedRebuilt.isNotEmpty()) {
            indexPersistence.upsertAll(changedRebuilt)
        }
        if (removedIds.isNotEmpty()) {
            indexPersistence.removeByIds(removedIds)
        }

        TaggingWorker.enqueueLibraryMaintenance(context)
    }

    private fun changedRawPhotosForLegacySync(
        allRaw: List<RawPhotoData>,
        existing: List<PhotoRecord>,
    ): List<RawPhotoData> {
        if (allRaw.isEmpty()) return emptyList()
        val existingById = existing.associateBy { record -> record.id }
        return allRaw.filter { raw ->
            val previous = existingById[raw.id] ?: return@filter true
            rawDiffersFromRecord(raw, previous)
        }
    }

    private fun rawDiffersFromRecord(raw: RawPhotoData, existing: PhotoRecord): Boolean {
        return raw.uriString != existing.uriString ||
            raw.filePath != existing.filePath ||
            raw.fileName != existing.fileName ||
            raw.dateAdded != existing.dateAdded ||
            raw.fileSize != existing.fileSize ||
            raw.width != existing.width ||
            raw.height != existing.height ||
            raw.mimeType != existing.mimeType ||
            raw.folderName.lowercase() != existing.folderName ||
            raw.folderPath.lowercase() != existing.folderPath
    }

    private fun persistMediaStoreSyncState(version: String, generation: Long?) {
        sharedPreferences.edit()
            .putString(Constants.MEDIA_STORE_VERSION_KEY, version)
            .apply {
                if (generation != null) {
                    putLong(Constants.MEDIA_STORE_GENERATION_KEY, generation)
                } else {
                    remove(Constants.MEDIA_STORE_GENERATION_KEY)
                }
            }
            .apply()
    }

    override fun onCleared() {
        mediaRebuildJob?.cancel()
        memoryRefreshRequests.close()
        memoryRefreshJob.cancel()
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
                perceptualHash = existing.perceptualHash,
                blurScore = existing.blurScore,
                mlTags = existing.mlTags,
                isArchiveFoodCandidate = existing.isArchiveFoodCandidate,
                isMlProcessed = existing.isMlProcessed,
                mlStatus = existing.mlStatus,
                ocrText = existing.ocrText,
                isOcrProcessed = existing.isOcrProcessed,
                ocrStatus = existing.ocrStatus,
            )
        }
    }

    private fun clampSelectionToResultIds(
        selectedPhotoIds: Set<Long>,
        visibleResultIds: List<Long>,
    ): Set<Long> {
        if (selectedPhotoIds.isEmpty() || visibleResultIds.isEmpty()) return emptySet()
        val visibleSelectedIds = HashSet<Long>(selectedPhotoIds.size * 4 / 3 + 1)
        visibleResultIds.forEach { id ->
            if (id in selectedPhotoIds) visibleSelectedIds.add(id)
        }
        if (visibleSelectedIds.isEmpty()) return emptySet()
        return selectedPhotoIds.filterTo(linkedSetOf()) { it in visibleSelectedIds }
    }

    private fun normalizeViewerIndex(currentIndex: Int?, resultSize: Int): Int? {
        if (currentIndex == null || resultSize <= 0) return null
        return currentIndex.coerceIn(0, resultSize - 1)
    }

    private fun resolveVisiblePhotos(): List<PhotoRecord> {
        return photoIndex.getByIdsOrdered(latestVisibleResultIds)
    }

    private data class ViewerWindow(
        val photos: List<PhotoRecord>,
        val startIndex: Int,
    )

    private fun resolveVisiblePhotoWindow(centerPhotoId: Long): ViewerWindow? {
        if (latestVisibleResultIds.isEmpty()) return null
        val centerIndex = latestVisibleResultIds.indexOf(centerPhotoId)
        if (centerIndex < 0) return null

        val start = (centerIndex - VIEWER_WINDOW_RADIUS).coerceAtLeast(0)
        val endExclusive = (centerIndex + VIEWER_WINDOW_RADIUS + 1).coerceAtMost(latestVisibleResultIds.size)
        val windowIds = latestVisibleResultIds.subList(start, endExclusive)
        val photos = photoIndex.getByIdsOrdered(windowIds)
        val localIndex = photos.indexOfFirst { photo -> photo.id == centerPhotoId }
        if (photos.isEmpty() || localIndex < 0) return null

        return ViewerWindow(
            photos = photos,
            startIndex = localIndex,
        )
    }

    private fun shouldRecentreViewerWindow(currentIndex: Int, windowSize: Int): Boolean {
        if (windowSize <= VIEWER_WINDOW_RECENTER_THRESHOLD * 2 + 1) return false
        return currentIndex <= VIEWER_WINDOW_RECENTER_THRESHOLD ||
            currentIndex >= windowSize - VIEWER_WINDOW_RECENTER_THRESHOLD - 1
    }

    private fun maybeRefreshMemoryStories(records: List<PhotoRecord>) {
        val identity = System.identityHashCode(records)
        if (identity == lastMemoryRecordsIdentity) return
        lastMemoryRecordsIdentity = identity
        memoryRefreshRequests.trySend(records)
    }

    private suspend fun runSearch(
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

    private fun shouldUseDaoCandidateSearch(tokens: List<QueryToken>): Boolean {
        if (tokens.isEmpty()) return false
        return tokens.any { token ->
            when (token) {
                is TextToken -> true
                is MLTagToken -> true
                is FolderToken -> true
                is LocationToken -> token.keyword !in setOf("near_me", "here", "home", "office", "abroad")
                else -> false
            }
        }
    }

    private fun buildTimelineMarks(
        orderedIds: List<Long>,
        records: List<PhotoRecord>,
    ): List<TimelineMark> {
        if (orderedIds.isEmpty()) return emptyList()
        val marks = ArrayList<TimelineMark>()
        var lastYear = Int.MIN_VALUE
        var lastMonth = Int.MIN_VALUE

        orderedIds.forEachIndexed { index, id ->
            val record = photoIndex.getByIdFromSnapshot(records, id) ?: return@forEachIndexed
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

    private fun monthLabel(month: Int): String {
        return when (month) {
            1 -> "Jan"
            2 -> "Feb"
            3 -> "Mar"
            4 -> "Apr"
            5 -> "May"
            6 -> "Jun"
            7 -> "Jul"
            8 -> "Aug"
            9 -> "Sep"
            10 -> "Oct"
            11 -> "Nov"
            12 -> "Dec"
            else -> "Month"
        }
    }

    private fun openStoryFromIdsInternal(photoIds: List<Long>, title: String): Boolean {
        if (photoIds.isEmpty() || photoIndex.size() == 0) return false
        val photos = photoIndex.getByIdsOrdered(photoIds)
        if (photos.isEmpty()) return false

        uiState.update { state ->
            state.copy(
                storyViewerTitle = title.ifBlank { state.storyViewerTitle.ifBlank { "Memory" } },
                storyViewerPhotos = photos,
                viewerStartIndex = null,
                viewerPhotos = emptyList(),
                viewerUsesVisibleWindow = false,
                selectedPhotoIds = emptySet(),
            )
        }
        return true
    }

    private fun tryConsumePendingStoryLaunch() {
        val pending = pendingStoryLaunch ?: return
        if (openStoryFromIdsInternal(pending.photoIds, pending.title)) {
            pendingStoryLaunch = null
        }
    }

    private fun mutateDeclutterSession(markTrash: Boolean) {
        uiState.update { state ->
            val session = state.declutterSession ?: return@update state
            val candidate = session.currentCandidate ?: return@update state
            val nextSession = session.copy(
                currentIndex = (session.currentIndex + 1).coerceAtMost(session.candidates.size),
                markedTrashIds = if (markTrash) {
                    session.markedTrashIds + candidate.photoId
                } else {
                    session.markedTrashIds - candidate.photoId
                },
                keptIds = if (markTrash) {
                    session.keptIds - candidate.photoId
                } else {
                    session.keptIds + candidate.photoId
                },
            )
            state.copy(
                declutterSession = nextSession,
                declutterCurrentPhoto = resolveDeclutterCurrentPhoto(nextSession, photoIndex.snapshot()),
            )
        }
    }

    private fun resolveDeclutterCurrentPhoto(
        session: DeclutterSession,
        records: List<PhotoRecord>,
    ): PhotoRecord? {
        val candidate = session.currentCandidate ?: return null
        return records.firstOrNull { photo -> photo.id == candidate.photoId }
    }

    private fun buildDeclutterCandidates(
        records: List<PhotoRecord>,
        groups: List<DuplicatePhotoGroup>,
    ): List<DeclutterCandidate> {
        val candidates = LinkedHashMap<Long, DeclutterReason>()

        groups.forEach { group ->
            val keeperId = group.heroPhotoId ?: group.photos.maxByOrNull { photo -> photo.fileSize }?.id
            group.photos.forEach { photo ->
                val reason = when (group.kind) {
                    DuplicateMatchKind.Exact -> {
                        if (photo.id == keeperId) null else DeclutterReason.ExactDuplicate
                    }

                    DuplicateMatchKind.Similar -> {
                        if (photo.id == keeperId) null else DeclutterReason.SimilarDuplicate
                    }

                    DuplicateMatchKind.Burst -> {
                        if (photo.id == keeperId) null else DeclutterReason.BurstExtra
                    }

                    DuplicateMatchKind.Blurry -> DeclutterReason.Blurry
                }
                if (reason != null) {
                    candidates.putIfAbsent(photo.id, reason)
                }
            }
        }

        records.forEach { photo ->
            val utilityReason = when (photo.utilityKind()) {
                UtilityKind.Screenshot -> DeclutterReason.Screenshot
                UtilityKind.Download -> DeclutterReason.Download
                UtilityKind.Social -> DeclutterReason.Social
                UtilityKind.Document -> DeclutterReason.Document
                UtilityKind.Meme -> DeclutterReason.Meme
                null -> null
            }
            if (utilityReason != null) {
                candidates.putIfAbsent(photo.id, utilityReason)
            }
        }

        return candidates.entries
            .asSequence()
            .take(MAX_DECLUTTER_CANDIDATES)
            .map { (photoId, reason) ->
                DeclutterCandidate(
                    photoId = photoId,
                    reason = reason,
                )
            }
            .toList()
    }

    companion object {
        private const val SEARCH_PAGE_SIZE = 60
        private const val SEARCH_PREFETCH_DISTANCE = 20
        private const val VIEWER_WINDOW_RADIUS = 50
        private const val VIEWER_WINDOW_RECENTER_THRESHOLD = 12
        private const val RECORDS_UPDATE_DEBOUNCE_MS = 250L
        private const val MAX_DECLUTTER_CANDIDATES = 300
        private const val REELS_ENABLED_KEY = "reels_enabled_v1"
        private val SEARCH_RUNTIME_STRATEGY = SearchRuntimeStrategy.V2
    }
}
