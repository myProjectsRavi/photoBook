package com.photobook.app.ui.screen

import android.content.ClipData
import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.photobook.app.feature.notes.PhotoNoteStore
import com.photobook.app.feature.qrshare.QrShareEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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
) {
    if (photos.isEmpty()) return

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val safeStart = startIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeStart, pageCount = { photos.size })

    val copyTextCoordinator = remember(context.applicationContext) {
        PhotoTextCopyCoordinator(
            extractor = OnDevicePhotoTextExtractor(context.applicationContext),
        )
    }
    val exifMetadataService = remember(context.applicationContext) {
        ExifMetadataService(context.applicationContext)
    }
    val qrShareEncoder = remember(context.applicationContext) {
        QrShareEncoder(context.applicationContext)
    }
    val bestShotPicker = remember { BurstBestShotPicker() }
    val photoEditService = remember(context.applicationContext) {
        PhotoEditService(context.applicationContext)
    }
    val photoNoteStore = remember(context.applicationContext) {
        PhotoNoteStore(context.applicationContext)
    }
    var showCopyTextSheet by remember { mutableStateOf(false) }
    var copySheetState by remember { mutableStateOf<CopySheetState>(CopySheetState.Idle) }
    var copySheetPhotoId by remember { mutableStateOf<Long?>(null) }
    var copySheetMode by remember { mutableStateOf(CopyTextMode.AllText) }
    var copySheetRegion by remember { mutableStateOf<NormalizedTextRegion?>(null) }
    var autoCopyPending by remember { mutableStateOf(false) }
    var showTextRegionSelector by remember { mutableStateOf(false) }
    var showExifSheet by remember { mutableStateOf(false) }
    var exifSheetState by remember { mutableStateOf<ExifSheetState>(ExifSheetState.Idle) }
    var exifSheetPhotoId by remember { mutableStateOf<Long?>(null) }
    var isCleaningMetadata by remember { mutableStateOf(false) }
    var showQrShareSheet by remember { mutableStateOf(false) }
    var qrSharePhotoId by remember { mutableStateOf<Long?>(null) }
    var viewerZoomScale by remember { mutableStateOf(MIN_VIEWER_ZOOM) }
    var viewerZoomOffset by remember { mutableStateOf(Offset.Zero) }
    var viewerImageSize by remember { mutableStateOf(IntSize.Zero) }
    var bestShotRecommendation by remember { mutableStateOf<BestShotRecommendation?>(null) }
    var showEditorSheet by remember { mutableStateOf(false) }
    var editorState by remember { mutableStateOf(PhotoEditState()) }
    var isApplyingEditorAction by remember { mutableStateOf(false) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var notePhotoId by remember { mutableStateOf<Long?>(null) }
    var noteDraft by remember { mutableStateOf("") }
    var hasNoteForCurrent by remember { mutableStateOf(false) }

    fun resetViewerZoom() {
        viewerZoomScale = MIN_VIEWER_ZOOM
        viewerZoomOffset = Offset.Zero
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
    }

    fun dismissQrShareSheet() {
        showQrShareSheet = false
        qrSharePhotoId = null
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

    fun openNotesSheet() {
        val active = photos[pagerState.currentPage]
        notePhotoId = active.id
        noteDraft = photoNoteStore.getNote(active.id)
        showNotesSheet = true
    }

    fun saveNoteForActivePhoto() {
        val active = photos[pagerState.currentPage]
        photoNoteStore.saveNote(active.id, noteDraft)
        hasNoteForCurrent = noteDraft.trim().isNotEmpty()
        Toast.makeText(
            context,
            context.getString(R.string.viewer_note_saved),
            Toast.LENGTH_SHORT,
        ).show()
        showNotesSheet = false
    }

    fun clearNoteForActivePhoto() {
        val active = photos[pagerState.currentPage]
        photoNoteStore.deleteNote(active.id)
        noteDraft = ""
        hasNoteForCurrent = false
        Toast.makeText(
            context,
            context.getString(R.string.viewer_note_cleared),
            Toast.LENGTH_SHORT,
        ).show()
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
                    copyToClipboard(seed.text)
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
        val uri = runCatching { Uri.parse(active.uriString) }.getOrNull() ?: return
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = active.mimeType.ifBlank { "image/*" }
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, active.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(
                Intent.createChooser(shareIntent, context.getString(R.string.viewer_share)),
            )
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        val activeId = photos[pagerState.currentPage].id
        bestShotRecommendation = withContext(Dispatchers.Default) {
            bestShotPicker.pick(photos, pagerState.currentPage)
        }
        hasNoteForCurrent = photoNoteStore.getNote(activeId).isNotBlank()
        if (copySheetPhotoId != null && copySheetPhotoId != activeId) {
            dismissCopySheet()
        }
        if (exifSheetPhotoId != null && exifSheetPhotoId != activeId) {
            dismissExifSheet()
        }
        showTextRegionSelector = false
        if (qrSharePhotoId != null && qrSharePhotoId != activeId) {
            dismissQrShareSheet()
        }
        if (showNotesSheet && notePhotoId != activeId) {
            showNotesSheet = false
            notePhotoId = null
            noteDraft = ""
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
            dismissQrShareSheet()
            onDismiss()
        },
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
                    // Prominent share button - top right
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

                // Action buttons bar (scrollable)
                run {
                    val active = photos.getOrNull(pagerState.currentPage) ?: return@run
                    val bestIndex = bestShotRecommendation?.bestIndex
                    val isCurrentBestShot = bestIndex == pagerState.currentPage
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
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
                            IconButton(modifier = Modifier.size(42.dp), onClick = ::startCopyAllTextFlow) {
                                Icon(Icons.Default.ContentCopy, contentDescription = stringResource(R.string.viewer_copy_all_text), tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                            IconButton(modifier = Modifier.size(42.dp), onClick = {
                                qrSharePhotoId = active.id
                                showQrShareSheet = true
                            }) {
                                Icon(Icons.Default.QrCode2, contentDescription = stringResource(R.string.viewer_generate_qr), tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                        Surface(color = Color(0x22FFFFFF), shape = RoundedCornerShape(28.dp)) {
                            IconButton(modifier = Modifier.size(42.dp), onClick = { onMoveToTrash(active) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.viewer_move_to_trash), tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }

                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = viewerZoomScale <= MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    val photo = photos[page]

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = Uri.parse(photo.uriString),
                            contentDescription = photo.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .onSizeChanged { size ->
                                    viewerImageSize = size
                                }
                                .pointerInput(photo.id, viewerImageSize) {
                                    detectTapGestures(
                                        onDoubleTap = { tapOffset ->
                                            if (viewerImageSize.width == 0 || viewerImageSize.height == 0) {
                                                return@detectTapGestures
                                            }
                                            if (viewerZoomScale > MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON) {
                                                resetViewerZoom()
                                            } else {
                                                val targetScale = DOUBLE_TAP_VIEWER_ZOOM
                                                val targetOffset = calculateDoubleTapZoomOffset(
                                                    tapOffset = tapOffset,
                                                    containerSize = viewerImageSize,
                                                    targetScale = targetScale,
                                                )
                                                viewerZoomScale = targetScale
                                                viewerZoomOffset = clampViewerOffset(
                                                    offset = targetOffset,
                                                    scale = targetScale,
                                                    containerSize = viewerImageSize,
                                                )
                                            }
                                        },
                                    )
                                }
                                .then(
                                    // Only attach pinch-to-zoom/pan when zoomed in to avoid
                                    // consuming swipes that the VerticalPager needs.
                                    if (viewerZoomScale > MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON) {
                                        Modifier.pointerInput(photo.id, viewerImageSize) {
                                            detectTransformGestures { centroid, pan, zoom, _ ->
                                                if (viewerImageSize.width == 0 || viewerImageSize.height == 0) {
                                                    return@detectTransformGestures
                                                }

                                                val oldScale = viewerZoomScale
                                                val newScale = (oldScale * zoom).coerceIn(MIN_VIEWER_ZOOM, MAX_VIEWER_ZOOM)

                                                if (newScale <= MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON) {
                                                    resetViewerZoom()
                                                    return@detectTransformGestures
                                                }

                                                val center = viewerImageSize.centerOffset()
                                                val centroidDelta = centroid - center
                                                val scaleFactor = newScale / oldScale
                                                val scaledOffset = Offset(
                                                    x = (viewerZoomOffset.x + centroidDelta.x) * scaleFactor - centroidDelta.x,
                                                    y = (viewerZoomOffset.y + centroidDelta.y) * scaleFactor - centroidDelta.y,
                                                )
                                                val nextOffset = scaledOffset + pan

                                                viewerZoomScale = newScale
                                                viewerZoomOffset = clampViewerOffset(
                                                    offset = nextOffset,
                                                    scale = newScale,
                                                    containerSize = viewerImageSize,
                                                )
                                            }
                                        }
                                    } else {
                                        // At 1x: pinch gesture + horizontal swipe to switch photo.
                                        // Vertical swipes are handled by VerticalPager (reels-style).
                                        Modifier
                                            .pointerInput(photo.id, viewerImageSize) {
                                                detectTransformGestures { centroid, _, zoom, _ ->
                                                    if (viewerImageSize.width == 0 || viewerImageSize.height == 0) {
                                                        return@detectTransformGestures
                                                    }
                                                    val newScale = (viewerZoomScale * zoom).coerceIn(MIN_VIEWER_ZOOM, MAX_VIEWER_ZOOM)
                                                    if (newScale > MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON) {
                                                        val center = viewerImageSize.centerOffset()
                                                        val centroidDelta = centroid - center
                                                        val scaleFactor = newScale / viewerZoomScale
                                                        val targetOffset = Offset(
                                                            x = centroidDelta.x * (1f - scaleFactor),
                                                            y = centroidDelta.y * (1f - scaleFactor),
                                                        )
                                                        viewerZoomScale = newScale
                                                        viewerZoomOffset = clampViewerOffset(
                                                            offset = targetOffset,
                                                            scale = newScale,
                                                            containerSize = viewerImageSize,
                                                        )
                                                    }
                                                }
                                            }
                                            .pointerInput(photo.id, photos.size) {
                                                var totalDx = 0f
                                                detectHorizontalDragGestures(
                                                    onDragStart = { totalDx = 0f },
                                                    onDragEnd = {
                                                        val threshold = size.width * 0.18f
                                                        if (totalDx <= -threshold) {
                                                            // Swipe left = next photo
                                                            val next = (pagerState.currentPage + 1)
                                                                .coerceAtMost(photos.size - 1)
                                                            if (next != pagerState.currentPage) {
                                                                coroutineScope.launch {
                                                                    pagerState.animateScrollToPage(next)
                                                                }
                                                            }
                                                        } else if (totalDx >= threshold) {
                                                            // Swipe right = previous photo
                                                            val prev = (pagerState.currentPage - 1).coerceAtLeast(0)
                                                            if (prev != pagerState.currentPage) {
                                                                coroutineScope.launch {
                                                                    pagerState.animateScrollToPage(prev)
                                                                }
                                                            }
                                                        }
                                                        totalDx = 0f
                                                    },
                                                    onDragCancel = { totalDx = 0f },
                                                    onHorizontalDrag = { change, dragAmount ->
                                                        totalDx += dragAmount
                                                        if (kotlin.math.abs(totalDx) > 12f) {
                                                            change.consume()
                                                        }
                                                    },
                                                )
                                            }
                                    }
                                )
                                .graphicsLayer {
                                    scaleX = viewerZoomScale
                                    scaleY = viewerZoomScale
                                    translationX = viewerZoomOffset.x
                                    translationY = viewerZoomOffset.y
                                },
                        )
                    }
                }

                val active = photos.getOrNull(pagerState.currentPage) ?: run {
                    // Photo list has been emptied or index is stale — dismiss viewer.
                    onDismiss()
                    return@Column
                }
                val noLocation = stringResource(R.string.no_location)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC111111))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (photos.size > 1) {
                        Text(
                            text = stringResource(R.string.viewer_swipe_hint),
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
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

                    val bestIndex = bestShotRecommendation?.bestIndex
                    if (bestIndex != null) {
                        val isBest = bestIndex == pagerState.currentPage
                        AssistChip(
                            onClick = {
                                if (!isBest) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(bestIndex) }
                                }
                            },
                            label = {
                                Text(
                                    text = if (isBest) {
                                        stringResource(R.string.viewer_best_shot_selected)
                                    } else {
                                        stringResource(R.string.viewer_best_shot_jump)
                                    },
                                )
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (isBest) {
                                    Color(0x33FFD54F)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                                },
                                labelColor = Color.White,
                            ),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        active.mlTags.forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(text = tag.label) },
                                shape = RoundedCornerShape(16.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    labelColor = Color.White,
                                ),
                            )
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
        if (showExifSheet) {
            ExifMetadataBottomSheet(
                state = exifSheetState,
                isCleaning = isCleaningMetadata,
                onDismiss = ::dismissExifSheet,
                onRetry = ::openExifSheet,
                onCleanCopy = ::cleanMetadataCopy,
            )
        }

        if (showQrShareSheet) {
            QrShareSheet(
                photo = photos[pagerState.currentPage],
                encoder = qrShareEncoder,
                onDismiss = ::dismissQrShareSheet,
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
        if (showNotesSheet) {
            PrivateNoteBottomSheet(
                noteText = noteDraft,
                maxChars = PhotoNoteStore.MAX_NOTE_CHARS,
                onDismiss = {
                    showNotesSheet = false
                    notePhotoId = null
                },
                onTextChange = { value ->
                    noteDraft = value.take(PhotoNoteStore.MAX_NOTE_CHARS)
                },
                onSave = ::saveNoteForActivePhoto,
                onClear = ::clearNoteForActivePhoto,
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

private fun clampViewerOffset(
    offset: Offset,
    scale: Float,
    containerSize: IntSize,
): Offset {
    if (scale <= MIN_VIEWER_ZOOM + VIEWER_ZOOM_EPSILON) {
        return Offset.Zero
    }
    if (containerSize.width <= 0 || containerSize.height <= 0) {
        return Offset.Zero
    }

    val maxTranslationX = (containerSize.width * (scale - 1f)) / 2f
    val maxTranslationY = (containerSize.height * (scale - 1f)) / 2f
    return Offset(
        x = offset.x.coerceIn(-maxTranslationX, maxTranslationX),
        y = offset.y.coerceIn(-maxTranslationY, maxTranslationY),
    )
}

private const val MIN_VIEWER_ZOOM = 1f
private const val MAX_VIEWER_ZOOM = 6f
private const val DOUBLE_TAP_VIEWER_ZOOM = 3f
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
