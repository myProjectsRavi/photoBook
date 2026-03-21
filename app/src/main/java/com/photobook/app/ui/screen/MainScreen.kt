package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    suggestions: List<SuggestionItem>,
    showSuggestions: Boolean,
    onQueryChange: (String) -> Unit,
    onSearchSubmitted: () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onSuggestionSelected: (SuggestionItem) -> Unit,
    onRemoveHistorySuggestion: (String) -> Unit,
    onClearQuery: () -> Unit,
    onPhotoClick: (Int) -> Unit,
) {
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

        if (!searchReady || query.isBlank()) {
            WelcomeState(modifier = Modifier.fillMaxSize())
        } else {
            Text(
                text = "Found ${results.size} photos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (searchReady && query.isNotBlank() && results.isEmpty()) {
            EmptyState(modifier = Modifier.fillMaxSize())
        } else if (searchReady && query.isNotBlank()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val columns = if (maxWidth >= 700.dp) 5 else if (maxWidth >= 520.dp) 4 else 3
                PhotoGrid(
                    photos = results,
                    columns = columns,
                    onPhotoClick = onPhotoClick,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
