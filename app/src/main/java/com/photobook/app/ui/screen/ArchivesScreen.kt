package com.photobook.app.ui.screen

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.feature.archive.ArchiveCandidate

@Composable
fun ArchivesScreen(
    candidates: List<ArchiveCandidate>,
    selectedPhotoIds: Set<Long>,
    retentionDays: Int,
    dueDeleteCount: Int,
    archivesEnabled: Boolean,
    paymentsEnabled: Boolean,
    foodEnabled: Boolean,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onArchivesEnabledChanged: (Boolean) -> Unit,
    onPaymentsEnabledChanged: (Boolean) -> Unit,
    onFoodEnabledChanged: (Boolean) -> Unit,
    onRetentionDaysChanged: (Int) -> Unit,
    onToggleSelection: (Long) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onKeepSelected: () -> Unit,
    onKeepCandidate: (Long) -> Unit,
    onArchiveCandidate: (ArchiveCandidate) -> Unit,
    onMoveSelectedToTrash: () -> Unit,
    onDeleteDueItems: () -> Unit,
) {
    var previewCandidate by remember(candidates) { mutableStateOf<ArchiveCandidate?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                ArchivesTopBar(
                    count = candidates.size,
                    selectedCount = selectedPhotoIds.size,
                    estimatedBytes = candidates.sumOf { candidate -> candidate.photo.fileSize.coerceAtLeast(0L) },
                    onDismiss = onDismiss,
                    onRefresh = onRefresh,
                    isLoading = isLoading,
                    archivesEnabled = archivesEnabled,
                )

                ArchivesEnableRow(
                    enabled = archivesEnabled,
                    onEnabledChanged = onArchivesEnabledChanged,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )

                if (archivesEnabled) {
                    ArchiveCategorySelector(
                        paymentsEnabled = paymentsEnabled,
                        foodEnabled = foodEnabled,
                        onPaymentsEnabledChanged = onPaymentsEnabledChanged,
                        onFoodEnabledChanged = onFoodEnabledChanged,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )

                    RetentionSelector(
                        selectedDays = retentionDays,
                        onRetentionDaysChanged = onRetentionDaysChanged,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }

                if (archivesEnabled && dueDeleteCount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.archives_due_delete_count, dueDeleteCount),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(onClick = onDeleteDueItems) {
                            Text(text = stringResource(R.string.archives_delete_due))
                        }
                    }
                }

                if (archivesEnabled) {
                    ArchivesActionRow(
                        selectedCount = selectedPhotoIds.size,
                        totalCount = candidates.size,
                        onSelectAll = onSelectAll,
                        onClearSelection = onClearSelection,
                        onKeepSelected = onKeepSelected,
                        onMoveSelectedToTrash = onMoveSelectedToTrash,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                when {
                    !archivesEnabled -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.archives_disabled),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }

                    isLoading && candidates.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }

                    candidates.isEmpty() -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.archives_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(32.dp),
                            )
                        }
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 112.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(candidates, key = { candidate -> candidate.photo.id }) { candidate ->
                                ArchiveCandidateTile(
                                    candidate = candidate,
                                    selected = candidate.photo.id in selectedPhotoIds,
                                    onPreview = { previewCandidate = candidate },
                                    onToggleSelection = { onToggleSelection(candidate.photo.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    previewCandidate
        ?.takeIf { candidate -> candidates.any { current -> current.photo.id == candidate.photo.id } }
        ?.let { candidate ->
            ArchiveCandidatePreviewDialog(
                candidate = candidate,
                retentionDays = retentionDays,
                onDismiss = { previewCandidate = null },
                onKeep = {
                    previewCandidate = null
                    onKeepCandidate(candidate.photo.id)
                },
                onArchive = {
                    previewCandidate = null
                    onArchiveCandidate(candidate)
                },
            )
        }
}

@Composable
private fun ArchivesTopBar(
    count: Int,
    selectedCount: Int,
    estimatedBytes: Long,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    isLoading: Boolean,
    archivesEnabled: Boolean,
) {
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.archives_title),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
            )
            Text(
                text = if (selectedCount > 0) {
                    stringResource(R.string.archives_selected_summary, selectedCount)
                } else {
                    stringResource(R.string.archives_summary, count, formatBytes(estimatedBytes))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            IconButton(onClick = onRefresh, enabled = archivesEnabled) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.duplicates_refresh))
            }
        }
    }
}

@Composable
private fun ArchivesEnableRow(
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = stringResource(R.string.archives_toggle_title),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text = if (enabled) {
                        stringResource(R.string.archives_toggle_on)
                    } else {
                        stringResource(R.string.archives_toggle_off)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChanged,
            )
        }
    }
}

@Composable
private fun ArchiveCategorySelector(
    paymentsEnabled: Boolean,
    foodEnabled: Boolean,
    onPaymentsEnabledChanged: (Boolean) -> Unit,
    onFoodEnabledChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.54f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.archives_categories_title),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            )
            ArchiveCategoryRow(
                title = stringResource(R.string.archives_category_payments),
                subtitle = stringResource(R.string.archives_category_payments_subtitle),
                enabled = paymentsEnabled,
                onEnabledChanged = onPaymentsEnabledChanged,
            )
            ArchiveCategoryRow(
                title = stringResource(R.string.archives_category_food),
                subtitle = stringResource(R.string.archives_category_food_subtitle),
                enabled = foodEnabled,
                onEnabledChanged = onFoodEnabledChanged,
            )
        }
    }
}

@Composable
private fun ArchiveCategoryRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onEnabledChanged: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = enabled,
            onCheckedChange = onEnabledChanged,
        )
    }
}

@Composable
private fun RetentionSelector(
    selectedDays: Int,
    onRetentionDaysChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.archives_retention_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(7, 14, 30).forEach { days ->
                FilterChip(
                    selected = selectedDays == days,
                    onClick = { onRetentionDaysChanged(days) },
                    label = { Text(text = stringResource(R.string.archives_retention_days, days)) },
                )
            }
        }
    }
}

@Composable
private fun ArchivesActionRow(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onKeepSelected: () -> Unit,
    onMoveSelectedToTrash: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                onClick = if (selectedCount == totalCount && totalCount > 0) onClearSelection else onSelectAll,
                enabled = totalCount > 0,
            ) {
                Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = if (selectedCount == totalCount && totalCount > 0) {
                        stringResource(R.string.clear_selection)
                    } else {
                        stringResource(R.string.archives_select_all)
                    },
                )
            }
            TextButton(
                onClick = onKeepSelected,
                enabled = selectedCount > 0,
            ) {
                Text(text = stringResource(R.string.archives_keep_selected))
            }
        }
        Button(
            onClick = onMoveSelectedToTrash,
            enabled = selectedCount > 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(text = stringResource(R.string.archives_move_to_trash, selectedCount))
        }
    }
}

@Composable
private fun ArchiveCandidateTile(
    candidate: ArchiveCandidate,
    selected: Boolean,
    onPreview: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.78f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.06f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.72f),
                shape = RoundedCornerShape(8.dp),
            )
            .clickable(onClick = onPreview),
    ) {
        AsyncImage(
            model = Uri.parse(candidate.photo.uriString),
            contentDescription = candidate.photo.fileName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .clickable(onClick = onToggleSelection),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = stringResource(R.string.archives_tile_selected),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.56f))
                .padding(horizontal = 6.dp, vertical = 5.dp),
        ) {
            Text(
                text = candidate.reasons.joinToString(" / "),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.archives_candidate_meta,
                    (candidate.confidence * 100.0).toInt(),
                    formatBytes(candidate.photo.fileSize),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.82f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ArchiveCandidatePreviewDialog(
    candidate: ArchiveCandidate,
    retentionDays: Int,
    onDismiss: () -> Unit,
    onKeep: () -> Unit,
    onArchive: () -> Unit,
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
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = stringResource(R.string.archives_review_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = candidate.photo.fileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(modifier = Modifier.size(48.dp))
                }

                AsyncImage(
                    model = Uri.parse(candidate.photo.uriString),
                    contentDescription = candidate.photo.fileName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(
                            R.string.archives_candidate_meta,
                            (candidate.confidence * 100.0).toInt(),
                            formatBytes(candidate.photo.fileSize),
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = candidate.reasons.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextButton(
                            onClick = onKeep,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.archives_review_keep))
                        }
                        Button(
                            onClick = onArchive,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = stringResource(R.string.archives_review_archive, retentionDays))
                        }
                    }
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return when {
        safeBytes >= 1024L * 1024L -> "%.1f MB".format(safeBytes / (1024.0 * 1024.0))
        safeBytes >= 1024L -> "%d KB".format(safeBytes / 1024L)
        else -> "$safeBytes B"
    }
}
