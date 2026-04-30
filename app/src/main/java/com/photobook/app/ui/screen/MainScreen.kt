package com.photobook.app.ui.screen

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.paging.compose.LazyPagingItems
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.duplicates.DuplicateMatchKind
import com.photobook.app.feature.duplicates.DuplicatePhotoGroup
import com.photobook.app.feature.memories.MemoryStory
import com.photobook.app.feature.videoindex.VideoSearchMoment
import com.photobook.app.search.PhotoSource
import com.photobook.app.ui.component.EmptyState
import com.photobook.app.ui.component.PhotoGrid
import com.photobook.app.ui.component.SearchBar
import com.photobook.app.ui.component.SuggestionDropdown
import com.photobook.app.ui.component.WelcomeState
import com.photobook.app.search.SuggestionItem
import com.photobook.app.ui.model.HomeFeedMode
import com.photobook.app.ui.model.TimelineMark

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    query: String,
    results: LazyPagingItems<PhotoRecord>,
    resultCount: Int,
    searchReady: Boolean,
    favoritesOnly: Boolean,
    feedMode: HomeFeedMode,
    selectedPhotoIds: Set<Long>,
    timelineMarks: List<TimelineMark>,
    suggestions: List<SuggestionItem>,
    showSuggestions: Boolean,
    onThisDayStory: MemoryStory?,
    memoryStories: List<MemoryStory>,
    videoIndexingEnabled: Boolean,
    videoMoments: List<VideoSearchMoment>,
    duplicateGroups: List<DuplicatePhotoGroup>,
    isFindingDuplicates: Boolean,
    showDuplicateFinder: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSuggestionSelected: (SuggestionItem) -> Unit,
    onClearQuery: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onToggleVideoIndexing: () -> Unit,
    onShareSelected: (Set<Long>) -> Unit,
    onMoveSelectedToTrash: (Set<Long>) -> Unit,
    onCreatePdfSelected: (Set<Long>) -> Unit,
    onAddSelectedToVault: (Set<Long>) -> Unit,
    onClearSelection: () -> Unit,
    onPhotoClick: (PhotoRecord) -> Unit,
    onPhotoLongClick: (PhotoRecord) -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenVault: () -> Unit,
    onSelectFeedMode: (HomeFeedMode) -> Unit,
    onSourceSelected: (PhotoSource) -> Unit,
    onOpenDeclutter: () -> Unit,
    onOpenDuplicateFinder: () -> Unit,
    onRefreshDuplicates: () -> Unit,
    onDismissDuplicateFinder: () -> Unit,
    onDuplicatePhotoClick: (String, Int) -> Unit,
    onOpenOnThisDayStory: () -> Unit,
    onMemoryStorySelected: (MemoryStory) -> Unit,
    onVideoMomentClick: (VideoSearchMoment) -> Unit,
) {
    val isSelectionMode = selectedPhotoIds.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    0.0f to Color(0xFFFFF1EB),
                    1.0f to Color(0xFFACE0F9),
                    center = androidx.compose.ui.geometry.Offset(0.4f, 0.2f)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .rotate(3f)
                            .background(
                                Brush.linearGradient(
                                    listOf(Color(0xFF6366F1), Color(0xFFA855F7), Color(0xFFEC4899))
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✦", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "PhotoBook",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = (-0.5).sp
                        )
                    )
                }

                Surface(
                    color = Color(0xFFEEF2FF),
                    shape = RoundedCornerShape(full = true)
                ) {
                    Text(
                        "PRIVACY FIRST",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF4F46E5)
                        )
                    )
                }
            }

            // Search Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.White.copy(alpha = 0.8f))
                        .border(1.dp, Color.White, RoundedCornerShape(24.dp))
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    SearchBar(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = onSearchSubmitted,
                        onClear = onClearQuery,
                        onFocusChanged = onSearchFocusChanged,
                        autoFocus = searchReady && query.isBlank(),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    RefinedActionButton(
                        icon = Icons.Default.AutoFixHigh,
                        label = "Declutter",
                        onClick = onOpenDeclutter,
                        color = Color(0xFF6366F1),
                        enabled = searchReady && !isSelectionMode
                    )
                    RefinedActionButton(
                        icon = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = "Favorites",
                        onClick = onToggleFavoritesOnly,
                        color = if (favoritesOnly) Color(0xFFEC4899) else Color(0xFF94A3B8),
                        enabled = searchReady
                    )
                    RefinedActionButton(
                        icon = Icons.Default.Lock,
                        label = "Vault",
                        onClick = onOpenVault,
                        color = Color(0xFF8B5CF6),
                        enabled = searchReady
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Video Indexing Status
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Videocam,
                                contentDescription = null,
                                tint = if (videoIndexingEnabled) Color(0xFF22C55E) else Color(0xFF94A3B8),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Video indexing: ${if (videoIndexingEnabled) "On" else "Off"}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        TextButton(onClick = onToggleVideoIndexing) {
                            Text(
                                if (videoIndexingEnabled) "Disable" else "Enable",
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF4F46E5)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Source Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PhotoSource.all.forEach { source ->
                        RefinedGlassChip(
                            label = source.label,
                            onClick = { onSourceSelected(source) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Timeline / Utilities
                if (searchReady && query.isBlank() && !isSelectionMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.3f))
                            .padding(4.dp)
                    ) {
                        RefinedTabButton(
                            label = "Timeline",
                            selected = feedMode == HomeFeedMode.Timeline,
                            onClick = { onSelectFeedMode(HomeFeedMode.Timeline) },
                            modifier = Modifier.weight(1f)
                        )
                        RefinedTabButton(
                            label = "Utilities",
                            selected = feedMode == HomeFeedMode.Utilities,
                            onClick = { onSelectFeedMode(HomeFeedMode.Utilities) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Suggestions
            SuggestionDropdown(
                visible = showSuggestions,
                suggestions = suggestions,
                onSuggestionClick = onSuggestionSelected,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            )

            // Results
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
            ) {
                when {
                    !searchReady || (query.isBlank() && resultCount == 0) -> {
                        WelcomeState(
                            memories = memoryStories,
                            onThisDayStory = onThisDayStory,
                            onOnThisDayClick = onOpenOnThisDayStory,
                            onMemoryClick = onMemoryStorySelected,
                            compact = searchReady,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    query.isNotBlank() && resultCount == 0 -> {
                        EmptyState(modifier = Modifier.fillMaxSize())
                    }
                    else -> {
                        Column {
                            if (videoIndexingEnabled && videoMoments.isNotEmpty()) {
                                VideoMomentsCard(
                                    moments = videoMoments.take(4),
                                    onVideoMomentClick = onVideoMomentClick,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                val columns = if (maxWidth >= 700.dp) 5 else if (maxWidth >= 520.dp) 4 else 3
                                PhotoGrid(
                                    photos = results,
                                    columns = columns,
                                    timelineMarks = timelineMarks,
                                    selectedPhotoIds = selectedPhotoIds,
                                    isSelectionMode = isSelectionMode,
                                    onPhotoClick = onPhotoClick,
                                    onPhotoLongClick = onPhotoLongClick,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Selection Bar overlay
        if (isSelectionMode) {
             Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                color = Color.White.copy(alpha = 0.9f),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.selection_count, selectedPhotoIds.size),
                        fontWeight = FontWeight.Black
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = onClearSelection) { Icon(Icons.Default.Close, null) }
                        IconButton(onClick = { onCreatePdfSelected(selectedPhotoIds) }) { Icon(Icons.Default.PictureAsPdf, null) }
                        IconButton(onClick = { onShareSelected(selectedPhotoIds) }) { Icon(Icons.Default.Share, null) }
                        IconButton(onClick = { onAddSelectedToVault(selectedPhotoIds) }) { Icon(Icons.Default.Lock, null) }
                        IconButton(onClick = { onMoveSelectedToTrash(selectedPhotoIds) }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red)) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }

        // FAB
        if (!isSelectionMode) {
            FloatingActionButton(
                onClick = onOpenQrScanner,
                containerColor = Color(0xFF4F46E5),
                contentColor = Color.White,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
            ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null)
            }
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

@Composable
private fun RefinedActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    color: Color,
    enabled: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(enabled = enabled) { onClick() }.alpha(if (enabled) 1f else 0.5f)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(color.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
                .border(1.dp, color.copy(alpha = 0.2f), RoundedCornerShape(18.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = color)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
    }
}

@Composable
private fun RefinedGlassChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.White.copy(alpha = 0.6f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun RefinedTabButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (selected) Color.White else Color.Transparent
    val textColor = if (selected) Color.Black else Color.Gray

    Surface(
        onClick = onClick,
        modifier = modifier.padding(2.dp),
        color = backgroundColor,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                    color = textColor
                )
            )
        }
    }
}

@Composable
private fun VideoMomentsCard(
    moments: List<VideoSearchMoment>,
    onVideoMomentClick: (VideoSearchMoment) -> Unit,
) {
    if (moments.isEmpty()) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = null,
                    tint = Color(0xFF4F46E5),
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = stringResource(R.string.video_moments_title),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black),
                )
            }

            moments.forEach { moment ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onVideoMomentClick(moment) }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = moment.displayName,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                        Text(
                            text = moment.previewText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = formatVideoTimestamp(moment.timestampMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF4F46E5),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

private fun formatVideoTimestamp(timestampMs: Long): String {
    val totalSeconds = (timestampMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
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
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.duplicates_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                    )
                    Text(
                        text = stringResource(R.string.duplicates_subtitle),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Gray,
                    )
                }
                TextButton(
                    onClick = onRefresh,
                    enabled = !isLoading,
                ) {
                    Text(text = stringResource(R.string.duplicates_refresh), fontWeight = FontWeight.Black)
                }
            }

            if (isLoading) {
                 LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(2.dp))
            }

            Box(modifier = Modifier.height(400.dp)) {
                if (groups.isEmpty() && !isLoading) {
                    Text(
                        text = stringResource(R.string.duplicates_empty),
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(groups) { group ->
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
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 1.dp,
        color = Color.White.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
             Text(
                text = when (group.kind) {
                    DuplicateMatchKind.Exact -> "Exact Duplicates (${group.photos.size})"
                    DuplicateMatchKind.Similar -> "Similar Photos (${group.photos.size})"
                    DuplicateMatchKind.Burst -> "Best from Burst (${group.photos.size})"
                    DuplicateMatchKind.Blurry -> "Blurry Photos (${group.photos.size})"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Black)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                group.photos.take(4).forEachIndexed { index, photo ->
                    val isHero = photo.id == group.heroPhotoId
                    AsyncImage(
                        model = Uri.parse(photo.uriString),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isHero) 2.dp else 0.dp,
                                color = if (isHero) Color(0xFF4F46E5) else Color.Transparent,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .clickable { onPhotoClick(index) },
                    )
                }
            }
        }
    }
}
