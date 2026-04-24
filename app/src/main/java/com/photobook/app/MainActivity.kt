package com.photobook.app

import android.Manifest
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.qrshare.QrReceivedImageStore
import com.photobook.app.ui.screen.MainScreen
import com.photobook.app.ui.screen.OnboardingScreen
import com.photobook.app.ui.screen.PhotoViewerScreen
import com.photobook.app.ui.screen.QrReceiveScannerScreen
import com.photobook.app.ui.theme.PhotoBookTheme
import com.photobook.app.ui.viewmodel.MainViewModel
import com.photobook.app.util.PermissionUtils
import dagger.hilt.android.AndroidEntryPoint

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
    val permissions = PermissionUtils.requiredPermissions()
    val qrReceivedImageStore = remember(context.applicationContext) {
        QrReceivedImageStore(context.applicationContext)
    }
    var showQrScanner by remember { mutableStateOf(false) }

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
        results = uiState.results,
        searchReady = uiState.searchReady,
        favoritesOnly = uiState.favoritesOnly,
        selectedPhotoIds = uiState.selectedPhotoIds,
        suggestions = uiState.suggestions,
        showSuggestions = uiState.showSuggestions,
        onQueryChange = viewModel::onQueryChanged,
        onSearchSubmitted = viewModel::onSearchSubmitted,
        onSearchFocusChanged = viewModel::onSearchFocusChanged,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onClearQuery = viewModel::onClearQuery,
        onToggleFavoritesOnly = viewModel::onToggleFavoritesOnly,
        onShareSelected = { selected ->
            sharePhotos(context, selected)
            viewModel.clearSelection()
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
    )

    val viewerIndex = uiState.viewerStartIndex
    if (viewerIndex != null) {
        PhotoViewerScreen(
            photos = uiState.results,
            startIndex = viewerIndex,
            onDismiss = viewModel::closeViewer,
            onPageChanged = viewModel::onViewerPageChanged,
            onToggleFavorite = viewModel::onToggleFavorite,
        )
    }

    if (showQrScanner) {
        QrReceiveScannerScreen(
            imageStore = qrReceivedImageStore,
            onDismiss = { showQrScanner = false },
        )
    }
}

private fun sharePhotos(context: Context, photos: List<PhotoRecord>) {
    if (photos.isEmpty()) return

    val uris = photos.map { Uri.parse(it.uriString) }
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
        photos.first().fileName,
        uris.first(),
    )
    uris.drop(1).forEach { uri ->
        clipData.addItem(ClipData.Item(uri))
    }

    shareIntent.clipData = clipData
    shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_selected)))
}
