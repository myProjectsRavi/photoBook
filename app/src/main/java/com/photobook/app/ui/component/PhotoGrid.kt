package com.photobook.app.ui.component

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.photobook.app.data.model.PhotoRecord
import androidx.paging.compose.LazyPagingItems

@Composable
fun PhotoGrid(
    photos: LazyPagingItems<PhotoRecord>,
    columns: Int,
    selectedPhotoIds: Set<Long>,
    isSelectionMode: Boolean,
    onPhotoClick: (PhotoRecord) -> Unit,
    onPhotoLongClick: (PhotoRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxSize(),
    ) {
        items(
            count = photos.itemCount,
            key = { index -> photos[index]?.id ?: "photo-placeholder-$index" },
        ) { index ->
            val photo = photos[index] ?: return@items
            PhotoThumbnail(
                photo = photo,
                isSelected = photo.id in selectedPhotoIds,
                showSelectionState = isSelectionMode,
                onClick = { onPhotoClick(photo) },
                onLongClick = { onPhotoLongClick(photo) },
            )
        }
    }
}
