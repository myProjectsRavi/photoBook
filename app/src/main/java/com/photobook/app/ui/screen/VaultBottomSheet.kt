package com.photobook.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.feature.vault.VaultItem
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
    onMoveOut: (VaultItem) -> Unit,
    onDelete: (VaultItem) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var previewItem by remember(items) { mutableStateOf<VaultItem?>(null) }

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
                                onPreview = { previewItem = item },
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
            onDismiss = { previewItem = null },
            onMoveOut = {
                previewItem = null
                onMoveOut(item)
            },
            onDelete = {
                previewItem = null
                onDelete(item)
            },
        )
    }
}

@Composable
private fun VaultItemCard(
    item: VaultItem,
    isBusy: Boolean,
    onPreview: () -> Unit,
    onMoveOut: () -> Unit,
    onDelete: () -> Unit,
) {
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
    onDismiss: () -> Unit,
    onMoveOut: () -> Unit,
    onDelete: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.viewer_close))
                    }
                    Text(
                        text = item.originalFileName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                VaultPreviewImage(
                    item = item,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                )
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

@Composable
private fun VaultPreviewImage(
    item: VaultItem,
    contentScale: ContentScale,
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
            contentDescription = item.originalFileName,
            contentScale = contentScale,
            modifier = modifier,
        )
    }
}
