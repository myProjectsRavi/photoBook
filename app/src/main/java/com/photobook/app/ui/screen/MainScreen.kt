package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
    onRemoveHistorySuggestion: (String) -> Unit,
    onClearQuery: () -> Unit,
    onToggleFavoritesOnly: () -> Unit,
    onShareSelected: (List<PhotoRecord>) -> Unit,
    onClearSelection: () -> Unit,
    onPhotoClick: (Int) -> Unit,
    onPhotoLongClick: (Int) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    val isSelectionMode = selectedPhotoIds.isNotEmpty()
    val selectedPhotos = results.filter { it.id in selectedPhotoIds }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearchSubmitted,
            onClear = onClearQuery,
            onFocusChanged = onSearchFocusChanged,
        )

        SuggestionDropdown(
            visible = showSuggestions,
            suggestions = suggestions,
            onSuggestionClick = onSuggestionSelected,
            onRemoveHistoryClick = onRemoveHistorySuggestion,
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
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            IconButton(onClick = { onShareSelected(selectedPhotos) }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.share_selected),
                                )
                            }
                            IconButton(onClick = onClearSelection) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.clear_selection),
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
                        leadingIcon = {
                            Icon(
                                imageVector = if (favoritesOnly) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = null,
                            )
                        },
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
                    onToggleFavorite = onToggleFavorite,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
