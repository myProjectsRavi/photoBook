package com.photobook.app.ui.screen

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.phototextsearch.PhotoTextSearchController
import com.photobook.app.feature.phototextsearch.mediaStorePhotoTextLayoutSource
import com.photobook.app.ui.component.PhotoTextSearchControls
import com.photobook.app.ui.component.PhotoTextSearchOverlay

/**
 * Instagram Reels-style vertical photo browser.
 * User swipes UP to see the next photo, DOWN for previous.
 * Fullscreen, immersive, offline-first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoReelsScreen(
    photos: List<PhotoRecord>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onToggleFavorite: (Long) -> Unit,
    onSharePhoto: (PhotoRecord) -> Unit,
) {
    if (photos.isEmpty()) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textSource = remember(context.applicationContext) {
        mediaStorePhotoTextLayoutSource(context.applicationContext)
    }
    val searchController = remember(textSource, scope) {
        PhotoTextSearchController(source = textSource, scope = scope)
    }
    val searchState by searchController.state.collectAsState()
    val safeStart = startIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeStart, pageCount = { photos.size })

    LaunchedEffect(pagerState.currentPage) {
        val active = photos.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        searchController.activate(active.id, active.uriString)
    }

    DisposableEffect(Unit) {
        onDispose { searchController.dispose() }
    }

    BackHandler(enabled = searchState.isOpen) {
        searchController.close()
    }

    Dialog(
        onDismissRequest = {
            searchController.close()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            VerticalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { page -> photos.getOrNull(page)?.id ?: page.toLong() },
                userScrollEnabled = !searchState.isOpen,
            ) { page ->
                photos.getOrNull(page)?.let { photo ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                    AsyncImage(
                        model = Uri.parse(photo.uriString),
                        contentDescription = photo.fileName,
                        // Fit while searching so every matched OCR polygon remains visible and
                        // shares the exact same geometry transform as the highlight overlay.
                        contentScale = if (searchState.isOpen && page == pagerState.currentPage) {
                            ContentScale.Fit
                        } else {
                            ContentScale.Crop
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    if (
                        searchState.isOpen &&
                        page == pagerState.currentPage &&
                        searchState.layout != null &&
                        searchState.matches.isNotEmpty()
                    ) {
                        PhotoTextSearchOverlay(
                            layout = searchState.layout!!,
                            matches = searchState.matches,
                            activeMatchIndex = searchState.activeMatchIndex,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    // Bottom gradient overlay with file info
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                                )
                            )
                            .padding(16.dp),
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = photo.fileName,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val location = listOfNotNull(photo.city, photo.state, photo.country)
                                .joinToString()
                            if (location.isNotBlank()) {
                                Text(
                                    text = location,
                                    color = Color.White.copy(alpha = 0.8f),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = photo.folderName,
                                color = Color.White.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }

                    // Right side action buttons (like Instagram Reels)
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Surface(color = Color(0x44000000), shape = RoundedCornerShape(50)) {
                            IconButton(
                                modifier = Modifier.size(48.dp),
                                onClick = { searchController.open() },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.viewer_search_text),
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Surface(color = Color(0x44000000), shape = RoundedCornerShape(50)) {
                            IconButton(onClick = { onToggleFavorite(photo.id) }) {
                                Icon(
                                    imageVector = if (photo.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = "Favorite",
                                    tint = if (photo.isFavorite) Color(0xFFFF6B6B) else Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                        Surface(color = Color(0x44000000), shape = RoundedCornerShape(50)) {
                            IconButton(onClick = { onSharePhoto(photo) }) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                    }
                }
            }

            if (searchState.isOpen) {
                PhotoTextSearchControls(
                    state = searchState,
                    onQueryChange = searchController::setQuery,
                    onPrevious = searchController::previousMatch,
                    onNext = searchController::nextMatch,
                    onShowMatch = { /* Fit mode keeps every match visible at 1x. */ },
                    onRetry = searchController::retry,
                    onClose = searchController::close,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Close button top-left
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                color = Color(0x44000000),
                shape = RoundedCornerShape(50),
            ) {
                IconButton(
                    onClick = {
                        searchController.close()
                        onDismiss()
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                    )
                }
            }

            // Page indicator top-right
            Surface(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                color = Color(0x44000000),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${photos.size}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}
