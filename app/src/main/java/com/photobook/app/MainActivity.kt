package com.photobook.app

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.metadata.ExifMetadataService
import com.photobook.app.feature.metadata.SafeShareItem
import com.photobook.app.feature.metadata.SafeShareResult
import com.photobook.app.feature.pdf.PdfExportResult
import com.photobook.app.feature.pdf.PdfExportService
import com.photobook.app.feature.trash.TrashRequestResult
import com.photobook.app.feature.trash.TrashService
import com.photobook.app.feature.vault.VaultCryptoSession
import com.photobook.app.feature.vault.VaultExportResult
import com.photobook.app.feature.vault.VaultItem
import com.photobook.app.feature.vault.VaultSaveResult
import com.photobook.app.feature.vault.VaultService
import com.photobook.app.feature.vault.rememberVaultAuthenticator
import com.photobook.app.ui.screen.ArchivesScreen
import com.photobook.app.ui.screen.MainScreen
import com.photobook.app.ui.screen.OnboardingScreen
import com.photobook.app.ui.screen.PhotoViewerScreen
import com.photobook.app.ui.screen.MemoryStoryViewerScreen
import com.photobook.app.ui.screen.PhotoReelsScreen
import com.photobook.app.ui.screen.VaultBottomSheet
import com.photobook.app.ui.theme.PhotoBookTheme
import com.photobook.app.ui.viewmodel.MainViewModel
import com.photobook.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleLaunchIntent(intent)
        setContent {
            PhotoBookTheme(dynamicColor = false) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhotoBookApp(viewModel = vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        val storyIds = intent?.getStringExtra(EXTRA_WIDGET_STORY_IDS).orEmpty()
            .split(',')
            .mapNotNull { token -> token.trim().toLongOrNull() }
            .distinct()
        val storyTitle = intent?.getStringExtra(EXTRA_WIDGET_STORY_TITLE).orEmpty()
        if (storyIds.isNotEmpty()) {
            vm.openStoryFromPhotoIds(storyIds, storyTitle)
            return
        }
        val launchQuery = intent?.getStringExtra(EXTRA_LAUNCH_QUERY).orEmpty()
        if (launchQuery.isNotBlank()) {
            vm.applyExternalQuery(launchQuery)
        }
    }

    companion object {
        const val EXTRA_LAUNCH_QUERY = "extra_launch_query"
        const val EXTRA_WIDGET_STORY_IDS = "extra_widget_story_ids"
        const val EXTRA_WIDGET_STORY_TITLE = "extra_widget_story_title"
    }
}

@Composable
private fun PhotoBookApp(viewModel: MainViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pagedResults = viewModel.pagedResults.collectAsLazyPagingItems()
    val permissions = PermissionUtils.requiredPermissions()
    val coroutineScope = rememberCoroutineScope()
    val exifMetadataService = remember(context.applicationContext) {
        ExifMetadataService(context.applicationContext)
    }
    val pdfExportService = remember(context.applicationContext) {
        PdfExportService(context.applicationContext)
    }
    val trashService = remember(context.applicationContext) {
        TrashService(context.applicationContext)
    }
    val vaultService = remember(context.applicationContext) {
        VaultService(context.applicationContext)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingTrashPhotoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var pendingArchiveTrashRequest by remember { mutableStateOf(false) }
    var pendingArchiveRetentionDays by remember { mutableStateOf(30) }
    var pendingArchiveDueDeleteIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showVault by remember { mutableStateOf(false) }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }
    var isVaultLoading by remember { mutableStateOf(false) }
    var isVaultBusy by remember { mutableStateOf(false) }

    // Trash bin state
    var showTrashScreen by remember { mutableStateOf(false) }
    var trashedPhotos by remember { mutableStateOf<List<com.photobook.app.feature.trash.TrashedPhoto>>(emptyList()) }
    var isLoadingTrash by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissionStatus(PermissionUtils.photoAccessMode(context))
    }
    val trashRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val trashedIds = pendingTrashPhotoIds
        val fromArchives = pendingArchiveTrashRequest
        val retentionDays = pendingArchiveRetentionDays
        pendingTrashPhotoIds = emptySet()
        pendingArchiveTrashRequest = false
        pendingArchiveRetentionDays = 30
        if (trashedIds.isEmpty()) return@rememberLauncherForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            if (fromArchives) {
                viewModel.onArchivePhotosMovedToTrash(trashedIds, retentionDays)
            } else {
                viewModel.onPhotosMovedToTrash(trashedIds)
            }
            Toast.makeText(
                context,
                context.getString(R.string.trash_moved_success),
                Toast.LENGTH_SHORT,
            ).show()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.trash_request_cancelled),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Launcher used for both Restore and Delete-Forever flows in the Trash screen.
    val trashActionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val dueDeleteIds = pendingArchiveDueDeleteIds
        pendingArchiveDueDeleteIds = emptySet()
        if (result.resultCode == Activity.RESULT_OK && dueDeleteIds.isNotEmpty()) {
            viewModel.onArchiveDueItemsDeleted(dueDeleteIds)
        }
        coroutineScope.launch {
            isLoadingTrash = true
            val archiveManagedIds = viewModel.archiveManagedTrashPhotoIds()
            trashedPhotos = trashService.listTrashed()
                .filterNot { photo -> photo.id in archiveManagedIds }
            isLoadingTrash = false
        }
    }

    fun refreshTrashBin() {
        coroutineScope.launch {
            isLoadingTrash = true
            val archiveManagedIds = viewModel.archiveManagedTrashPhotoIds()
            trashedPhotos = trashService.listTrashed()
                .filterNot { photo -> photo.id in archiveManagedIds }
            isLoadingTrash = false
        }
    }

    fun openTrashBin() {
        showTrashScreen = true
        refreshTrashBin()
    }

    fun closeVault() {
        showVault = false
        vaultService.invalidatePreviewCache()
        vaultItems = emptyList()
        isVaultLoading = false
        isVaultBusy = false
        coroutineScope.launch {
            vaultService.clearPreviewCache()
        }
    }

    suspend fun loadVisibleVaultItems(session: VaultCryptoSession): List<VaultItem> {
        if (!showVault) return emptyList()
        val previewGeneration = vaultService.beginPreviewLoad()
        val items = vaultService.listItems(
            session = session,
            includePreviews = true,
            previewGeneration = previewGeneration,
        )
        return when {
            !showVault -> emptyList()
            vaultService.isPreviewLoadCurrent(previewGeneration) -> items
            else -> vaultItems
        }
    }

    fun refreshVault(session: VaultCryptoSession) {
        showVault = true
        coroutineScope.launch {
            isVaultLoading = true
            runCatching {
                loadVisibleVaultItems(session)
            }.onSuccess { items ->
                vaultItems = items
            }.onFailure {
                closeVault()
                Toast.makeText(
                    context,
                    context.getString(R.string.vault_biometric_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            isVaultLoading = false
        }
    }

    val authenticateVault = rememberVaultAuthenticator(vaultService)

    fun requestMoveToTrash(photos: List<PhotoRecord>, archiveRetentionDays: Int? = null) {
        if (photos.isEmpty()) return
        when (val request = trashService.createTrashRequest(photos)) {
            is TrashRequestResult.Ready -> {
                pendingTrashPhotoIds = photos.map { photo -> photo.id }.toSet()
                pendingArchiveTrashRequest = archiveRetentionDays != null
                pendingArchiveRetentionDays = archiveRetentionDays ?: 30
                val intentRequest = IntentSenderRequest.Builder(request.intentSender).build()
                trashRequestLauncher.launch(intentRequest)
            }

            TrashRequestResult.UnsupportedAndroid -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.trash_not_supported),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            is TrashRequestResult.Error -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.trash_request_error),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    fun movePhotosToVault(
        photos: List<PhotoRecord>,
        session: VaultCryptoSession,
    ) {
        if (photos.isEmpty()) return
        coroutineScope.launch {
            isVaultBusy = true
            when (val result = vaultService.addPhotos(photos = photos, session = session)) {
                is VaultSaveResult.Success -> {
                    val protectedPhotos = photos.filter { photo -> photo.id in result.addedPhotoIds }
                    Toast.makeText(
                        context,
                        context.getString(R.string.vault_move_success, protectedPhotos.size),
                        Toast.LENGTH_SHORT,
                    ).show()
                    viewModel.clearSelection()
                    vaultItems = runCatching {
                        loadVisibleVaultItems(session)
                    }.getOrDefault(emptyList())
                    if (protectedPhotos.isNotEmpty()) {
                        requestMoveToTrash(protectedPhotos)
                    }
                }

                is VaultSaveResult.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.vault_add_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            isVaultBusy = false
        }
    }

    fun addSelectedToVault(
        selectedIds: Set<Long>,
        session: VaultCryptoSession,
    ) {
        if (selectedIds.isEmpty()) return
        coroutineScope.launch {
            val selectedPhotos = viewModel.resolvePhotosByIds(selectedIds)
            movePhotosToVault(selectedPhotos, session)
        }
    }

    fun moveVaultItemOut(
        item: VaultItem,
        session: VaultCryptoSession,
    ) {
        coroutineScope.launch {
            isVaultBusy = true
            when (vaultService.exportToDevice(itemId = item.id, session = session)) {
                is VaultExportResult.Success -> {
                    val removedFromVault = vaultService.deleteItem(item.id)
                    vaultItems = runCatching {
                        loadVisibleVaultItems(session)
                    }.getOrDefault(emptyList())
                    Toast.makeText(
                        context,
                        context.getString(
                            if (removedFromVault) {
                                R.string.vault_move_out_success
                            } else {
                                R.string.vault_export_success
                            },
                        ),
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is VaultExportResult.Error -> Toast.makeText(
                    context,
                    context.getString(R.string.vault_export_error),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            isVaultBusy = false
        }
    }

    fun deleteVaultItem(
        item: VaultItem,
        session: VaultCryptoSession,
    ) {
        coroutineScope.launch {
            isVaultBusy = true
            val deleted = vaultService.deleteItem(item.id)
            if (deleted) {
                vaultItems = runCatching {
                    loadVisibleVaultItems(session)
                }.getOrDefault(emptyList())
            } else {
                Toast.makeText(
                    context,
                    context.getString(R.string.vault_delete_error),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            isVaultBusy = false
        }
    }

    DisposableEffect(showVault) {
        val activity = context as? Activity
        if (showVault) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    DisposableEffect(lifecycleOwner, showVault) {
        val observer = LifecycleEventObserver { _, event ->
            if (showVault && event == Lifecycle.Event.ON_STOP) {
                closeVault()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun shareSelectedPhotos(photos: List<PhotoRecord>) {
        if (photos.isEmpty()) return
        coroutineScope.launch {
            when (val result = exifMetadataService.createSafeShareCopies(photos)) {
                is SafeShareResult.Success -> {
                    if (result.items.isNotEmpty()) {
                        sharePhotos(context, result.items)
                        viewModel.clearSelection()
                    }
                }

                is SafeShareResult.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.safe_share_prepare_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    suspend fun createAndSharePdf(
        photos: List<PhotoRecord>,
        persistToDownloads: Boolean,
        clearSelectionOnSuccess: Boolean,
    ) {
        if (photos.isEmpty()) return
        Toast.makeText(
            context,
            context.getString(R.string.create_pdf_preparing),
            Toast.LENGTH_SHORT,
        ).show()
        val result = if (persistToDownloads) {
            pdfExportService.exportPhotos(photos)
        } else {
            pdfExportService.exportPhotosForSharing(photos)
        }
        when (result) {
            is PdfExportResult.Success -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.create_pdf_success, result.pageCount),
                    Toast.LENGTH_SHORT,
                ).show()
                sharePdf(context, result.uri, result.fileName)
                if (clearSelectionOnSuccess) {
                    viewModel.clearSelection()
                }
            }

            is PdfExportResult.PartialSuccess -> {
                Toast.makeText(
                    context,
                    context.getString(
                        R.string.create_pdf_partial_success,
                        result.pageCount,
                        result.skippedCount,
                    ),
                    Toast.LENGTH_LONG,
                ).show()
                sharePdf(context, result.uri, result.fileName)
                if (clearSelectionOnSuccess) {
                    viewModel.clearSelection()
                }
            }

            is PdfExportResult.TooManyPages -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.create_pdf_too_many_pages, result.maxAllowed),
                    Toast.LENGTH_SHORT,
                ).show()
            }

            is PdfExportResult.Error -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.create_pdf_error),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionStatus(PermissionUtils.photoAccessMode(context))
    }

    if (!uiState.hasPhotoPermission) {
        OnboardingScreen(
            isIndexing = false,
            progress = 0f,
            onGrantPermission = { permissionLauncher.launch(permissions.toTypedArray()) }
        )
        return
    }

    if (uiState.isIndexing) {
        OnboardingScreen(
            isIndexing = true,
            progress = uiState.indexProgress,
            onGrantPermission = {}
        )
        return
    }

    MainScreen(
        query = uiState.query,
        results = pagedResults,
        resultCount = uiState.resultCount,
        searchReady = uiState.searchReady,
        favoritesOnly = uiState.favoritesOnly,
        reelsEnabled = uiState.reelsEnabled,
        selectedPhotoIds = uiState.selectedPhotoIds,
        timelineMarks = uiState.timelineMarks,
        suggestions = uiState.suggestions,
        showSuggestions = uiState.showSuggestions,
        onThisDayStory = uiState.onThisDayStory,
        memoryStories = uiState.memoryStories,
        duplicateGroups = uiState.duplicateGroups,
        isFindingDuplicates = uiState.isFindingDuplicates,
        showDuplicateFinder = uiState.showDuplicateFinder,
        archiveCandidateCount = uiState.archiveCandidates.size,
        archiveDueDeleteCount = uiState.archiveDueDeleteCount,
        limitedPhotoAccess = uiState.photoAccessMode == PermissionUtils.PhotoAccessMode.Limited,
        onQueryChange = viewModel::onQueryChanged,
        onSearchSubmitted = viewModel::onSearchSubmitted,
        onSearchFocusChanged = viewModel::onSearchFocusChanged,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onClearQuery = viewModel::onClearQuery,
        onToggleFavoritesOnly = viewModel::onToggleFavoritesOnly,
        onToggleReels = viewModel::onToggleReelsEnabled,
        onLogoClick = viewModel::resetToDefaultView,
        onShareSelected = { selectedIds ->
            coroutineScope.launch {
                val selectedPhotos = viewModel.resolvePhotosByIds(selectedIds)
                shareSelectedPhotos(selectedPhotos)
            }
        },
        onMoveSelectedToTrash = { selectedIds ->
            coroutineScope.launch {
                val selectedPhotos = viewModel.resolvePhotosByIds(selectedIds)
                if (selectedPhotos.isNotEmpty()) {
                    requestMoveToTrash(selectedPhotos)
                }
            }
        },
        onCreatePdfSelected = { selectedIds ->
            coroutineScope.launch {
                val selectedPhotos = viewModel.resolvePhotosByIds(selectedIds)
                createAndSharePdf(
                    photos = selectedPhotos,
                    persistToDownloads = true,
                    clearSelectionOnSuccess = true,
                )
            }
        },
        onAddSelectedToVault = { selectedIds ->
            authenticateVault { session ->
                addSelectedToVault(selectedIds, session)
            }
        },
        onCopyTextFromPhoto = { photoId ->
            // Open the viewer on this photo so the user can access Copy Text from there
            viewModel.openPhotoById(photoId)
        },
        onClearSelection = viewModel::clearSelection,
        onPhotoClick = viewModel::onPhotoClicked,
        onPhotoLongClick = viewModel::onPhotoLongPressed,
        onOpenTrash = { openTrashBin() },
        onOpenVault = {
            authenticateVault { session ->
                refreshVault(session)
            }
        },
        onManagePhotoAccess = { permissionLauncher.launch(permissions.toTypedArray()) },
        onOpenArchives = viewModel::openArchives,
        onSourceSelected = viewModel::onSourceSelected,
        onOpenDuplicateFinder = viewModel::openDuplicateFinder,
        onRefreshDuplicates = viewModel::refreshDuplicateGroups,
        onDismissDuplicateFinder = viewModel::dismissDuplicateFinder,
        onDuplicatePhotoClick = viewModel::openDuplicatePhoto,
        onOpenOnThisDayStory = viewModel::onOpenOnThisDayStory,
        onMemoryStorySelected = viewModel::onMemoryStorySelected,
    )

    val viewerIndex = uiState.viewerStartIndex
    val viewerPhotos = uiState.viewerPhotos
    if (viewerIndex != null && viewerPhotos.isNotEmpty()) {
        PhotoViewerScreen(
            photos = viewerPhotos,
            startIndex = viewerIndex,
            onDismiss = viewModel::closeViewer,
            onPageChanged = viewModel::onViewerPhotoChanged,
            onToggleFavorite = viewModel::onToggleFavorite,
            onMoveToTrash = { photo ->
                requestMoveToTrash(listOf(photo))
            },
            onMoveToVault = { photo ->
                authenticateVault { session ->
                    movePhotosToVault(listOf(photo), session)
                }
            },
            onShareAsPdf = { photo ->
                coroutineScope.launch {
                    createAndSharePdf(
                        photos = listOf(photo),
                        persistToDownloads = false,
                        clearSelectionOnSuccess = false,
                    )
                }
            },
            reelsEnabled = uiState.reelsEnabled,
        )
    }

    val storyViewerPhotos = uiState.storyViewerPhotos
    if (storyViewerPhotos.isNotEmpty()) {
        MemoryStoryViewerScreen(
            title = uiState.storyViewerTitle,
            photos = storyViewerPhotos,
            onDismiss = viewModel::closeStoryViewer,
            onOpenPhoto = viewModel::openStoryPhoto,
        )
    }

    val reelsPhotos = uiState.reelsPhotos
    val reelsStartIndex = uiState.reelsStartIndex
    if (reelsStartIndex != null && reelsPhotos.isNotEmpty()) {
        PhotoReelsScreen(
            photos = reelsPhotos,
            startIndex = reelsStartIndex,
            onDismiss = viewModel::closeReels,
            onToggleFavorite = viewModel::onToggleFavorite,
            onSharePhoto = { photo ->
                shareSelectedPhotos(listOf(photo))
            },
        )
    }

    if (showTrashScreen) {
        com.photobook.app.ui.screen.TrashScreen(
            photos = trashedPhotos,
            isLoading = isLoadingTrash,
            onDismiss = { showTrashScreen = false },
            onRestore = { item ->
                when (val req = trashService.createRestoreRequest(listOf(item.uri))) {
                    is TrashRequestResult.Ready -> {
                        trashActionLauncher.launch(IntentSenderRequest.Builder(req.intentSender).build())
                    }
                    TrashRequestResult.UnsupportedAndroid -> Toast.makeText(
                        context, context.getString(R.string.trash_not_supported), Toast.LENGTH_SHORT
                    ).show()
                    is TrashRequestResult.Error -> Toast.makeText(
                        context, context.getString(R.string.trash_request_error), Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDeleteForever = { item ->
                when (val req = trashService.createDeleteRequest(listOf(item.uri))) {
                    is TrashRequestResult.Ready -> {
                        trashActionLauncher.launch(IntentSenderRequest.Builder(req.intentSender).build())
                    }
                    TrashRequestResult.UnsupportedAndroid -> Toast.makeText(
                        context, context.getString(R.string.trash_not_supported), Toast.LENGTH_SHORT
                    ).show()
                    is TrashRequestResult.Error -> Toast.makeText(
                        context, context.getString(R.string.trash_request_error), Toast.LENGTH_SHORT
                    ).show()
                }
            },
        )
    }

    if (uiState.showArchives) {
        ArchivesScreen(
            candidates = uiState.archiveCandidates,
            selectedPhotoIds = uiState.archiveSelectedPhotoIds,
            retentionDays = uiState.archiveRetentionDays,
            dueDeleteCount = uiState.archiveDueDeleteCount,
            archivesEnabled = uiState.archivesEnabled,
            paymentsEnabled = uiState.archivePaymentsEnabled,
            foodEnabled = uiState.archiveFoodEnabled,
            isLoading = uiState.isArchivesLoading,
            onDismiss = viewModel::dismissArchives,
            onRefresh = viewModel::refreshArchives,
            onArchivesEnabledChanged = viewModel::setArchivesEnabled,
            onPaymentsEnabledChanged = viewModel::setArchivePaymentsEnabled,
            onFoodEnabledChanged = viewModel::setArchiveFoodEnabled,
            onRetentionDaysChanged = viewModel::setArchiveRetentionDays,
            onToggleSelection = viewModel::toggleArchiveCandidateSelection,
            onSelectAll = viewModel::selectAllArchiveCandidates,
            onClearSelection = viewModel::clearArchiveCandidateSelection,
            onKeepSelected = viewModel::keepSelectedArchiveCandidates,
            onKeepCandidate = viewModel::keepArchiveCandidate,
            onArchiveCandidate = { candidate ->
                requestMoveToTrash(
                    photos = listOf(candidate.photo),
                    archiveRetentionDays = uiState.archiveRetentionDays,
                )
            },
            onMoveSelectedToTrash = {
                coroutineScope.launch {
                    val photos = viewModel.resolveArchivePhotosByIds(uiState.archiveSelectedPhotoIds)
                    requestMoveToTrash(photos, archiveRetentionDays = uiState.archiveRetentionDays)
                }
            },
            onDeleteDueItems = {
                coroutineScope.launch {
                    val dueItems = viewModel.resolveArchiveDueDeleteItems()
                    if (dueItems.isEmpty()) return@launch
                    val uris = dueItems.mapNotNull { item ->
                        runCatching { Uri.parse(item.uriString) }.getOrNull()
                    }
                    if (uris.isEmpty()) return@launch
                    when (val req = trashService.createDeleteRequest(uris)) {
                        is TrashRequestResult.Ready -> {
                            pendingArchiveDueDeleteIds = dueItems.map { item -> item.photoId }.toSet()
                            trashActionLauncher.launch(IntentSenderRequest.Builder(req.intentSender).build())
                        }
                        TrashRequestResult.UnsupportedAndroid -> Toast.makeText(
                            context, context.getString(R.string.trash_not_supported), Toast.LENGTH_SHORT
                        ).show()
                        is TrashRequestResult.Error -> Toast.makeText(
                            context, context.getString(R.string.trash_request_error), Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
        )
    }

    if (showVault) {
        VaultBottomSheet(
            items = vaultItems,
            isLoading = isVaultLoading,
            isBusy = isVaultBusy,
            onDismiss = { closeVault() },
            onRefresh = {
                authenticateVault { session ->
                    refreshVault(session)
                }
            },
            onMoveOut = { item ->
                authenticateVault { session ->
                    moveVaultItemOut(item, session)
                }
            },
            onDelete = { item ->
                authenticateVault { session ->
                    deleteVaultItem(item, session)
                }
            },
        )
    }

}

private fun vaultAuthenticators(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    } else {
        BiometricManager.Authenticators.BIOMETRIC_STRONG
    }
}

private fun sharePhotos(context: Context, photos: List<SafeShareItem>) {
    if (photos.isEmpty()) return

    val uris = photos.map { it.uri }
    val mimeType = photos
        .map { it.mimeType.ifBlank { "image/*" } }
        .distinct()
        .singleOrNull()
        ?: "image/*"

    val shareIntent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uris.first())
        }
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
    }

    val clipData = ClipData.newUri(
        context.contentResolver,
        photos.first().label,
        uris.first(),
    )
    uris.drop(1).forEach { uri ->
        clipData.addItem(ClipData.Item(uri))
    }

    shareIntent.clipData = clipData
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_selected)))
}

private fun sharePdf(context: Context, uri: Uri, fileName: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.create_pdf_share),
        ),
    )
}
