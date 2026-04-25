package com.photobook.app.ui.screen

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.duplicates.DuplicateMatchKind
import com.photobook.app.feature.duplicates.DuplicatePhotoGroup
import com.photobook.app.feature.memories.MemoryStory
import com.photobook.app.search.PhotoSource
import com.photobook.app.ui.component.EmptyState
import com.photobook.app.ui.component.PhotoGrid
import com.photobook.app.ui.component.SearchBar
import com.photobook.app.ui.component.SuggestionDropdown
import com.photobook.app.ui.component.WelcomeState
import com.photobook.app.search.SuggestionItem
import androidx.paging.compose.LazyPagingItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    query: String,
    results: LazyPagingItems<PhotoRecord>,
    resultCount: Int,
    searchReady: Boolean,
    favoritesOnly: Boolean,
    selectedPhotoIds: Set<Long>,
    suggestions: List<SuggestionItem>,
    showSuggestions: Boolean,
    memoryStories: List<MemoryStory>,
    duplicateGroups: List<DuplicatePhotoGroup>,
    isFindingDuplicates: Boolean,
    showDuplicateFinder: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSuggestionSelected: (SuggestionItem) -> Unit,
    onClearQuery: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onShareSelected: (Set<Long>) -> Unit,
    onMoveSelectedToTrash: (Set<Long>) -> Unit,
    onCreatePdfSelected: (Set<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onPhotoClick: (PhotoRecord) -> Unit,
    onPhotoLongClick: (PhotoRecord) -> Unit,
    onOpenQrScanner: () -> Unit,
    onSourceSelected: (PhotoSource) -> Unit,
    onOpenDuplicateFinder: () -> Unit,
    onRefreshDuplicates: () -> Unit,
    onDismissDuplicateFinder: () -> Unit,
    onDuplicatePhotoClick: (String, Int) -> Unit,
    onMemoryStorySelected: (MemoryStory) -> Unit,
) {
    val isSelectionMode = selectedPhotoIds.isNotEmpty()

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchBar(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = onSearchSubmitted,
                onClear = onClearQuery,
                onFocusChanged = onSearchFocusChanged,
                autoFocus = searchReady && query.isBlank(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.private_reassurance),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onOpenDuplicateFinder,
                    enabled = searchReady && !isSelectionMode,
                ) {
                    Text(text = stringResource(R.string.duplicates_action))
                }
            }

            SuggestionDropdown(
                visible = showSuggestions,
                suggestions = suggestions,
                onSuggestionClick = onSuggestionSelected,
                modifier = Modifier.fillMaxWidth(),
            )

            if (searchReady && !isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PhotoSource.all.forEach { source ->
                        AssistChip(
                            onClick = { onSourceSelected(source) },
                            label = { Text(text = source.label) },
                            shape = RoundedCornerShape(14.dp),
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ),
                        )
                    }
                }
            }

            if (searchReady && query.isNotBlank()) {
                if (isSelectionMode) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        tonalElevation = 2.dp,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.selection_count, selectedPhotoIds.size),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                TextButton(onClick = onClearSelection) {
                                    Text(text = stringResource(R.string.clear_selection))
                                }
                                Button(onClick = { onCreatePdfSelected(selectedPhotoIds) }) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = stringResource(R.string.create_pdf_selected),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                Button(onClick = { onShareSelected(selectedPhotoIds) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = stringResource(R.string.share_selected),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                                Button(onClick = { onMoveSelectedToTrash(selectedPhotoIds) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = stringResource(R.string.trash_selected),
                                        modifier = Modifier.padding(start = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.found_photos, resultCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FilterChip(
                            selected = favoritesOnly,
                            onClick = onToggleFavoritesOnly,
                            label = { Text(text = stringResource(R.string.favorites_filter)) },
                        )
                    }
                }
            }

            if (!searchReady || query.isBlank()) {
                WelcomeState(
                    memories = memoryStories,
                    onMemoryClick = onMemoryStorySelected,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (searchReady && query.isNotBlank() && resultCount == 0) {
                EmptyState(modifier = Modifier.fillMaxSize())
            } else if (searchReady && query.isNotBlank()) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val columns = if (maxWidth >= 700.dp) 5 else if (maxWidth >= 520.dp) 4 else 3
                    PhotoGrid(
                        photos = results,
                        columns = columns,
                        selectedPhotoIds = selectedPhotoIds,
                        isSelectionMode = isSelectionMode,
                        onPhotoClick = onPhotoClick,
                        onPhotoLongClick = onPhotoLongClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = onOpenQrScanner,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 20.dp),
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = stringResource(R.string.scan_qr_action),
            )
        }

        if (showDuplicateFinder) {
            DuplicateFinderSheet(
                groups = duplicateGroups,
                isLoading = isFindingDuplicates,
                onDismiss = onDismissDuplicateFinder,
                onRefresh = onRefreshDuplicates,
                onPhotoClick = onDuplicatePhotoClick,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DuplicateFinderSheet(
    groups: List<DuplicatePhotoGroup>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onPhotoClick: (String, Int) -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.duplicates_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.duplicates_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                ) {
                    Text(text = stringResource(R.string.duplicates_refresh))
                }
            }

            when {
                isLoading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.duplicates_scanning),
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                groups.isEmpty() -> {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = stringResource(R.string.duplicates_empty),
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(groups, key = { group -> group.id }) { group ->
                            DuplicateGroupCard(
                                group = group,
                                onPhotoClick = { index -> onPhotoClick(group.id, index) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicatePhotoGroup,
    onPhotoClick: (Int) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when (group.kind) {
                    DuplicateMatchKind.Exact -> stringResource(
                        R.string.duplicates_exact_group,
                        group.photos.size,
                    )

                    DuplicateMatchKind.Similar -> stringResource(
                        R.string.duplicates_similar_group,
                        group.photos.size,
                    )

                    DuplicateMatchKind.Burst -> stringResource(
                        R.string.duplicates_burst_group,
                        group.photos.size,
                    )

                    DuplicateMatchKind.Blurry -> stringResource(
                        R.string.duplicates_blurry_group,
                        group.photos.size,
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = when (group.kind) {
                    DuplicateMatchKind.Exact -> stringResource(R.string.duplicates_exact_hint)
                    DuplicateMatchKind.Similar -> stringResource(R.string.duplicates_similar_hint)
                    DuplicateMatchKind.Burst -> stringResource(R.string.duplicates_burst_hint)
                    DuplicateMatchKind.Blurry -> stringResource(R.string.duplicates_blurry_hint)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (group.kind == DuplicateMatchKind.Burst && group.heroPhotoId != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = stringResource(R.string.duplicates_hero_shot),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                group.photos.take(5).forEachIndexed { index, photo ->
                    val isHero = photo.id == group.heroPhotoId
                    AsyncImage(
                        model = Uri.parse(photo.uriString),
                        contentDescription = photo.fileName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isHero) 2.dp else 0.dp,
                                color = if (isHero) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onPhotoClick(index) },
                    )
                }
            }
        }
    }
}
