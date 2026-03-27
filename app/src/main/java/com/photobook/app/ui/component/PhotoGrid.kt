package com.photobook.app.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.photobook.app.data.model.PhotoRecord

@Composable
fun PhotoGrid(
    photos: List<PhotoRecord>,
    columns: Int,
    selectedPhotoIds: Set<Long>,
    isSelectionMode: Boolean,
    onPhotoClick: (Int) -> Unit,
    onPhotoLongClick: (Int) -> Unit,
    onToggleFavorite: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
    ) {
        itemsIndexed(
            items = photos,
            key = { _, photo -> photo.id },
        ) { index, photo ->
            PhotoThumbnail(
                photo = photo,
                isSelected = photo.id in selectedPhotoIds,
                showSelectionState = isSelectionMode,
                onClick = { onPhotoClick(index) },
                onLongClick = { onPhotoLongClick(index) },
                onToggleFavorite = { onToggleFavorite(photo.id) },
            )
        }
    }
}
