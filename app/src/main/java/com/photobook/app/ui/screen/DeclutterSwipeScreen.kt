package com.photobook.app.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.declutter.DeclutterReason
import com.photobook.app.feature.declutter.DeclutterSession

@Composable
fun DeclutterSwipeScreen(
    session: DeclutterSession,
    currentPhoto: PhotoRecord?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onKeepCurrent: () -> Unit,
    onTrashCurrent: () -> Unit,
    onUndoLast: () -> Unit,
    onApplyTrash: (Set<Long>) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.95f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.viewer_close),
                            tint = Color.White,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.declutter_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                        Text(
                            text = if (session.candidates.isEmpty()) {
                                stringResource(R.string.declutter_empty)
                            } else {
                                stringResource(
                                    R.string.declutter_progress,
                                    session.currentIndex.coerceAtMost(session.candidates.size),
                                    session.candidates.size,
                                )
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }

                if (session.markedTrashIds.isNotEmpty()) {
                    Button(onClick = { onApplyTrash(session.markedTrashIds) }) {
                        Text(text = stringResource(R.string.declutter_apply, session.markedTrashIds.size))
                    }
                }
            }

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                session.candidates.isEmpty() -> {
                    EmptyStateBody(
                        message = stringResource(R.string.declutter_empty),
                        actionLabel = null,
                        onAction = {},
                    )
                }

                session.isComplete -> {
                    EmptyStateBody(
                        message = stringResource(
                            R.string.declutter_done_summary,
                            session.markedTrashIds.size,
                        ),
                        actionLabel = if (session.markedTrashIds.isEmpty()) {
                            null
                        } else {
                            stringResource(R.string.declutter_apply, session.markedTrashIds.size)
                        },
                        onAction = { onApplyTrash(session.markedTrashIds) },
                    )
                }

                currentPhoto == null -> {
                    EmptyStateBody(
                        message = stringResource(R.string.declutter_loading_photo),
                        actionLabel = null,
                        onAction = {},
                    )
                }

                else -> {
                    val reason = remember(session.currentIndex, session.candidates) {
                        session.currentCandidate?.reason
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .pointerInput(session.currentIndex, session.candidates.size) {
                                var dragX = 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, dragAmount ->
                                        dragX += dragAmount
                                    },
                                    onDragEnd = {
                                        when {
                                            dragX <= -SWIPE_THRESHOLD_PX -> onTrashCurrent()
                                            dragX >= SWIPE_THRESHOLD_PX -> onKeepCurrent()
                                        }
                                        dragX = 0f
                                    },
                                )
                            },
                    ) {
                        AsyncImage(
                            model = currentPhoto.uriString,
                            contentDescription = currentPhoto.fileName,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black, RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White.copy(alpha = 0.12f),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.declutter_reason_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.7f),
                            )
                            Text(
                                text = reasonText(reason),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onTrashCurrent,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.declutter_trash),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Button(
                            onClick = onKeepCurrent,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Done,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.declutter_keep),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }

                    if ((session.markedTrashIds.isNotEmpty() || session.keptIds.isNotEmpty()) && !session.isComplete) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Button(onClick = onUndoLast) {
                                Text(text = stringResource(R.string.declutter_undo))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateBody(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
        )
        if (actionLabel != null) {
            Button(
                modifier = Modifier
                    .padding(top = 14.dp)
                    .height(40.dp),
                onClick = onAction,
            ) {
                Text(text = actionLabel)
            }
        }
    }
}

@Composable
private fun reasonText(reason: DeclutterReason?): String {
    return when (reason) {
        DeclutterReason.ExactDuplicate -> stringResource(R.string.declutter_reason_exact)
        DeclutterReason.SimilarDuplicate -> stringResource(R.string.declutter_reason_similar)
        DeclutterReason.BurstExtra -> stringResource(R.string.declutter_reason_burst)
        DeclutterReason.Blurry -> stringResource(R.string.declutter_reason_blurry)
        DeclutterReason.Screenshot -> stringResource(R.string.declutter_reason_screenshot)
        DeclutterReason.Download -> stringResource(R.string.declutter_reason_download)
        DeclutterReason.Social -> stringResource(R.string.declutter_reason_social)
        DeclutterReason.Document -> stringResource(R.string.declutter_reason_document)
        DeclutterReason.Meme -> stringResource(R.string.declutter_reason_meme)
        null -> stringResource(R.string.declutter_reason_unknown)
    }
}

private const val SWIPE_THRESHOLD_PX = 220f
