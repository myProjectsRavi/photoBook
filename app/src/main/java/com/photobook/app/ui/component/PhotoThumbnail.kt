package com.photobook.app.ui.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.photobook.app.data.model.PhotoRecord

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhotoThumbnail(
    photo: PhotoRecord,
    isSelected: Boolean,
    showSelectionState: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardShape = remember { RoundedCornerShape(10.dp) }
    val haptic = LocalHapticFeedback.current
    val selectedOverlayAlpha = animateFloatAsState(
        targetValue = if (isSelected) 0.28f else 0f,
        label = "selected_overlay_alpha",
    )
    val context = LocalContext.current
    val thumbSize = remember {
        val am = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        if (am.isLowRamDevice) THUMBNAIL_REQUEST_SIZE_LOW_RAM else THUMBNAIL_REQUEST_SIZE_PX
    }
    val imageRequest = remember(photo.uriString, thumbSize) {
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
        modifier = modifier
            .padding(2.dp)
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            ),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageRequest,
                contentDescription = photo.mlTags.joinToString { it.label }.ifBlank { photo.fileName },
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            if (selectedOverlayAlpha.value > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = selectedOverlayAlpha.value)),
                )
            }

            if (showSelectionState) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .size(20.dp),
                )
            }
        }
    }
}

private const val THUMBNAIL_REQUEST_SIZE_PX = 512
private const val THUMBNAIL_REQUEST_SIZE_LOW_RAM = 256
