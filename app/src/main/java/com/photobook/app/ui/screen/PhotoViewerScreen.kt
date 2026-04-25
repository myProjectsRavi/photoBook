package com.photobook.app.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
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
import com.photobook.app.feature.metadata.ExifDetails
import com.photobook.app.feature.metadata.ExifDetailsResult
import com.photobook.app.feature.metadata.ExifMetadataService
import com.photobook.app.feature.metadata.MetadataCleanResult
import com.photobook.app.feature.metadata.SafeShareResult
import com.photobook.app.feature.qrshare.QrShareEncoder
import java.util.Locale
import kotlinx.coroutines.launch
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

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        val activeId = photos[pagerState.currentPage].id
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
                    val active = photos[pagerState.currentPage]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                modifier = Modifier.size(42.dp),
                                onClick = { onToggleFavorite(active.id) },
                            ) {
                                Icon(
                                    imageVector = if (active.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.viewer_favorite),
                                    tint = if (active.isFavorite) Color(0xFFFF6B6B) else Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                modifier = Modifier.size(42.dp),
                                onClick = ::openExifSheet,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = stringResource(R.string.viewer_metadata),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                modifier = Modifier.size(42.dp),
                                onClick = ::startCopyAllTextFlow,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.viewer_copy_all_text),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                modifier = Modifier.size(42.dp),
                                onClick = {
                                    copyTextCoordinator.cancelActiveRequest()
                                    showTextRegionSelector = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CropFree,
                                    contentDescription = stringResource(R.string.viewer_select_text_area),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                modifier = Modifier.size(42.dp),
                                onClick = {
                                    val activePhoto = photos[pagerState.currentPage]
                                    qrSharePhotoId = activePhoto.id
                                    showQrShareSheet = true
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCode2,
                                    contentDescription = stringResource(R.string.viewer_generate_qr),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                modifier = Modifier.size(42.dp),
                                onClick = { onMoveToTrash(active) },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.viewer_move_to_trash),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                modifier = Modifier.size(42.dp),
                                onClick = {
                                    coroutineScope.launch {
                                        when (val safeShare = exifMetadataService.createSafeShareCopies(listOf(active))) {
                                            is SafeShareResult.Success -> {
                                                val item = safeShare.items.firstOrNull() ?: return@launch
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = item.mimeType.ifBlank { "image/*" }
                                                    putExtra(Intent.EXTRA_STREAM, item.uri)
                                                    clipData = android.content.ClipData.newUri(
                                                        context.contentResolver,
                                                        item.label,
                                                        item.uri,
                                                    )
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(
                                                    Intent.createChooser(
                                                        shareIntent,
                                                        context.getString(R.string.viewer_share),
                                                    )
                                                )
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
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.viewer_share),
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
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
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                val active = photos[pagerState.currentPage]
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
        if (showQrShareSheet) {
            QrShareSheet(
                photo = photos[pagerState.currentPage],
                encoder = qrShareEncoder,
                onDismiss = ::dismissQrShareSheet,
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
