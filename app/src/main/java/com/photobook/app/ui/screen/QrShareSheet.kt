package com.photobook.app.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.photobook.app.R
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.qrshare.QrBitmapEncoder
import com.photobook.app.feature.qrshare.QrShareEncoder
import com.photobook.app.feature.qrshare.QrShareGenerationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareSheet(
    photo: PhotoRecord,
    encoder: QrShareEncoder,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val generationResult by produceState<QrShareGenerationResult?>(
        initialValue = null,
        key1 = photo.id,
        key2 = photo.uriString,
    ) {
        value = encoder.generateForPhoto(photo)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.viewer_qr_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            when (val result = generationResult) {
                null -> {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.viewer_qr_preparing),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is QrShareGenerationResult.Error -> {
                    Text(
                        text = stringResource(R.string.viewer_qr_error),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is QrShareGenerationResult.TooLarge -> {
                    Text(
                        text = stringResource(
                            R.string.viewer_qr_too_large,
                            result.byteSize / 1024,
                            result.maxSupportedBytes / 1024,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is QrShareGenerationResult.Success -> {
                    var frameIndex by remember(result.packet.transferId) {
                        mutableIntStateOf(0)
                    }
                    LaunchedEffect(result.packet.transferId) {
                        frameIndex = 0
                        while (isActive) {
                            delay(FRAME_INTERVAL_MS)
                            frameIndex = (frameIndex + 1) % result.packet.frames.size
                        }
                    }

                    val frameBitmap by produceState<android.graphics.Bitmap?>(
                        initialValue = null,
                        key1 = result.packet.transferId,
                        key2 = frameIndex,
                    ) {
                        val payload = result.packet.frames[frameIndex]
                        value = withContext(Dispatchers.Default) {
                            QrBitmapEncoder.encode(payload)
                        }
                    }

                    Surface(
                        tonalElevation = 2.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp),
                    ) {
                        if (frameBitmap == null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                                Text(text = stringResource(R.string.viewer_qr_generating_frame))
                            }
                        } else {
                            Image(
                                bitmap = frameBitmap!!.asImageBitmap(),
                                contentDescription = stringResource(R.string.viewer_qr_frame),
                                modifier = Modifier
                                    .size(320.dp)
                                    .padding(8.dp),
                            )
                        }
                    }

                    Text(
                        text = stringResource(
                            R.string.viewer_qr_frame_progress,
                            frameIndex + 1,
                            result.packet.frames.size,
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = stringResource(
                            R.string.viewer_qr_scan_hint,
                            result.packet.byteSize / 1024,
                            result.packet.totalChunks,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private const val FRAME_INTERVAL_MS = 450L
