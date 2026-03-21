package com.photobook.app

import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.photobook.app.ui.screen.MainScreen
import com.photobook.app.ui.screen.OnboardingScreen
import com.photobook.app.ui.screen.PhotoViewerScreen
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

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        viewModel.refreshPermissionStatus(PermissionUtils.hasPhotoPermissions(context))
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
        photoCount = uiState.photoCount,
        searchReady = uiState.searchReady,
        suggestions = uiState.suggestions,
        showSuggestions = uiState.showSuggestions,
        onQueryChange = viewModel::onQueryChanged,
        onSearchSubmitted = viewModel::onSearchSubmitted,
        onSearchFocusChanged = viewModel::onSearchFocusChanged,
        onSuggestionSelected = viewModel::onSuggestionSelected,
        onClearQuery = viewModel::onClearQuery,
        onPhotoClick = viewModel::onPhotoClicked,
    )

    val viewerIndex = uiState.viewerStartIndex
    if (viewerIndex != null) {
        PhotoViewerScreen(
            photos = uiState.results,
            startIndex = viewerIndex,
            onDismiss = viewModel::closeViewer,
            onPageChanged = viewModel::onViewerPageChanged,
        )
    }
}
