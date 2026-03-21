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
    onPhotoClick: (Int) -> Unit,
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
                onClick = { onPhotoClick(index) },
            )
        }
    }
}
