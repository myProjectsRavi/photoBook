package com.photobook.app.ui.screen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoryStoryViewerScreen(
    title: String,
    photos: List<PhotoRecord>,
    onDismiss: () -> Unit,
    onOpenPhoto: (PhotoRecord) -> Unit,
) {
    if (photos.isEmpty()) return
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { photos.size })

    LaunchedEffect(pagerState.currentPage, photos.size) {
        if (photos.size <= 1) return@LaunchedEffect
        val current = pagerState.currentPage
        delay(STORY_ADVANCE_MS)
        if (pagerState.currentPage != current) return@LaunchedEffect
        if (current >= photos.lastIndex) {
            onDismiss()
        } else {
            // IMPORTANT: launch the scroll on `coroutineScope` (not on the LaunchedEffect's
            // own scope). `animateScrollToPage` updates `pagerState.currentPage` as soon as the
            // animation crosses the 50 % threshold, which re-keys this LaunchedEffect and
            // cancels its coroutine — leaving the pager visually "stuck" between two pages
            // (the half / half story bug). `rememberCoroutineScope` survives that re-key so
            // the snap animation always finishes cleanly.
            coroutineScope.launch {
                pagerState.animateScrollToPage(current + 1)
            }
        }
    }

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
            val infiniteTransition = rememberInfiniteTransition(label = "story_ken_burns")
            val scale by infiniteTransition.animateFloat(
                initialValue = 1.0f,
                targetValue = 1.08f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 7000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "story_ken_burns_scale",
            )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
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
}

private const val STORY_ADVANCE_MS = 4200L
