package com.photobook.app.ui.screen

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.copytext.ExtractedTextResult
import com.photobook.app.feature.copytext.NormalizedTextRegion
import com.photobook.app.feature.copytext.OnDevicePhotoTextExtractor
import com.photobook.app.feature.copytext.PhotoTextCopyCoordinator
import com.photobook.app.feature.copytext.PreviewSeed
import com.photobook.app.feature.duplicates.BestShotRecommendation
import com.photobook.app.feature.duplicates.BurstBestShotPicker
import com.photobook.app.feature.editor.CropPreset
import com.photobook.app.feature.editor.PhotoEditResult
import com.photobook.app.feature.editor.PhotoEditService
import com.photobook.app.feature.editor.PhotoEditState
import com.photobook.app.feature.editor.QuickFilter
import com.photobook.app.feature.metadata.ExifDetails
import com.photobook.app.feature.metadata.ExifDetailsResult
import com.photobook.app.feature.metadata.ExifMetadataService
import com.photobook.app.feature.metadata.MetadataCleanResult
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photos: List<PhotoRecord>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onMoveToTrash: (PhotoRecord) -> Unit,
    onShareAsPdf: (PhotoRecord) -> Unit,
    reelsEnabled: Boolean = false,
) {
    if (photos.isEmpty()) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val safeStart = startIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeStart, pageCount = { photos.size })
    val firstPhotoId = photos.firstOrNull()?.id
    val lastPhotoId = photos.lastOrNull()?.id

    LaunchedEffect(safeStart, firstPhotoId, lastPhotoId) {
        if (safeStart in photos.indices && pagerState.currentPage != safeStart) {
            pagerState.scrollToPage(safeStart)
        }
    }

    val copyTextCoordinator = remember(context.applicationContext) {
        PhotoTextCopyCoordinator(
            extractor = OnDevicePhotoTextExtractor(context.applicationContext),
        )
    }
    val exifMetadataService = remember(context.applicationContext) {
        ExifMetadataService(context.applicationContext)
    }
    val bestShotPicker = remember { BurstBestShotPicker() }
    val photoEditService = remember(context.applicationContext) {
        PhotoEditService(context.applicationContext)
    }
    var showCopyTextSheet by remember { mutableStateOf(false) }
    var showTextRegionSelector by remember { mutableStateOf(false) }
    var copySheetState by remember { mutableStateOf<CopySheetState>(CopySheetState.Idle) }
    var copySheetPhotoId by remember { mutableStateOf<Long?>(null) }
    var copySheetMode by remember { mutableStateOf(CopyTextMode.AllText) }
    var copySheetRegion by remember { mutableStateOf<NormalizedTextRegion?>(null) }
    var autoCopyPending by remember { mutableStateOf(false) }
    var showExifSheet by remember { mutableStateOf(false) }
    var exifSheetState by remember { mutableStateOf<ExifSheetState>(ExifSheetState.Idle) }
    var exifSheetPhotoId by remember { mutableStateOf<Long?>(null) }
    var isCleaningMetadata by remember { mutableStateOf(false) }
    var currentPageZoom by remember { mutableStateOf(MIN_VIEWER_ZOOM) }
    var bestShotRecommendation by remember { mutableStateOf<BestShotRecommendation?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var editorState by remember { mutableStateOf(PhotoEditState()) }
    var isApplyingEditorAction by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    fun resetViewerZoom() {
        currentPageZoom = MIN_VIEWER_ZOOM
    }

    fun copyToClipboard(text: String) {
        clipboardManager.setText(AnnotatedString(text))
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        Toast.makeText(
            context,
            context.getString(R.string.viewer_copy_text_success),
            Toast.LENGTH_SHORT,
        ).show()
    }

    fun dismissCopySheet() {
        copyTextCoordinator.cancelActiveRequest()
        showCopyTextSheet = false
        copySheetState = CopySheetState.Idle
        copySheetPhotoId = null
        copySheetMode = CopyTextMode.AllText
        copySheetRegion = null
        autoCopyPending = false
        showTextRegionSelector = false
    }

    fun dismissExifSheet() {
        showExifSheet = false
        exifSheetState = ExifSheetState.Idle
        exifSheetPhotoId = null
        isCleaningMetadata = false
    }

    fun openExifSheet() {
        val active = photos[pagerState.currentPage]
        exifSheetPhotoId = active.id
        showExifSheet = true
        exifSheetState = ExifSheetState.Loading
        coroutineScope.launch {
            exifSheetState = when (val result = exifMetadataService.loadDetails(active)) {
                is ExifDetailsResult.Success -> ExifSheetState.Ready(result.details)
                is ExifDetailsResult.Error -> ExifSheetState.Error
            }
        }
    }

    fun cleanMetadataCopy() {
        val active = photos[pagerState.currentPage]
        if (isCleaningMetadata) return
        isCleaningMetadata = true
        coroutineScope.launch {
            when (exifMetadataService.createCleanCopy(active)) {
                is MetadataCleanResult.Success -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.viewer_metadata_clean_success),
                        Toast.LENGTH_SHORT,
                    ).show()
                }

                is MetadataCleanResult.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.viewer_metadata_clean_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            isCleaningMetadata = false
        }
    }

    fun openEditorSheet() {
        editorState = PhotoEditState()
        showEditorSheet = true
    }

    fun shareEditedCopy(result: PhotoEditResult.Success) {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.uri)
            clipData = ClipData.newUri(context.contentResolver, result.fileName, result.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.viewer_share)))
    }

    fun saveEditedCopyToDevice(result: PhotoEditResult.Success): Boolean {
        val resolver = context.contentResolver
        val outputName = "PhotoBook_Edit_${System.currentTimeMillis()}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
            put(MediaStore.Images.Media.MIME_TYPE, result.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PhotoBook")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val destUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
        val copied = runCatching {
            resolver.openInputStream(result.uri)?.use { input ->
                resolver.openOutputStream(destUri)?.use { output ->
                    input.copyTo(output)
                    true
                } ?: false
            } ?: false
        }.getOrDefault(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(destUri, doneValues, null, null)
        }
        if (!copied) {
            runCatching { resolver.delete(destUri, null, null) }
        }
        return copied
    }

    fun runEditorAction(shareAfterRender: Boolean) {
        if (isApplyingEditorAction) return
        val active = photos[pagerState.currentPage]
        isApplyingEditorAction = true
        coroutineScope.launch {
            when (val result = photoEditService.renderEditedCopy(active, editorState)) {
                is PhotoEditResult.Success -> {
                    if (shareAfterRender) {
                        shareEditedCopy(result)
                    } else {
                        val saved = saveEditedCopyToDevice(result)
                        Toast.makeText(
                            context,
                            if (saved) {
                                context.getString(R.string.viewer_editor_save_success)
                            } else {
                                context.getString(R.string.viewer_editor_save_error)
                            },
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }

                PhotoEditResult.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.viewer_editor_render_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            isApplyingEditorAction = false
        }
    }

    fun applyCopyResult(result: ExtractedTextResult, fallbackText: String?) {
        val nextState = reduceCopySheetState(result, fallbackText = fallbackText)
        copySheetState = nextState
        if (autoCopyPending && nextState is CopySheetState.Ready) {
            copyToClipboard(nextState.text)
            autoCopyPending = false
        }
    }

    fun startCopyAllTextFlow() {
        val active = photos[pagerState.currentPage]
        copySheetPhotoId = active.id
        copySheetMode = CopyTextMode.AllText
        copySheetRegion = null

        coroutineScope.launch {
            when (val seed = copyTextCoordinator.previewSeed(active.id, active.ocrText)) {
                is PreviewSeed.Cached -> {
                    copyToClipboard(seed.text)
                }

                is PreviewSeed.Fallback -> {
                    showCopyTextSheet = true
                    copySheetState = CopySheetState.Ready(
                        text = seed.text,
                        isRefreshing = true,
                    )
                    autoCopyPending = true
                    copyTextCoordinator.extractForPhoto(
                        scope = coroutineScope,
                        photoId = active.id,
                        photoUri = active.uriString,
                    ) { result ->
                        applyCopyResult(result, fallbackText = seed.text)
                    }
                }

                PreviewSeed.None -> {
                    showCopyTextSheet = true
                    copySheetState = CopySheetState.Loading
                    autoCopyPending = true
                    copyTextCoordinator.extractForPhoto(
                        scope = coroutineScope,
                        photoId = active.id,
                        photoUri = active.uriString,
                    ) { result ->
                        applyCopyResult(result, fallbackText = null)
                    }
                }
            }
        }
    }

    fun startSelectedTextFlow(region: NormalizedTextRegion) {
        val active = photos[pagerState.currentPage]
        copySheetPhotoId = active.id
        copySheetMode = CopyTextMode.SelectedArea
        copySheetRegion = region.normalized()
        autoCopyPending = true
        showCopyTextSheet = true
        copySheetState = CopySheetState.Loading
        copyTextCoordinator.extractRegionForPhoto(
            scope = coroutineScope,
            photoId = active.id,
            photoUri = active.uriString,
            region = region,
        ) { result ->
            applyCopyResult(result, fallbackText = null)
        }
    }

    fun retryCopyTextFlow() {
        val region = copySheetRegion
        if (copySheetMode == CopyTextMode.SelectedArea && region != null) {
            startSelectedTextFlow(region)
        } else {
            startCopyAllTextFlow()
        }
    }

    fun shareActivePhoto() {
        val active = photos[pagerState.currentPage]
        coroutineScope.launch {
            when (val result = exifMetadataService.createSafeShareCopies(listOf(active))) {
                is com.photobook.app.feature.metadata.SafeShareResult.Success -> {
                    val item = result.items.firstOrNull() ?: return@launch
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = item.mimeType.ifBlank { "image/*" }
                        putExtra(Intent.EXTRA_STREAM, item.uri)
                        clipData = ClipData.newUri(context.contentResolver, item.label, item.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    runCatching {
                        context.startActivity(
                            Intent.createChooser(shareIntent, context.getString(R.string.viewer_share)),
                        )
                    }
                }

                is com.photobook.app.feature.metadata.SafeShareResult.Error -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.safe_share_prepare_error),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        val activeId = photos[pagerState.currentPage].id
        bestShotRecommendation = withContext(Dispatchers.Default) {
            bestShotPicker.pick(photos, pagerState.currentPage)
        }
        if (copySheetPhotoId != null && copySheetPhotoId != activeId) {
            dismissCopySheet()
        }
        if (showTextRegionSelector) {
            showTextRegionSelector = false
        }
        if (exifSheetPhotoId != null && exifSheetPhotoId != activeId) {
            dismissExifSheet()
        }
        if (showEditorSheet) {
            showEditorSheet = false
            editorState = PhotoEditState()
        }
        resetViewerZoom()
    }

    DisposableEffect(Unit) {
        onDispose {
            copyTextCoordinator.cancelActiveRequest()
        }
    }

    Dialog(
        onDismissRequest = {
            dismissCopySheet()
            dismissExifSheet()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // ===== Main Pager (fills entire screen) =====
                val pagerScrollEnabled = currentPageZoom <= MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON
                if (reelsEnabled) {
                    // Instagram Reels-style vertical browsing: swipe up = next photo.
                    VerticalPager(
                        state = pagerState,
                        key = { page -> photos[page].id },
                        userScrollEnabled = pagerScrollEnabled,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        PhotoPage(
                            photo = photos[page],
                            isActive = page == pagerState.currentPage,
                            reelsEnabled = true,
                            onZoomChanged = { currentPageZoom = it },
                            onSingleTap = { showControls = !showControls },
                            onDismiss = {
                                dismissCopySheet()
                                onDismiss()
                            },
                        )
                    }
                } else {
                    HorizontalPager(
                        state = pagerState,
                        key = { page -> photos[page].id },
                        userScrollEnabled = pagerScrollEnabled,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        PhotoPage(
                            photo = photos[page],
                            isActive = page == pagerState.currentPage,
                            reelsEnabled = false,
                            onZoomChanged = { currentPageZoom = it },
                            onSingleTap = { showControls = !showControls },
                            onDismiss = {
                                dismissCopySheet()
                                onDismiss()
                            },
                        )
                    }
                }

                // ===== Controls overlay (tap to show/hide) =====
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Top bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x88000000))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Surface(
                                color = Color(0x22FFFFFF),
                                shape = RoundedCornerShape(28.dp),
                            ) {
                                IconButton(
                                    modifier = Modifier.size(42.dp),
                                    onClick = {
                                        dismissCopySheet()
                                        onDismiss()
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = stringResource(R.string.viewer_close),
                                        tint = Color.White,
                                    )
                                }
                            }
                            Text(
                                text = stringResource(R.string.viewer_index, pagerState.currentPage + 1, photos.size),
                                color = Color.White,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Surface(
                                    color = Color(0x44FFFFFF),
                                    shape = RoundedCornerShape(28.dp),
                                ) {
                                    IconButton(
                                        modifier = Modifier.size(42.dp),
                                        onClick = { shareActivePhoto() },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = stringResource(R.string.viewer_share),
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            }
                        }

                        // Bottom section: action buttons + info
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0x88000000)),
                        ) {
                            // Action buttons bar
                            run {
                                val active = photos.getOrNull(pagerState.currentPage) ?: return@run
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(horizontal = 18.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                                        IconButton(modifier = Modifier.size(42.dp), onClick = { onToggleFavorite(active.id) }) {
                                            Icon(
                                                imageVector = if (active.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                                contentDescription = stringResource(R.string.viewer_favorite),
                                                tint = if (active.isFavorite) Color(0xFFFF6B6B) else Color.White,
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                    }
                                    Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                                        IconButton(modifier = Modifier.size(42.dp), onClick = ::openExifSheet) {
                                            Icon(Icons.Default.Info, contentDescription = stringResource(R.string.viewer_metadata), tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                                        IconButton(modifier = Modifier.size(42.dp), onClick = ::openEditorSheet) {
                                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.viewer_edit_photo), tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                                        IconButton(modifier = Modifier.size(42.dp), onClick = { onShareAsPdf(active) }) {
                                            Icon(Icons.Default.PictureAsPdf, contentDescription = stringResource(R.string.viewer_share_as_pdf), tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                                        IconButton(modifier = Modifier.size(42.dp), onClick = ::startCopyAllTextFlow) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.viewer_copy_all_text), tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                                        IconButton(
                                            modifier = Modifier.size(42.dp),
                                            onClick = {
                                                dismissCopySheet()
                                                showTextRegionSelector = true
                                            },
                                        ) {
                                            Icon(Icons.Default.CropFree, contentDescription = stringResource(R.string.viewer_select_text_area), tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                                        IconButton(modifier = Modifier.size(42.dp), onClick = { onMoveToTrash(active) }) {
                                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.viewer_move_to_trash), tint = Color.White, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                }
                            }

                            // Photo info
                            run {
                                val active = photos.getOrNull(pagerState.currentPage) ?: return@run
                                val noLocation = stringResource(R.string.no_location)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = active.fileName,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = stringResource(
                                            R.string.viewer_meta,
                                            active.width,
                                            active.height,
                                            (active.fileSize / 1024).toInt(),
                                            active.folderName,
                                        ),
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    val location = listOfNotNull(active.city, active.state, active.country)
                                        .joinToString()
                                        .ifBlank { noLocation }
                                    Text(
                                        text = location,
                                        color = Color.LightGray,
                                        style = MaterialTheme.typography.bodySmall,
                                    )
                                    Text(
                                        text = stringResource(R.string.viewer_swipe_hint),
                                        color = Color(0xFFD6D6D6),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showCopyTextSheet) {
            CopyTextBottomSheet(
                mode = copySheetMode,
                state = copySheetState,
                onDismiss = ::dismissCopySheet,
                onRetry = ::retryCopyTextFlow,
                onCopy = { text -> copyToClipboard(text) },
            )
        }
        if (showTextRegionSelector) {
            TextRegionSelectionDialog(
                photo = photos[pagerState.currentPage],
                onDismiss = { showTextRegionSelector = false },
                onRegionSelected = { region ->
                    showTextRegionSelector = false
                    startSelectedTextFlow(region)
                },
            )
        }
        if (showExifSheet) {
            ExifMetadataBottomSheet(
                state = exifSheetState,
                isCleaning = isCleaningMetadata,
                onDismiss = ::dismissExifSheet,
                onRetry = ::openExifSheet,
                onCleanCopy = ::cleanMetadataCopy,
            )
        }

        if (showEditorSheet) {
            QuickEditorBottomSheet(
                photo = photos[pagerState.currentPage],
                state = editorState,
                isApplying = isApplyingEditorAction,
                onDismiss = {
                    showEditorSheet = false
                    editorState = PhotoEditState()
                },
                onStateChange = { next -> editorState = next },
                onRotate = {
                    editorState = editorState.copy(rotationQuarterTurns = (editorState.rotationQuarterTurns + 1) % 4)
                },
                onSaveCopy = { runEditorAction(shareAfterRender = false) },
                onShareCopy = { runEditorAction(shareAfterRender = true) },
            )
        }
    }
}

@Composable
private fun TextRegionSelectionDialog(
    photo: PhotoRecord,
    onDismiss: () -> Unit,
    onRegionSelected: (NormalizedTextRegion) -> Unit,
) {
    var selection by remember(photo.id) {
        mutableStateOf(TextSelectionBox(left = 0.14f, top = 0.24f, right = 0.86f, bottom = 0.58f))
    }
    var dragMode by remember { mutableStateOf(TextSelectionDragMode.None) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.viewer_close),
                            tint = Color.White,
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.viewer_select_text_area_title),
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.viewer_select_text_area_hint),
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Box(modifier = Modifier.size(48.dp))
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    val density = LocalDensity.current
                    val containerWidth = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
                    val containerHeight = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
                    val imageWidth = photo.width.takeIf { it > 0 }?.toFloat() ?: 1f
                    val imageHeight = photo.height.takeIf { it > 0 }?.toFloat() ?: 1f
                    val scale = min(containerWidth / imageWidth, containerHeight / imageHeight)
                    val displayWidth = (imageWidth * scale).coerceAtLeast(1f)
                    val displayHeight = (imageHeight * scale).coerceAtLeast(1f)
                    val imageOffset = Offset(
                        x = (containerWidth - displayWidth) / 2f,
                        y = (containerHeight - displayHeight) / 2f,
                    )
                    val handleRadius = with(density) { 8.dp.toPx() }
                    val hitSlop = with(density) { 34.dp.toPx() }

                    AsyncImage(
                        model = Uri.parse(photo.uriString),
                        contentDescription = photo.fileName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(selection, displayWidth, displayHeight, imageOffset) {
                                detectDragGestures(
                                    onDragStart = { touch ->
                                        dragMode = selection.hitTest(
                                            touch = touch,
                                            imageOffset = imageOffset,
                                            imageSize = Size(displayWidth, displayHeight),
                                            hitSlop = hitSlop,
                                        )
                                    },
                                    onDragEnd = { dragMode = TextSelectionDragMode.None },
                                    onDragCancel = { dragMode = TextSelectionDragMode.None },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (dragMode != TextSelectionDragMode.None) {
                                            selection = selection.dragged(
                                                mode = dragMode,
                                                deltaX = dragAmount.x / displayWidth,
                                                deltaY = dragAmount.y / displayHeight,
                                            )
                                        }
                                    },
                                )
                            },
                    ) {
                        val rectLeft = imageOffset.x + selection.left * displayWidth
                        val rectTop = imageOffset.y + selection.top * displayHeight
                        val rectRight = imageOffset.x + selection.right * displayWidth
                        val rectBottom = imageOffset.y + selection.bottom * displayHeight
                        val dim = Color.Black.copy(alpha = 0.52f)

                        drawRect(dim, topLeft = Offset.Zero, size = Size(size.width, rectTop))
                        drawRect(
                            dim,
                            topLeft = Offset(0f, rectBottom),
                            size = Size(size.width, size.height - rectBottom),
                        )
                        drawRect(
                            dim,
                            topLeft = Offset(0f, rectTop),
                            size = Size(rectLeft, rectBottom - rectTop),
                        )
                        drawRect(
                            dim,
                            topLeft = Offset(rectRight, rectTop),
                            size = Size(size.width - rectRight, rectBottom - rectTop),
                        )
                        drawRect(
                            color = Color.White,
                            topLeft = Offset(rectLeft, rectTop),
                            size = Size(rectRight - rectLeft, rectBottom - rectTop),
                            style = Stroke(width = 3.dp.toPx()),
                        )
                        listOf(
                            Offset(rectLeft, rectTop),
                            Offset(rectRight, rectTop),
                            Offset(rectLeft, rectBottom),
                            Offset(rectRight, rectBottom),
                        ).forEach { center ->
                            drawCircle(Color.White, radius = handleRadius, center = center)
                            drawCircle(Color.Black.copy(alpha = 0.3f), radius = handleRadius / 2f, center = center)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = stringResource(R.string.viewer_close), color = Color.White)
                    }
                    Button(
                        onClick = {
                            onRegionSelected(selection.toRegion())
                        },
                        modifier = Modifier.weight(2f),
                    ) {
                        Text(text = stringResource(R.string.viewer_copy_selected_area))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExifMetadataBottomSheet(
    state: ExifSheetState,
    isCleaning: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onCleanCopy: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.viewer_metadata_title),
                style = MaterialTheme.typography.titleMedium,
            )

            when (state) {
                ExifSheetState.Idle -> Unit
                ExifSheetState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.viewer_metadata_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                ExifSheetState.Error -> {
                    Text(
                        text = stringResource(R.string.viewer_metadata_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.viewer_copy_text_retry))
                    }
                }

                is ExifSheetState.Ready -> {
                    val details = state.details
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 140.dp, max = 360.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_file),
                                value = details.fileName,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_dimensions),
                                value = details.dimensions,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_size),
                                value = formatBytes(details.fileSizeBytes),
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_mime),
                                value = details.mimeType,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_folder),
                                value = details.folderName,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_camera),
                                value = details.cameraModel,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_lens),
                                value = details.lensModel,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_capture_time),
                                value = details.captureDateTime,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_orientation),
                                value = details.orientation,
                            )
                            ExifDetailRow(
                                label = stringResource(R.string.viewer_metadata_location),
                                value = if (details.latitude != null && details.longitude != null) {
                                    String.format(
                                        Locale.US,
                                        "%.6f, %.6f",
                                        details.latitude,
                                        details.longitude,
                                    )
                                } else {
                                    stringResource(R.string.viewer_metadata_location_missing)
                                },
                            )
                        }
                    }

                    Button(
                        onClick = onCleanCopy,
                        enabled = !isCleaning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (isCleaning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(text = stringResource(R.string.viewer_metadata_clean_action))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExifDetailRow(
    label: String,
    value: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value.ifBlank { "Unknown" },
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyTextBottomSheet(
    mode: CopyTextMode,
    state: CopySheetState,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onCopy: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(
                    when (mode) {
                        CopyTextMode.AllText -> R.string.viewer_copy_text_title
                        CopyTextMode.SelectedArea -> R.string.viewer_select_text_area_title
                    }
                ),
                style = MaterialTheme.typography.titleMedium,
            )

            when (state) {
                CopySheetState.Idle -> Unit

                CopySheetState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.viewer_copy_text_loading),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                CopySheetState.Empty -> {
                    Text(
                        text = stringResource(R.string.viewer_copy_text_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.viewer_copy_text_retry))
                    }
                }

                CopySheetState.Error -> {
                    Text(
                        text = stringResource(R.string.viewer_copy_text_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = onRetry) {
                        Text(text = stringResource(R.string.viewer_copy_text_retry))
                    }
                }

                is CopySheetState.Ready -> {
                    if (state.isRefreshing) {
                        Text(
                            text = stringResource(R.string.viewer_copy_text_refreshing),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        tonalElevation = 1.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp, max = 320.dp),
                    ) {
                        SelectionContainer {
                            Text(
                                text = state.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(12.dp)
                                    .verticalScroll(rememberScrollState()),
                            )
                        }
                    }

                    Button(
                        onClick = { onCopy(state.text) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(text = stringResource(R.string.viewer_copy_text_action))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickEditorBottomSheet(
    photo: PhotoRecord,
    state: PhotoEditState,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onStateChange: (PhotoEditState) -> Unit,
    onRotate: () -> Unit,
    onSaveCopy: () -> Unit,
    onShareCopy: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cropAspect = if (state.cropPreset == CropPreset.Original) {
        photo.aspectRatio.coerceIn(0.45f, 2.2f)
    } else {
        state.cropPreset.ratio
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.viewer_editor_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.viewer_editor_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(cropAspect.coerceIn(0.45f, 2.2f))
                    .heightIn(min = 180.dp, max = 260.dp)
                    .background(Color.Black, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val previewColorFilter = remember(state.exposure, state.contrast, state.filter) {
                    ColorFilter.colorMatrix(
                        buildEditorPreviewMatrix(
                            exposure = state.exposure,
                            contrast = state.contrast,
                            filter = state.filter,
                        )
                    )
                }
                AsyncImage(
                    model = Uri.parse(photo.uriString),
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Crop,
                    colorFilter = previewColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = (state.rotationQuarterTurns % 4) * 90f
                        }
                        .padding(10.dp),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CropPreset.entries.forEach { preset ->
                    AssistChip(
                        onClick = { onStateChange(state.copy(cropPreset = preset)) },
                        label = { Text(text = preset.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (preset == state.cropPreset) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                        ),
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickFilter.entries.forEach { filter ->
                    AssistChip(
                        onClick = { onStateChange(state.copy(filter = filter)) },
                        label = { Text(text = filter.label) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (filter == state.filter) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            },
                        ),
                    )
                }
            }

            Text(
                text = stringResource(R.string.viewer_editor_exposure, state.exposure),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = state.exposure,
                onValueChange = { value ->
                    onStateChange(state.copy(exposure = value.coerceIn(-1f, 1f)))
                },
                valueRange = -1f..1f,
                steps = 19,
            )

            Text(
                text = stringResource(R.string.viewer_editor_contrast, state.contrast),
                style = MaterialTheme.typography.labelMedium,
            )
            Slider(
                value = state.contrast,
                onValueChange = { value ->
                    onStateChange(state.copy(contrast = value.coerceIn(0.6f, 1.6f)))
                },
                valueRange = 0.6f..1.6f,
                steps = 20,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    modifier = Modifier.weight(1f),
                    onClick = onRotate,
                ) {
                    Text(text = stringResource(R.string.viewer_editor_rotate))
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onSaveCopy,
                    enabled = !isApplying,
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(text = stringResource(R.string.viewer_editor_save_copy))
                    }
                }
                Button(
                    modifier = Modifier.weight(1f),
                    onClick = onShareCopy,
                    enabled = !isApplying,
                ) {
                    Text(text = stringResource(R.string.viewer_editor_share_copy))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivateNoteBottomSheet(
    noteText: String,
    maxChars: Int,
    onDismiss: () -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.viewer_note_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.viewer_note_subtitle),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = noteText,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 140.dp),
                minLines = 5,
                maxLines = 10,
                placeholder = {
                    Text(text = stringResource(R.string.viewer_note_placeholder))
                },
            )
            Text(
                text = stringResource(R.string.viewer_note_char_count, noteText.length, maxChars),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = onClear,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.viewer_note_clear))
                }
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.viewer_note_save))
                }
            }
        }
    }
}

private sealed interface ExifSheetState {
    data object Idle : ExifSheetState
    data object Loading : ExifSheetState
    data object Error : ExifSheetState
    data class Ready(val details: ExifDetails) : ExifSheetState
}

private sealed interface CopySheetState {
    data object Idle : CopySheetState
    data object Loading : CopySheetState
    data object Empty : CopySheetState
    data object Error : CopySheetState
    data class Ready(
        val text: String,
        val isRefreshing: Boolean,
    ) : CopySheetState
}

private enum class CopyTextMode {
    AllText,
    SelectedArea,
}

private enum class TextSelectionDragMode {
    None,
    Move,
    TopLeft,
    TopRight,
    BottomLeft,
    BottomRight,
}

private data class TextSelectionBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun toRegion(): NormalizedTextRegion {
        return NormalizedTextRegion(left, top, right, bottom).normalized()
    }

    fun hitTest(
        touch: Offset,
        imageOffset: Offset,
        imageSize: Size,
        hitSlop: Float,
    ): TextSelectionDragMode {
        val rectLeft = imageOffset.x + left * imageSize.width
        val rectTop = imageOffset.y + top * imageSize.height
        val rectRight = imageOffset.x + right * imageSize.width
        val rectBottom = imageOffset.y + bottom * imageSize.height

        fun near(x: Float, y: Float): Boolean {
            return abs(touch.x - x) <= hitSlop && abs(touch.y - y) <= hitSlop
        }

        return when {
            near(rectLeft, rectTop) -> TextSelectionDragMode.TopLeft
            near(rectRight, rectTop) -> TextSelectionDragMode.TopRight
            near(rectLeft, rectBottom) -> TextSelectionDragMode.BottomLeft
            near(rectRight, rectBottom) -> TextSelectionDragMode.BottomRight
            touch.x in rectLeft..rectRight && touch.y in rectTop..rectBottom -> TextSelectionDragMode.Move
            else -> TextSelectionDragMode.None
        }
    }

    fun dragged(
        mode: TextSelectionDragMode,
        deltaX: Float,
        deltaY: Float,
    ): TextSelectionBox {
        return when (mode) {
            TextSelectionDragMode.None -> this
            TextSelectionDragMode.Move -> move(deltaX, deltaY)
            TextSelectionDragMode.TopLeft -> copy(
                left = (left + deltaX).coerceIn(0f, right - MIN_SIZE),
                top = (top + deltaY).coerceIn(0f, bottom - MIN_SIZE),
            )

            TextSelectionDragMode.TopRight -> copy(
                right = (right + deltaX).coerceIn(left + MIN_SIZE, 1f),
                top = (top + deltaY).coerceIn(0f, bottom - MIN_SIZE),
            )

            TextSelectionDragMode.BottomLeft -> copy(
                left = (left + deltaX).coerceIn(0f, right - MIN_SIZE),
                bottom = (bottom + deltaY).coerceIn(top + MIN_SIZE, 1f),
            )

            TextSelectionDragMode.BottomRight -> copy(
                right = (right + deltaX).coerceIn(left + MIN_SIZE, 1f),
                bottom = (bottom + deltaY).coerceIn(top + MIN_SIZE, 1f),
            )
        }
    }

    private fun move(deltaX: Float, deltaY: Float): TextSelectionBox {
        val width = right - left
        val height = bottom - top
        val nextLeft = (left + deltaX).coerceIn(0f, 1f - width)
        val nextTop = (top + deltaY).coerceIn(0f, 1f - height)
        return copy(
            left = nextLeft,
            top = nextTop,
            right = nextLeft + width,
            bottom = nextTop + height,
        )
    }

    companion object {
        private const val MIN_SIZE = 0.08f
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

private fun reduceCopySheetState(
    result: ExtractedTextResult,
    fallbackText: String?,
): CopySheetState {
    return when (result) {
        is ExtractedTextResult.Success -> {
            CopySheetState.Ready(
                text = result.text,
                isRefreshing = false,
            )
        }

        ExtractedTextResult.Empty -> {
            if (!fallbackText.isNullOrBlank()) {
                CopySheetState.Ready(
                    text = fallbackText,
                    isRefreshing = false,
                )
            } else {
                CopySheetState.Empty
            }
        }

        is ExtractedTextResult.Error -> {
            if (!fallbackText.isNullOrBlank()) {
                CopySheetState.Ready(
                    text = fallbackText,
                    isRefreshing = false,
                )
            } else {
                CopySheetState.Error
            }
        }
    }
}

private fun IntSize.centerOffset(): Offset {
    return Offset(width / 2f, height / 2f)
}

/**
 * Individual photo page composable used inside both HorizontalPager and VerticalPager.
 * Handles pinch-to-zoom (Google Photos quality), double-tap zoom on exact point, pan when zoomed,
 * and single-tap to toggle controls.
 */
@Composable
private fun PhotoPage(
    photo: PhotoRecord,
    isActive: Boolean,
    reelsEnabled: Boolean,
    onZoomChanged: (Float) -> Unit,
    onSingleTap: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    // Zoom state lives locally per page and is keyed on the photo id, so it always
    // resets cleanly when the pager recycles a page. Plain state (not Animatable) is
    // used so the pinch loop can update it directly inside the restricted gesture
    // scope, while double-tap uses a cancellable animation for a premium feel.
    var scale by remember(photo.id) { mutableStateOf(MIN_VIEWER_ZOOM) }
    var offset by remember(photo.id) { mutableStateOf(Offset.Zero) }
    val animationJob = remember(photo.id) { mutableStateOf<Job?>(null) }

    // Swipe-to-dismiss states
    var swipeDragY by remember(photo.id) { mutableStateOf(0f) }
    var isSwipingToDismiss by remember(photo.id) { mutableStateOf(false) }

    // Keep the parent informed of the live zoom so it can disable pager swiping
    // while the user is zoomed in. Only the active page reports, to avoid offscreen
    // pages racing the value back to 1x.
    LaunchedEffect(photo.id, isActive) {
        if (isActive) {
            snapshotFlow { scale }.collect { onZoomChanged(it) }
        }
    }
    // Whenever a page stops being active, snap it back to its neutral state.
    LaunchedEffect(isActive) {
        if (!isActive) {
            animationJob.value?.cancel()
            scale = MIN_VIEWER_ZOOM
            offset = Offset.Zero
            swipeDragY = 0f
            isSwipingToDismiss = false
        }
    }

    fun clampOffset(target: Offset, atScale: Float): Offset {
        if (atScale <= MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON) return Offset.Zero
        if (containerSize.width <= 0 || containerSize.height <= 0) return Offset.Zero
        val maxX = containerSize.width * (atScale - 1f) / 2f
        val maxY = containerSize.height * (atScale - 1f) / 2f
        val x = target.x.coerceIn(-maxX, maxX)
        val y = target.y.coerceIn(-maxY, maxY)
        // Guard against any NaN/Infinity ever reaching graphicsLayer.
        return Offset(if (x.isFinite()) x else 0f, if (y.isFinite()) y else 0f)
    }

    val bgAlpha = if (isSwipingToDismiss && containerSize.height > 0) {
        (1f - (kotlin.math.abs(swipeDragY) / containerSize.height.toFloat() * 1.6f)).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        val dragScale = if (isSwipingToDismiss && containerSize.height > 0) {
            (1f - (kotlin.math.abs(swipeDragY) / containerSize.height.toFloat() * 0.45f)).coerceIn(0.65f, 1f)
        } else {
            1f
        }

        AsyncImage(
            model = Uri.parse(photo.uriString),
            contentDescription = photo.fileName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .onSizeChanged { containerSize = it }
                // Taps: single tap toggles controls (always works, even when zoomed,
                // guaranteeing the user can always reach the close button). Double tap
                // smoothly zooms to/from the exact tap point.
                .pointerInput(photo.id) {
                    detectTapGestures(
                        onTap = { onSingleTap() },
                        onDoubleTap = { tapOffset ->
                            if (containerSize.width == 0 || containerSize.height == 0) {
                                return@detectTapGestures
                            }
                            val startScale = scale
                            val startOffset = offset
                            val zoomingOut = scale > MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON
                            val targetScale = if (zoomingOut) MIN_VIEWER_ZOOM else DOUBLE_TAP_VIEWER_ZOOM
                            val targetOffset = if (zoomingOut) {
                                Offset.Zero
                            } else {
                                clampOffset(
                                    calculateDoubleTapZoomOffset(tapOffset, containerSize, targetScale),
                                    targetScale,
                                )
                            }
                            animationJob.value?.cancel()
                            animationJob.value = scope.launch {
                                runCatching {
                                    animate(0f, 1f, animationSpec = tween(220)) { t, _ ->
                                        scale = startScale + (targetScale - startScale) * t
                                        offset = Offset(
                                            startOffset.x + (targetOffset.x - startOffset.x) * t,
                                            startOffset.y + (targetOffset.y - startOffset.y) * t,
                                        )
                                    }
                                }
                            }
                        },
                    )
                }
                // Vertical Swipe-to-dismiss gesture (only active when not zoomed in and reels mode is off)
                .pointerInput(photo.id, isActive, reelsEnabled) {
                    if (!isActive || reelsEnabled) return@pointerInput
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        animationJob.value?.cancel()
                        var dragActive = false
                        var dragDirectionChecked = false
                        var accumulatedDragY = 0f

                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            val zoomedIn = scale > MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON

                            if (!zoomedIn && pressedCount == 1) {
                                val change = event.changes.first()
                                val positionChange = change.position - change.previousPosition

                                if (!dragDirectionChecked) {
                                    if (kotlin.math.abs(positionChange.y) > 4f || kotlin.math.abs(positionChange.x) > 4f) {
                                        dragDirectionChecked = true
                                        // Detect if vertical drag dominates significantly
                                        if (kotlin.math.abs(positionChange.y) > kotlin.math.abs(positionChange.x) * 1.5f) {
                                            dragActive = true
                                            isSwipingToDismiss = true
                                        }
                                    }
                                }

                                if (dragActive) {
                                    accumulatedDragY += positionChange.y
                                    swipeDragY = accumulatedDragY
                                    change.consume()
                                }
                            } else {
                                dragActive = false
                                isSwipingToDismiss = false
                                swipeDragY = 0f
                            }
                        } while (event.changes.any { it.pressed })

                        if (dragActive) {
                            val dragThresholdPx = with(density) { 150.dp.toPx() }
                            if (kotlin.math.abs(swipeDragY) > dragThresholdPx) {
                                onDismiss()
                            } else {
                                val startDragY = swipeDragY
                                animationJob.value?.cancel()
                                animationJob.value = scope.launch {
                                    runCatching {
                                        animate(startDragY, 0f, animationSpec = tween(220)) { value, _ ->
                                            swipeDragY = value
                                        }
                                    }
                                    isSwipingToDismiss = false
                                }
                            }
                        } else {
                            isSwipingToDismiss = false
                            swipeDragY = 0f
                        }
                    }
                }
                // Pinch-to-zoom + pan. This pointerInput is keyed ONLY on photo.id, so it
                // is never cancelled/restarted mid-gesture (the previous bug that froze the
                // whole app). It only consumes events while actually transforming, leaving
                // single-finger 1x swipes for the pager.
                .pointerInput(photo.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        // A new touch always cancels any in-flight double-tap animation.
                        animationJob.value?.cancel()
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            val zoomedIn = scale > MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON
                            if (pressedCount > 1 || zoomedIn) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val oldScale = scale
                                    val newScale = (oldScale * zoomChange)
                                        .coerceIn(MIN_VIEWER_ZOOM, MAX_VIEWER_ZOOM)
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val center = containerSize.centerOffset()
                                    val centroidDelta = centroid - center
                                    val scaleFactor = if (oldScale <= 0f) 1f else newScale / oldScale
                                    val scaled = Offset(
                                        x = offset.x * scaleFactor + centroidDelta.x * (1f - scaleFactor),
                                        y = offset.y * scaleFactor + centroidDelta.y * (1f - scaleFactor),
                                    )
                                    scale = newScale
                                    offset = clampOffset(scaled + panChange, newScale)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Clean up any sub-pixel drift once the gesture ends.
                        if (scale <= MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON && offset != Offset.Zero) {
                            offset = Offset.Zero
                        }
                    }
                }
                .graphicsLayer {
                    scaleX = scale * dragScale
                    scaleY = scale * dragScale
                    translationX = offset.x
                    translationY = offset.y + swipeDragY
                },
        )
    }
}

private fun calculateDoubleTapZoomOffset(
    tapOffset: Offset,
    containerSize: IntSize,
    targetScale: Float,
): Offset {
    val center = containerSize.centerOffset()
    val zoomFactor = (targetScale - 1f).coerceAtLeast(0f)
    return Offset(
        x = (center.x - tapOffset.x) * zoomFactor,
        y = (center.y - tapOffset.y) * zoomFactor,
    )
}

private const val MIN_VIEWER_ZOOM = 1f
private const val MAX_VIEWER_ZOOM = 8f
private const val DOUBLE_TAP_VIEWER_ZOOM = 2.8f
private const val VIEWER_ZOOM_EPSILON = 0.01f

/**
 * Builds a Compose ColorMatrix mirroring [PhotoEditService]'s contrast → exposure → filter pipeline
 * so the live preview matches the rendered result.
 */
private fun buildEditorPreviewMatrix(
    exposure: Float,
    contrast: Float,
    filter: com.photobook.app.feature.editor.QuickFilter,
): ColorMatrix {
    val c = contrast.coerceIn(0.6f, 1.6f)
    val translate = 128f * (1f - c)
    val contrastValues = floatArrayOf(
        c, 0f, 0f, 0f, translate,
        0f, c, 0f, 0f, translate,
        0f, 0f, c, 0f, translate,
        0f, 0f, 0f, 1f, 0f,
    )

    val offset = exposure.coerceIn(-1f, 1f) * 62f
    val exposureValues = floatArrayOf(
        1f, 0f, 0f, 0f, offset,
        0f, 1f, 0f, 0f, offset,
        0f, 0f, 1f, 0f, offset,
        0f, 0f, 0f, 1f, 0f,
    )

    val filterValues = when (filter) {
        com.photobook.app.feature.editor.QuickFilter.Original -> floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        com.photobook.app.feature.editor.QuickFilter.Mono -> floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        com.photobook.app.feature.editor.QuickFilter.Vivid -> floatArrayOf(
            1.343f, -0.168f, -0.033f, 0f, 6f,
            -0.078f, 1.434f, -0.114f, 0f, 6f,
            -0.078f, -0.168f, 1.388f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f,
        )
        com.photobook.app.feature.editor.QuickFilter.Warm -> floatArrayOf(
            1.08f, 0f, 0f, 0f, 8f,
            0f, 1.0f, 0f, 0f, 2f,
            0f, 0f, 0.92f, 0f, -6f,
            0f, 0f, 0f, 1f, 0f,
        )
        com.photobook.app.feature.editor.QuickFilter.Cool -> floatArrayOf(
            0.94f, 0f, 0f, 0f, -4f,
            0f, 1.0f, 0f, 0f, 0f,
            0f, 0f, 1.08f, 0f, 8f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    return ColorMatrix(multiplyColorMatrices(multiplyColorMatrices(contrastValues, exposureValues), filterValues))
}

/** Compose a × b for 4×5 ColorMatrix arrays (rows of mat-A applied after mat-B logically). */
private fun multiplyColorMatrices(a: FloatArray, b: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0..3) {
        for (col in 0..3) {
            var sum = 0f
            for (k in 0..3) {
                sum += a[row * 5 + k] * b[k * 5 + col]
            }
            out[row * 5 + col] = sum
        }
        // Translation column (index 4) combines the row of A applied to B's translation, plus A's own translation.
        var translation = a[row * 5 + 4]
        for (k in 0..3) {
            translation += a[row * 5 + k] * b[k * 5 + 4]
        }
        out[row * 5 + 4] = translation
    }
    return out
}
