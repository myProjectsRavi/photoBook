package com.photobook.app.ui.screen

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
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
import com.photobook.app.feature.copytext.OnDevicePhotoTextExtractor
import com.photobook.app.feature.copytext.PhotoTextCopyCoordinator
import com.photobook.app.feature.copytext.PreviewSeed
import com.photobook.app.feature.qrshare.QrShareEncoder
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PhotoViewerScreen(
    photos: List<PhotoRecord>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleFavorite: (Long) -> Unit,
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
    val qrShareEncoder = remember(context.applicationContext) {
        QrShareEncoder(context.applicationContext)
    }
    var showCopyTextSheet by remember { mutableStateOf(false) }
    var copySheetState by remember { mutableStateOf<CopySheetState>(CopySheetState.Idle) }
    var copySheetPhotoId by remember { mutableStateOf<Long?>(null) }
    var showQrShareSheet by remember { mutableStateOf(false) }
    var qrSharePhotoId by remember { mutableStateOf<Long?>(null) }

    fun dismissCopySheet() {
        copyTextCoordinator.cancelActiveRequest()
        showCopyTextSheet = false
        copySheetState = CopySheetState.Idle
        copySheetPhotoId = null
    }

    fun dismissQrShareSheet() {
        showQrShareSheet = false
        qrSharePhotoId = null
    }

    fun startCopyTextFlow() {
        val active = photos[pagerState.currentPage]
        copySheetPhotoId = active.id
        showCopyTextSheet = true

        coroutineScope.launch {
            when (val seed = copyTextCoordinator.previewSeed(active.id, active.ocrText)) {
                is PreviewSeed.Cached -> {
                    copySheetState = CopySheetState.Ready(
                        text = seed.text,
                        isRefreshing = false,
                    )
                }

                is PreviewSeed.Fallback -> {
                    copySheetState = CopySheetState.Ready(
                        text = seed.text,
                        isRefreshing = true,
                    )
                    copyTextCoordinator.extractForPhoto(
                        scope = coroutineScope,
                        photoId = active.id,
                        photoUri = active.uriString,
                    ) { result ->
                        copySheetState = reduceCopySheetState(result, fallbackText = seed.text)
                    }
                }

                PreviewSeed.None -> {
                    copySheetState = CopySheetState.Loading
                    copyTextCoordinator.extractForPhoto(
                        scope = coroutineScope,
                        photoId = active.id,
                        photoUri = active.uriString,
                    ) { result ->
                        copySheetState = reduceCopySheetState(result, fallbackText = null)
                    }
                }
            }
        }
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

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
        val activeId = photos[pagerState.currentPage].id
        if (copySheetPhotoId != null && copySheetPhotoId != activeId) {
            dismissCopySheet()
        }
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(onClick = { onToggleFavorite(active.id) }) {
                                Icon(
                                    imageVector = if (active.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.viewer_favorite),
                                    tint = if (active.isFavorite) Color(0xFFFF6B6B) else Color.White,
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(onClick = ::startCopyTextFlow) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = stringResource(R.string.viewer_copy_text),
                                    tint = Color.White,
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
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
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    val uri = Uri.parse(active.uriString)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = active.mimeType.ifBlank { "image/*" }
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newUri(
                                            context.contentResolver,
                                            active.fileName,
                                            uri,
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.viewer_share),
                                        )
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.viewer_share),
                                    tint = Color.White,
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
                state = copySheetState,
                onDismiss = ::dismissCopySheet,
                onRetry = ::startCopyTextFlow,
                onCopy = { text -> copyToClipboard(text) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CopyTextBottomSheet(
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
                text = stringResource(R.string.viewer_copy_text_title),
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
