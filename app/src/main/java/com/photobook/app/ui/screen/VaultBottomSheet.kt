package com.photobook.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.feature.phototextsearch.PhotoTextLayoutSource
import com.photobook.app.feature.phototextsearch.PhotoTextSearchController
import com.photobook.app.feature.vault.VaultItem
import com.photobook.app.ui.component.PhotoTextSearchBackHandler
import com.photobook.app.ui.component.rememberPhotoTextSearchDialogImeState
import com.photobook.app.ui.component.PhotoTextSearchHeader
import com.photobook.app.ui.component.PhotoTextSearchNavigation
import com.photobook.app.ui.component.PhotoTextSearchOverlay
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultBottomSheet(
    items: List<VaultItem>,
    isLoading: Boolean,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPreviewNeeded: (VaultItem) -> Unit,
    photoTextLayoutSource: PhotoTextLayoutSource,
    onMoveOut: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewItemId by remember { mutableStateOf<String?>(null) }
    val previewItem = items.firstOrNull { item -> item.id == previewItemId }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.vault_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    onClick = onRefresh,
                    enabled = !isLoading && !isBusy,
                ) {
                    Text(text = stringResource(R.string.vault_refresh))
                }
            }

            when {
                isLoading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                        Text(text = stringResource(R.string.vault_loading))
                    }
                }

                items.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.vault_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 128.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 520.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(
                            items = items,
                            key = { item -> item.id },
                        ) { item ->
                            VaultItemCard(
                                item = item,
                                isBusy = isBusy,
                                onPreviewNeeded = { onPreviewNeeded(item) },
                                onPreview = { previewItemId = item.id },
                                onMoveOut = { onMoveOut(item) },
                                onDelete = { onDelete(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    previewItem?.let { item ->
        VaultItemPreviewDialog(
            item = item,
            isBusy = isBusy,
            onPreviewNeeded = { onPreviewNeeded(item) },
            photoTextLayoutSource = photoTextLayoutSource,
            onDismiss = { previewItemId = null },
            onMoveOut = {
                previewItemId = null
                onMoveOut(item)
            },
            onDelete = {
                previewItemId = null
                onDelete(item)
            },
        )
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    isBusy: Boolean,
    onPreviewNeeded: () -> Unit,
    onPreview: () -> Unit,
    onMoveOut: () -> Unit,
    onDelete: () -> Unit,
) {
    LaunchedEffect(item.id, item.previewUri) {
        if (item.previewUri == null) {
            onPreviewNeeded()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VaultPreviewImage(
                item = item,
                contentScale = ContentScale.Crop,
                onPreviewError = onPreviewNeeded,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = !isBusy, onClick = onPreview),
            )
            Text(
                text = item.originalFileName,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.vault_item_meta,
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.addedAtMs)),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onMoveOut,
                    enabled = !isBusy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(text = stringResource(R.string.vault_move_out))
                }
                TextButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                ) {
                    Text(text = stringResource(R.string.vault_delete))
                }
            }
        }
    }
}

@Composable
private fun VaultItemPreviewDialog(
    item: VaultItem,
    isBusy: Boolean,
    onPreviewNeeded: () -> Unit,
    photoTextLayoutSource: PhotoTextLayoutSource,
    onDismiss: () -> Unit,
    onMoveOut: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val searchController = remember(photoTextLayoutSource, scope) {
        PhotoTextSearchController(source = photoTextLayoutSource, scope = scope)
    }
    val searchState by searchController.state.collectAsState()

    LaunchedEffect(item.id) {
        searchController.activate(item.sourcePhotoId, item.id)
    }
    DisposableEffect(searchController) {
        onDispose { searchController.dispose() }
    }

    Dialog(
        onDismissRequest = {
            if (searchState.isOpen) {
                searchController.close()
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = !searchState.isOpen,
        ),
    ) {
        val photoTextSearchImeState = rememberPhotoTextSearchDialogImeState()
        PhotoTextSearchBackHandler(
            enabled = searchState.isOpen,
            imeVisible = photoTextSearchImeState.isVisible,
            onClose = searchController::close,
        )

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = photoTextSearchImeState.bottomPadding),
            ) {
                val compactSearchChrome =
                    searchState.isOpen && maxHeight < 320.dp && maxWidth >= 600.dp
                val requestInitialFocus = maxHeight >= 480.dp

                Column(modifier = Modifier.fillMaxSize()) {
                    if (searchState.isOpen) {
                        PhotoTextSearchHeader(
                            state = searchState,
                            onQueryChange = searchController::setQuery,
                            onPrevious = searchController::previousMatch,
                            onNext = searchController::nextMatch,
                            onShowMatch = { },
                            onRetry = searchController::retry,
                            onClose = searchController::close,
                            showMatchAction = false,
                            compactNavigation = compactSearchChrome,
                            requestInitialFocus = requestInitialFocus,
                        )
                        if (!compactSearchChrome) {
                            PhotoTextSearchNavigation(
                                state = searchState,
                                onPrevious = searchController::previousMatch,
                                onNext = searchController::nextMatch,
                                onShowMatch = { },
                                onRetry = searchController::retry,
                                showMatchAction = false,
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = stringResource(R.string.viewer_close),
                                )
                            }
                            Text(
                                text = item.originalFileName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { searchController.open() },
                                modifier = Modifier.size(48.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.viewer_search_text),
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clipToBounds()
                            .background(Color.Black),
                    ) {
                        VaultPreviewImage(
                            item = item,
                            contentScale = ContentScale.Fit,
                            contentDescription = if (searchState.isOpen) null else item.originalFileName,
                            onPreviewError = onPreviewNeeded,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (
                            searchState.isOpen &&
                            searchState.layout != null &&
                            searchState.matches.isNotEmpty()
                        ) {
                            PhotoTextSearchOverlay(
                                layout = searchState.layout!!,
                                matches = searchState.matches,
                                activeMatchIndex = searchState.activeMatchIndex,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }

                    if (!searchState.isOpen) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = onDismiss,
                                enabled = !isBusy,
                            ) {
                                Text(text = stringResource(R.string.vault_keep_in_vault))
                            }
                            Button(
                                onClick = onMoveOut,
                                enabled = !isBusy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(text = stringResource(R.string.vault_move_out))
                            }
                            TextButton(
                                onClick = onDelete,
                                enabled = !isBusy,
                            ) {
                                Text(text = stringResource(R.string.vault_delete))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultPreviewImage(
    item: VaultItem,
    contentScale: ContentScale,
    contentDescription: String? = item.originalFileName,
    onPreviewError: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (item.previewUri == null) {
        Box(
            modifier = modifier.background(Color.Black.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.vault_preview_unavailable),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    } else {
        AsyncImage(
            model = item.previewUri,
            contentDescription = contentDescription,
            contentScale = contentScale,
            onError = { onPreviewError() },
            modifier = modifier,
        )
    }
}
