package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.util.PerformanceProfiler

/**
 * Read-only startup surface. No photo card is clickable or long-clickable, so a partially hydrated
 * library can never drive selection, viewer, sharing, trash, Vault, Archive, or search behavior.
 */
@Composable
fun StartupPreviewContent(
    photos: List<PhotoRecord>,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "PhotoBook",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = "Preparing your private library…",
            style = MaterialTheme.typography.bodyMedium,
        )
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Recent photos",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Start,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .semantics { testTagsAsResourceId = true },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(
                items = photos,
                key = { photo -> photo.id },
            ) { photo ->
                StartupPreviewThumbnail(photo = photo)
            }
        }
    }
}

@Composable
private fun StartupPreviewThumbnail(photo: PhotoRecord) {
    val context = LocalContext.current
    val thumbSize = remember {
        PerformanceProfiler.from(context).thumbnailRequestSizePx
    }
    val request = remember(photo.uriString, thumbSize) {
        ImageRequest.Builder(context)
            .data(photo.uriString)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .size(thumbSize)
            .precision(Precision.EXACT)
            .build()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(STARTUP_PREVIEW_THUMBNAIL_TAG),
        shape = RoundedCornerShape(10.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        AsyncImage(
            model = request,
            contentDescription = photo.fileName,
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.Crop,
        )
    }
}

const val STARTUP_PREVIEW_THUMBNAIL_TAG = "startup_preview_thumbnail"
