package com.photobook.app.ui.screen

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoViewerScreen(
    photos: List<PhotoRecord>,
    startIndex: Int,
    onDismiss: () -> Unit,
    onPageChanged: (Int) -> Unit,
    onToggleFavorite: (Long) -> Unit,
) {
    if (photos.isEmpty()) return

    val context = LocalContext.current
    val safeStart = startIndex.coerceIn(0, photos.lastIndex)
    val pagerState = rememberPagerState(initialPage = safeStart, pageCount = { photos.size })

    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        color = Color(0x22FFFFFF),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.viewer_close),
                                tint = Color.White,
                            )
                        }
                    }
                    Text(
                        text = stringResource(R.string.viewer_index, pagerState.currentPage + 1, photos.size),
                        color = Color.White,
                    )
                    val active = photos[pagerState.currentPage]
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(onClick = { onToggleFavorite(active.id) }) {
                                Icon(
                                    imageVector = if (active.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(R.string.viewer_favorite),
                                    tint = if (active.isFavorite) Color(0xFFFF6B6B) else Color.White,
                                )
                            }
                        }
                        Surface(
                            color = Color(0x22FFFFFF),
                            shape = RoundedCornerShape(28.dp),
                        ) {
                            IconButton(
                                onClick = {
                                    val uri = Uri.parse(active.uriString)
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = active.mimeType.ifBlank { "image/*" }
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        clipData = ClipData.newUri(
                                            context.contentResolver,
                                            active.fileName,
                                            uri,
                                        )
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    context.startActivity(
                                        Intent.createChooser(
                                            shareIntent,
                                            context.getString(R.string.viewer_share),
                                        )
                                    )
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = stringResource(R.string.viewer_share),
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { page ->
                    val photo = photos[page]

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = Uri.parse(photo.uriString),
                            contentDescription = photo.fileName,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                val active = photos[pagerState.currentPage]
                val noLocation = stringResource(R.string.no_location)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC111111))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (photos.size > 1) {
                        Text(
                            text = stringResource(R.string.viewer_swipe_hint),
                            color = Color.LightGray,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    Text(
                        text = active.fileName,
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.viewer_meta,
                            active.width,
                            active.height,
                            (active.fileSize / 1024).toInt(),
                            active.folderName,
                        ),
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val location = listOfNotNull(active.city, active.state, active.country)
                        .joinToString()
                        .ifBlank { noLocation }
                    Text(
                        text = location,
                        color = Color.LightGray,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        active.mlTags.forEach { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(text = tag.label) },
                                shape = RoundedCornerShape(16.dp),
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                                    labelColor = Color.White,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
