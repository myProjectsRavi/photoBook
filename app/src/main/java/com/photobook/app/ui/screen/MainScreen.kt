package com.photobook.app.ui.screen

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ui.component.EmptyState
import com.photobook.app.ui.component.PhotoGrid
import com.photobook.app.ui.component.SearchBar
import com.photobook.app.ui.component.SuggestionDropdown
import com.photobook.app.ui.component.WelcomeState
import com.photobook.app.search.SuggestionItem

@Composable
fun MainScreen(
    query: String,
    results: List<PhotoRecord>,
    searchReady: Boolean,
    favoritesOnly: Boolean,
    selectedPhotoIds: Set<Long>,
    suggestions: List<SuggestionItem>,
    showSuggestions: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSuggestionSelected: (SuggestionItem) -> Unit,
    onClearQuery: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onShareSelected: (List<PhotoRecord>) -> Unit,
    onClearSelection: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    onPhotoLongClick: (Int) -> Unit,
    onOpenQrScanner: () -> Unit,
) {
    val isSelectionMode = selectedPhotoIds.isNotEmpty()
    val selectedPhotos = results.filter { it.id in selectedPhotoIds }

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

            Text(
                text = stringResource(R.string.private_reassurance),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SuggestionDropdown(
                visible = showSuggestions,
                suggestions = suggestions,
                onSuggestionClick = onSuggestionSelected,
                modifier = Modifier.fillMaxWidth(),
            )

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
                                Button(onClick = { onShareSelected(selectedPhotos) }) {
                                    Icon(
                                        imageVector = Icons.Default.Share,
                                        contentDescription = null,
                                    )
                                    Text(
                                        text = stringResource(R.string.share_selected),
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
                            text = stringResource(R.string.found_photos, results.size),
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
                WelcomeState(modifier = Modifier.fillMaxSize())
            }

            if (searchReady && query.isNotBlank() && results.isEmpty()) {
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
    }
}
