package com.photobook.app.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryStoryViewerScreen(
    title: String,
    photos: List<PhotoRecord>,
    onDismiss: () -> Unit,
    onOpenPhoto: (PhotoRecord) -> Unit,
    onSearchPhoto: (PhotoRecord) -> Unit,
) {
    if (photos.isEmpty()) return
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { photos.size })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val photo = photos[page]
            val density = LocalDensity.current
            val tapThresholdPx = with(density) { 120.dp.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(page, photos.size) {
                        detectTapGestures { position ->
                            when {
                                position.x < tapThresholdPx -> {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                }

                                position.x > size.width - tapThresholdPx -> {
                                    if (pagerState.currentPage < photos.lastIndex) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    } else {
                                        onDismiss()
                                    }
                                }

                                else -> onOpenPhoto(photo)
                            }
                        }
                    },
            ) {
                AsyncImage(
                    model = photo.uriString,
                    contentDescription = photo.fileName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(photos.size) { index ->
                    val active = index <= pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .size(height = 3.dp, width = 1.dp)
                            .background(
                                if (active) Color.White else Color.White.copy(alpha = 0.25f),
                                shape = MaterialTheme.shapes.small,
                            ),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.story_progress,
                            pagerState.currentPage + 1,
                            photos.size,
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            photos.getOrNull(pagerState.currentPage)?.let(onSearchPhoto)
                        },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = stringResource(R.string.viewer_search_text),
                            tint = Color.White,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.viewer_close),
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val current = pagerState.currentPage
                    if (current > 0) {
                        coroutineScope.launch { pagerState.animateScrollToPage(current - 1) }
                    }
                },
                enabled = pagerState.currentPage > 0,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.story_previous),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.story_user_paced_hint),
                color = Color.White.copy(alpha = 0.82f),
                style = MaterialTheme.typography.labelSmall,
            )
            IconButton(
                onClick = {
                    val current = pagerState.currentPage
                    if (current < photos.lastIndex) {
                        coroutineScope.launch { pagerState.animateScrollToPage(current + 1) }
                    }
                },
                enabled = pagerState.currentPage < photos.lastIndex,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = stringResource(R.string.story_next),
                    tint = Color.White,
                )
            }
        }
    }
}
