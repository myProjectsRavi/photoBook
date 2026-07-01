package com.photobook.app.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareSheet(
    photo: PhotoRecord,
    encoder: QrShareEncoder,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val qrResult by produceState<QrPacketResult>(
        initialValue = QrPacketResult.Loading,
        key1 = photo.id,
    ) {
        val singleFrame = encoder.generateSingleFrameQr(photo)
        value = when (singleFrame) {
            is QrShareGenerationResult.Success -> QrPacketResult.Ready(singleFrame.packet)
            is QrShareGenerationResult.TooLarge -> {
                when (val multiFrame = encoder.generateForPhoto(photo)) {
                    is QrShareGenerationResult.Success -> QrPacketResult.Ready(multiFrame.packet)
                    is QrShareGenerationResult.TooLarge -> QrPacketResult.TooLarge(
                        byteSize = multiFrame.byteSize,
                        maxSupportedBytes = multiFrame.maxSupportedBytes,
                    )
                    is QrShareGenerationResult.Error -> QrPacketResult.Error
                }
            }
            is QrShareGenerationResult.Error -> QrPacketResult.Error
        }
    }

    var frameIndex by remember(photo.id) { mutableIntStateOf(0) }
    var currentBitmap by remember(photo.id) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(qrResult) {
        if (qrResult !is QrPacketResult.Ready) return@LaunchedEffect
        frameIndex = 0
    }

    LaunchedEffect(qrResult, frameIndex) {
        val ready = qrResult as? QrPacketResult.Ready ?: return@LaunchedEffect
        val payload = ready.packet.frames.getOrNull(frameIndex) ?: return@LaunchedEffect
        val nextBitmap = runCatching {
            withContext(Dispatchers.Default) {
                QrBitmapEncoder.encode(
                    payload,
                    sizePx = if (ready.packet.frames.size == 1) 600 else 720,
                )
            }
        }.getOrNull()
        val oldBitmap = currentBitmap
        currentBitmap = nextBitmap
        if (oldBitmap != null && oldBitmap !== nextBitmap && !oldBitmap.isRecycled) {
            oldBitmap.recycle()
        }
    }

    LaunchedEffect(qrResult) {
        val ready = qrResult as? QrPacketResult.Ready ?: return@LaunchedEffect
        if (ready.packet.frames.size <= 1) return@LaunchedEffect
        while (true) {
            delay(QR_FRAME_INTERVAL_MS.toLong())
            frameIndex = (frameIndex + 1) % ready.packet.frames.size
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.viewer_qr_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )

            when (val result = qrResult) {
                QrPacketResult.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                QrPacketResult.Error -> {
                    Text(
                        text = stringResource(R.string.viewer_qr_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is QrPacketResult.TooLarge -> {
                    Text(
                        text = stringResource(
                            R.string.viewer_qr_too_large,
                            result.byteSize / 1024,
                            result.maxSupportedBytes / 1024,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is QrPacketResult.Ready -> {
                    DisposableEffect(result.packet.transferId) {
                        onDispose {
                            runCatching { if (currentBitmap != null && !currentBitmap!!.isRecycled) currentBitmap!!.recycle() }
                            currentBitmap = null
                        }
                    }
                    val bitmap = currentBitmap
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.viewer_qr_frame),
                            modifier = Modifier
                                .size(320.dp)
                                .padding(8.dp),
                        )
                    } else {
                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    }
                    if (result.packet.frames.size > 1) {
                        Text(
                            text = stringResource(
                                R.string.viewer_qr_frame_progress,
                                frameIndex + 1,
                                result.packet.frames.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Text(
                        text = if (result.packet.frames.size == 1) {
                            stringResource(
                                R.string.viewer_qr_single_hint,
                                result.packet.byteSize.toKilobytesCeil(),
                            )
                        } else {
                            stringResource(
                                R.string.viewer_qr_scan_hint,
                                result.packet.byteSize.toKilobytesCeil(),
                                result.packet.totalChunks,
                                estimatedScanSeconds(result.packet.frames.size),
                            )
                        },
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private sealed interface QrPacketResult {
    data object Loading : QrPacketResult
    data object Error : QrPacketResult
    data class TooLarge(
        val byteSize: Int,
        val maxSupportedBytes: Int,
    ) : QrPacketResult
    data class Ready(
        val packet: com.photobook.app.feature.qrshare.QrSharePacket,
    ) : QrPacketResult
}

private fun estimatedScanSeconds(frameCount: Int): Int {
    if (frameCount <= 1) return 1
    val cycleMs = frameCount * QR_FRAME_INTERVAL_MS
    val twoPassScanMs = cycleMs * 2
    return (twoPassScanMs / 1000).coerceAtLeast(2)
}

private fun Int.toKilobytesCeil(): Int {
    return ((coerceAtLeast(1) + 1023) / 1024).coerceAtLeast(1)
}

private const val QR_FRAME_INTERVAL_MS = 220
