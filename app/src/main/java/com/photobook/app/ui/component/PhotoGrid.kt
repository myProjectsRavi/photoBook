package com.photobook.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ui.model.TimelineMark
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun PhotoGrid(
    photos: LazyPagingItems<PhotoRecord>,
    columns: Int,
    timelineMarks: List<TimelineMark>,
    selectedPhotoIds: Set<Long>,
    isSelectionMode: Boolean,
    onPhotoClick: (PhotoRecord) -> Unit,
    onPhotoLongClick: (PhotoRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val haptics = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    var scrubTrackHeightPx by remember { mutableFloatStateOf(1f) }
    var scrubY by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }
    var activeTimelineLabel by remember { mutableStateOf<String?>(null) }
    var lastScrubTargetIndex by remember { mutableIntStateOf(-1) }

    val sortedMarks = remember(timelineMarks) { timelineMarks.sortedBy { mark -> mark.index } }

    fun updateScrubPosition(rawY: Float) {
        val itemCount = photos.itemCount
        if (itemCount <= 0) return

        val clampedY = rawY.coerceIn(0f, scrubTrackHeightPx)
        val fraction = (clampedY / scrubTrackHeightPx).coerceIn(0f, 1f)
        val targetIndex = ((itemCount - 1) * fraction).roundToInt().coerceIn(0, itemCount - 1)
        scrubY = clampedY

        if (targetIndex != lastScrubTargetIndex) {
            lastScrubTargetIndex = targetIndex
            coroutineScope.launch {
                gridState.scrollToItem(targetIndex)
            }
        }

        val timelineLabel = timelineLabelForIndex(targetIndex, sortedMarks)
        if (timelineLabel != activeTimelineLabel) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            activeTimelineLabel = timelineLabel
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
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

        if (photos.itemCount > 1 && sortedMarks.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
                    .width(38.dp)
                    .padding(end = 6.dp, top = 12.dp, bottom = 12.dp)
                    .onSizeChanged { size ->
                        scrubTrackHeightPx = size.height.toFloat().coerceAtLeast(1f)
                    }
                    .pointerInput(photos.itemCount, sortedMarks) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isScrubbing = true
                                updateScrubPosition(offset.y)
                            },
                            onDragEnd = {
                                isScrubbing = false
                                activeTimelineLabel = null
                            },
                            onDragCancel = {
                                isScrubbing = false
                                activeTimelineLabel = null
                            },
                        ) { change, dragAmount ->
                            change.consume()
                            updateScrubPosition(scrubY + dragAmount)
                        }
                    },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .align(Alignment.Center)
                        .background(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f),
                            shape = RoundedCornerShape(20.dp),
                        ),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(
                            y = with(density) {
                                (scrubY - if (isScrubbing) 10.dp.toPx() else 7.dp.toPx()).toDp()
                            },
                        )
                        .size(if (isScrubbing) 20.dp else 14.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isScrubbing) 0.92f else 0.72f),
                            shape = CircleShape,
                        ),
                )
            }
        }

        if (isScrubbing && !activeTimelineLabel.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .offset(y = with(density) { (scrubY - scrubTrackHeightPx / 2f).toDp() }),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                tonalElevation = 4.dp,
                shadowElevation = 6.dp,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = activeTimelineLabel.orEmpty(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun timelineLabelForIndex(
    targetIndex: Int,
    marks: List<TimelineMark>,
): String {
    if (marks.isEmpty()) return ""
    var lastLabel = marks.first().label
    marks.forEach { mark ->
        if (mark.index > targetIndex) return lastLabel
        lastLabel = mark.label
    }
    return lastLabel
}
