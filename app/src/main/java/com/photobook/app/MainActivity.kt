package com.photobook.app

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.Context
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
import com.photobook.app.ui.screen.MainScreen
import com.photobook.app.ui.screen.OnboardingScreen
import com.photobook.app.ui.screen.PhotoViewerScreen
import com.photobook.app.ui.screen.QrReceiveScannerScreen
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
        setContent {
            PhotoBookTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PhotoBookApp(viewModel = vm)
                }
            }
        }
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
    var showQrScanner by remember { mutableStateOf(false) }
    var pendingTrashPhotoIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

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
        selectedPhotoIds = uiState.selectedPhotoIds,
        suggestions = uiState.suggestions,
        showSuggestions = uiState.showSuggestions,
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
        onShareSelected = { selectedIds ->
            coroutineScope.launch {
                val selectedPhotos = viewModel.resolvePhotosByIds(selectedIds)
                if (selectedPhotos.isEmpty()) return@launch
                when (val safeShare = exifMetadataService.createSafeShareCopies(selectedPhotos)) {
                    is SafeShareResult.Success -> {
                        sharePhotos(context, safeShare.items)
                        viewModel.clearSelection()
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
        onSourceSelected = viewModel::onSourceSelected,
        onOpenDuplicateFinder = viewModel::openDuplicateFinder,
        onRefreshDuplicates = viewModel::refreshDuplicateGroups,
        onDismissDuplicateFinder = viewModel::dismissDuplicateFinder,
        onDuplicatePhotoClick = viewModel::openDuplicatePhoto,
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
        )
    }

    if (showQrScanner) {
        QrReceiveScannerScreen(
            imageStore = qrReceivedImageStore,
            onDismiss = { showQrScanner = false },
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
