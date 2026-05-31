package com.photobook.app.ui.screen

import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items as lazyItems
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
import androidx.compose.ui.geometry.Offset
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
import com.photobook.app.search.PhotoSource
import com.photobook.app.ui.component.EmptyState
import com.photobook.app.ui.component.PhotoGrid
import com.photobook.app.ui.component.SearchBar
import com.photobook.app.ui.component.SuggestionDropdown
import com.photobook.app.ui.component.WelcomeState
import com.photobook.app.search.SuggestionItem
import com.photobook.app.ui.model.HomeFeedMode
import com.photobook.app.ui.model.TimelineMark

private val MeshBase = Color(0xFFFFF0E4)
private val MeshWarm = Color(0xFFFFD8B8)
private val MeshRose = Color(0xFFFFD4E4)
private val MeshMint = Color(0xFFE9F3FF)
private val GlassSurface = Color(0xCCFFFFFF)
private val GlassBorder = Color(0xF2FFFFFF)
private val AccentIndigo = Color(0xFF4F46E5)
private val AccentPink = Color(0xFFEC4899)
private val AccentViolet = Color(0xFF8B5CF6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    query: String,
    results: LazyPagingItems<PhotoRecord>,
    resultCount: Int,
    searchReady: Boolean,
    favoritesOnly: Boolean,
    feedMode: HomeFeedMode,
    reelsEnabled: Boolean,
    selectedPhotoIds: Set<Long>,
    timelineMarks: List<TimelineMark>,
    suggestions: List<SuggestionItem>,
    showSuggestions: Boolean,
    onThisDayStory: MemoryStory?,
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
    onToggleReels: () -> Unit,
    onLogoClick: () -> Unit,
    onShareSelected: (Set<Long>) -> Unit,
    onMoveSelectedToTrash: (Set<Long>) -> Unit,
    onCreatePdfSelected: (Set<Long>) -> Unit,
    onAddSelectedToVault: (Set<Long>) -> Unit,
    onCopyTextFromPhoto: (Long) -> Unit,
    onGenerateQrForPhoto: (Long) -> Unit,
    onClearSelection: () -> Unit,
    onPhotoClick: (PhotoRecord) -> Unit,
    onPhotoLongClick: (PhotoRecord) -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenReels: () -> Unit,
    onSelectFeedMode: (HomeFeedMode) -> Unit,
    onSourceSelected: (PhotoSource) -> Unit,
    onOpenDeclutter: () -> Unit,
    onOpenDuplicateFinder: () -> Unit,
    onRefreshDuplicates: () -> Unit,
    onDismissDuplicateFinder: () -> Unit,
    onDuplicatePhotoClick: (String, Int) -> Unit,
    onOpenOnThisDayStory: () -> Unit,
    onMemoryStorySelected: (MemoryStory) -> Unit,
) {
    val isSelectionMode = selectedPhotoIds.isNotEmpty()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors = listOf(MeshBase, MeshMint)))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(MeshWarm.copy(alpha = 0.95f), Color.Transparent),
                        center = Offset(240f, 120f),
                        radius = 900f,
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(MeshRose.copy(alpha = 0.85f), Color.Transparent),
                        center = Offset(860f, 220f),
                        radius = 900f,
                    )
                )
        )

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                    ) { onLogoClick() },
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .rotate(3f)
                            .background(
                                Brush.linearGradient(
                                    listOf(AccentIndigo, AccentViolet, AccentPink)
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
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.18f)),
                ) {
                    Row(
                        modifier = Modifier
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        AccentIndigo,
                                        AccentViolet,
                                        AccentPink,
                                    )
                                ),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .background(Color.White.copy(alpha = 0.16f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("✦", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                        Text(
                            "PRO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.2.sp
                            )
                        )
                    }
                }
            }

            // Search Area
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp)),
                    shape = RoundedCornerShape(28.dp),
                    color = GlassSurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                    tonalElevation = 2.dp,
                    shadowElevation = 10.dp,
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                        SearchBar(
                            query = query,
                            onQueryChange = onQueryChange,
                            onSearch = onSearchSubmitted,
                            onClear = onClearQuery,
                            onFocusChanged = onSearchFocusChanged,
                            autoFocus = searchReady && query.isBlank(),
                        )
                    }
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
                        color = AccentIndigo,
                        enabled = searchReady && !isSelectionMode
                    )
                    RefinedActionButton(
                        icon = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        label = "Favorites",
                        onClick = onToggleFavoritesOnly,
                        color = if (favoritesOnly) AccentPink else Color(0xFF64748B),
                        enabled = searchReady
                    )
                    RefinedActionButton(
                        icon = Icons.Default.Slideshow,
                        label = "Reels",
                        onClick = onToggleReels,
                        color = if (reelsEnabled) AccentIndigo else Color(0xFF64748B),
                        enabled = searchReady
                    )
                    RefinedActionButton(
                        icon = Icons.Default.Delete,
                        label = "Trash",
                        onClick = onOpenReels, // wired to Trash via MainActivity
                        color = AccentViolet,
                        enabled = searchReady && !isSelectionMode
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

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

                // (Removed Timeline/Screenshots tab row — Timeline is the only feed.)
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

        // Selection Bar overlay
        if (isSelectionMode) {
             Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                color = GlassSurface.copy(alpha = 0.94f),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
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
                        if (selectedPhotoIds.size == 1) {
                            IconButton(onClick = { onCopyTextFromPhoto(selectedPhotoIds.first()) }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy text from photo") }
                        }
                        IconButton(onClick = { onCreatePdfSelected(selectedPhotoIds) }) { Icon(Icons.Default.PictureAsPdf, null) }
                        IconButton(onClick = { onShareSelected(selectedPhotoIds) }) { Icon(Icons.Default.Share, null) }
                        IconButton(onClick = { onMoveSelectedToTrash(selectedPhotoIds) }, colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red)) { Icon(Icons.Default.Delete, null) }
                    }
                }
            }
        }

        // FAB removed: offline QR share/scan is temporarily disabled in the UI.

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
        modifier = Modifier
            .clickable(enabled = enabled) { onClick() }
            .alpha(if (enabled) 1f else 0.5f)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(Color.White.copy(alpha = 0.66f), RoundedCornerShape(16.dp))
                .border(1.dp, color.copy(alpha = 0.20f), RoundedCornerShape(16.dp)),
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
        color = Color.White.copy(alpha = 0.68f),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
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
    val backgroundColor = if (selected) Color.White.copy(alpha = 0.92f) else Color.Transparent
    val textColor = if (selected) Color(0xFF0F172A) else Color(0xFF64748B)

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
                        lazyItems(
                            items = groups,
                            key = { it.id },
                        ) { group ->
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
        color = Color.White.copy(alpha = 0.74f),
        border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder)
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
