package com.photobook.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.metadata.ExifMetadataService
import com.photobook.app.feature.metadata.SafeShareItem
import com.photobook.app.feature.metadata.SafeShareResult
import com.photobook.app.feature.pdf.PdfExportResult
import com.photobook.app.feature.pdf.PdfExportService
import com.photobook.app.feature.qrshare.QrReceivedImageStore
import com.photobook.app.feature.trash.TrashRequestResult
import com.photobook.app.feature.trash.TrashService
import com.photobook.app.feature.vault.VaultExportResult
import com.photobook.app.feature.vault.VaultItem
import com.photobook.app.feature.vault.VaultSaveResult
import com.photobook.app.feature.vault.VaultService
import com.photobook.app.ui.screen.MainScreen
import com.photobook.app.ui.screen.OnboardingScreen
import com.photobook.app.ui.screen.PhotoViewerScreen
import com.photobook.app.ui.screen.MemoryStoryViewerScreen
import com.photobook.app.ui.screen.PhotoReelsScreen
import com.photobook.app.ui.screen.DeclutterSwipeScreen
import com.photobook.app.ui.screen.QrReceiveScannerScreen
import com.photobook.app.ui.screen.VaultBottomSheet
import com.photobook.app.ui.theme.PhotoBookTheme
import com.photobook.app.ui.viewmodel.MainViewModel
import com.photobook.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

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
    val qrReceivedImageStore = remember(context.applicationContext) {
        QrReceivedImageStore(context.applicationContext)
    }
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
    var showQrScanner by remember { mutableStateOf(false) }
    var pendingTrashPhotoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var showVaultSheet by remember { mutableStateOf(false) }
    var isVaultLoading by remember { mutableStateOf(false) }
    var isVaultBusy by remember { mutableStateOf(false) }
    var vaultItems by remember { mutableStateOf<List<VaultItem>>(emptyList()) }

    // Trash bin state
    var showTrashScreen by remember { mutableStateOf(false) }
    var trashedPhotos by remember { mutableStateOf<List<com.photobook.app.feature.trash.TrashedPhoto>>(emptyList()) }
    var isLoadingTrash by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissionStatus(PermissionUtils.hasPhotoPermissions(context))
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.scan_qr_camera_denied),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    val trashRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val trashedIds = pendingTrashPhotoIds
        pendingTrashPhotoIds = emptySet()
        if (trashedIds.isEmpty()) return@rememberLauncherForActivityResult

        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onPhotosMovedToTrash(trashedIds)
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
    ) { _ ->
        // After any system action, re-query trash list.
        coroutineScope.launch {
            isLoadingTrash = true
            trashedPhotos = trashService.listTrashed()
            isLoadingTrash = false
        }
    }

    fun openTrashBin() {
        showTrashScreen = true
        coroutineScope.launch {
            isLoadingTrash = true
            trashedPhotos = trashService.listTrashed()
            isLoadingTrash = false
        }
    }

    fun requestMoveToTrash(photos: List<PhotoRecord>) {
        if (photos.isEmpty()) return
        when (val request = trashService.createTrashRequest(photos)) {
            is TrashRequestResult.Ready -> {
                pendingTrashPhotoIds = photos.map { photo -> photo.id }.toSet()
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

    fun refreshVaultItems() {
        coroutineScope.launch {
            isVaultLoading = true
            vaultItems = vaultService.listItems()
            isVaultLoading = false
        }
    }

    fun runWithVaultBiometrics(onAuthorized: () -> Unit) {
        requestBiometricUnlock(
            context = context,
            title = context.getString(R.string.vault_biometric_title),
            subtitle = context.getString(R.string.vault_biometric_subtitle),
            onSuccess = onAuthorized,
            onFailure = {
                Toast.makeText(
                    context,
                    context.getString(R.string.vault_biometric_failed),
                    Toast.LENGTH_LONG,
                ).show()
                // Help the user set up a screen lock so the vault becomes usable next time.
                runCatching {
                    val settingsIntent = Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(settingsIntent)
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissionStatus(PermissionUtils.hasPhotoPermissions(context))
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
        feedMode = uiState.feedMode,
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
                if (selectedPhotos.isEmpty()) return@launch
                when (val result = pdfExportService.exportPhotos(selectedPhotos)) {
                    is PdfExportResult.Success -> {
                        Toast.makeText(
                            context,
                            context.getString(R.string.create_pdf_success, result.pageCount),
                            Toast.LENGTH_SHORT,
                        ).show()
                        sharePdf(context, result.uri, result.fileName)
                        viewModel.clearSelection()
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
        },
        onAddSelectedToVault = { selectedIds ->
            coroutineScope.launch {
                val selectedPhotos = viewModel.resolvePhotosByIds(selectedIds)
                if (selectedPhotos.isEmpty()) return@launch
                runWithVaultBiometrics {
                    coroutineScope.launch {
                        isVaultBusy = true
                        when (val result = vaultService.addPhotos(selectedPhotos)) {
                            is VaultSaveResult.Success -> {
                                Toast.makeText(
                                    context,
                                    context.getString(
                                        R.string.vault_add_success,
                                        result.addedCount,
                                        result.skippedCount,
                                    ),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                viewModel.clearSelection()
                                if (showVaultSheet) {
                                    refreshVaultItems()
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
            }
        },
        onCopyTextFromPhoto = { photoId ->
            // Open the viewer on this photo so the user can access Copy Text from there
            viewModel.openPhotoById(photoId)
        },
        onGenerateQrForPhoto = { photoId ->
            // Open the viewer on this photo so the user can access QR generation from there
            viewModel.openPhotoById(photoId)
        },
        onClearSelection = viewModel::clearSelection,
        onPhotoClick = viewModel::onPhotoClicked,
        onPhotoLongClick = viewModel::onPhotoLongPressed,
        onOpenQrScanner = {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                showQrScanner = true
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        },
        onOpenVault = {
            runWithVaultBiometrics {
                showVaultSheet = true
                refreshVaultItems()
            }
        },
        onOpenReels = { openTrashBin() },
        onSelectFeedMode = viewModel::onSelectFeedMode,
        onSourceSelected = viewModel::onSourceSelected,
        onOpenDeclutter = viewModel::openDeclutterSwipe,
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
            onPageChanged = viewModel::onViewerPageChanged,
            onToggleFavorite = viewModel::onToggleFavorite,
            onMoveToTrash = { photo ->
                requestMoveToTrash(listOf(photo))
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

    val declutterSession = uiState.declutterSession
    if (declutterSession != null || uiState.isDeclutterLoading) {
        DeclutterSwipeScreen(
            session = declutterSession ?: com.photobook.app.feature.declutter.DeclutterSession(emptyList()),
            currentPhoto = uiState.declutterCurrentPhoto,
            isLoading = uiState.isDeclutterLoading,
            onDismiss = viewModel::dismissDeclutterSwipe,
            onKeepCurrent = viewModel::onDeclutterKeepCurrent,
            onTrashCurrent = viewModel::onDeclutterTrashCurrent,
            onUndoLast = viewModel::onDeclutterUndo,
            onApplyTrash = { photoIds ->
                coroutineScope.launch {
                    val photos = viewModel.resolvePhotosByIds(photoIds)
                    if (photos.isNotEmpty()) {
                        requestMoveToTrash(photos)
                    }
                    viewModel.dismissDeclutterSwipe()
                }
            },
        )
    }

    if (showQrScanner) {
        QrReceiveScannerScreen(
            imageStore = qrReceivedImageStore,
            onDismiss = { showQrScanner = false },
        )
    }

    if (showVaultSheet) {
        VaultBottomSheet(
            items = vaultItems,
            isLoading = isVaultLoading,
            isBusy = isVaultBusy,
            onDismiss = { showVaultSheet = false },
            onRefresh = ::refreshVaultItems,
            onSaveToDevice = { item ->
                coroutineScope.launch {
                    isVaultBusy = true
                    when (vaultService.exportToDevice(item.id)) {
                        is VaultExportResult.Success -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.vault_export_success),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }

                        is VaultExportResult.Error -> {
                            Toast.makeText(
                                context,
                                context.getString(R.string.vault_export_error),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    isVaultBusy = false
                }
            },
            onDelete = { item ->
                coroutineScope.launch {
                    isVaultBusy = true
                    val deleted = vaultService.deleteItem(item.id)
                    if (deleted) {
                        vaultItems = vaultItems.filterNot { existing -> existing.id == item.id }
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.vault_delete_error),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                    isVaultBusy = false
                }
            },
        )
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

private fun requestBiometricUnlock(
    context: Context,
    title: String,
    subtitle: String,
    onSuccess: () -> Unit,
    onFailure: () -> Unit,
) {
    val activity = context.findFragmentActivity()
    if (activity == null) {
        onFailure()
        return
    }

    val biometricManager = BiometricManager.from(activity)

    // Try, in order of strength: STRONG+PIN, WEAK+PIN, STRONG-only, WEAK-only, PIN-only.
    // On API 28/29 the combined BIOMETRIC_*|DEVICE_CREDENTIAL flag is not supported, so we
    // gracefully fall back. We only treat the prompt as unavailable when nothing works.
    val candidates = buildList {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            add(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            add(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            add(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
        }
        add(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        add(BiometricManager.Authenticators.BIOMETRIC_WEAK)
    }

    val supported = candidates.firstOrNull { auth ->
        runCatching {
            biometricManager.canAuthenticate(auth) == BiometricManager.BIOMETRIC_SUCCESS
        }.getOrDefault(false)
    }

    if (supported == null) {
        onFailure()
        return
    }

    val promptInfo = runCatching {
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(supported)
        // setNegativeButtonText is required when DEVICE_CREDENTIAL is not part of the allowed set.
        val hasDeviceCredential =
            (supported and BiometricManager.Authenticators.DEVICE_CREDENTIAL) != 0
        if (!hasDeviceCredential) {
            builder.setNegativeButtonText(context.getString(android.R.string.cancel))
        }
        builder.build()
    }.getOrElse {
        onFailure()
        return
    }

    val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onFailure()
                }
            }
        },
    )
    runCatching { prompt.authenticate(promptInfo) }.onFailure { onFailure() }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? {
    return when (this) {
        is FragmentActivity -> this
        is ContextWrapper -> baseContext.findFragmentActivity()
        else -> null
    }
}
