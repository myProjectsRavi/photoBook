package com.photobook.app.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareSheet(
    photo: PhotoRecord,
    encoder: QrShareEncoder,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Generate full multi-frame QR sequence (animated cycling). Works for any photo size.
    val qrResult by produceState<QrAnimResult>(
        initialValue = QrAnimResult.Loading,
        key1 = photo.id,
    ) {
        value = when (val gen = encoder.generateForPhoto(photo)) {
            is QrShareGenerationResult.Success -> {
                val frames = gen.packet.frames
                val bitmaps = withContext(Dispatchers.Default) {
                    frames.map { payload -> QrBitmapEncoder.encode(payload, sizePx = 720) }
                }
                QrAnimResult.Ready(
                    bitmaps = bitmaps,
                    totalChunks = gen.packet.totalChunks,
                    fileName = gen.packet.fileName,
                )
            }
            is QrShareGenerationResult.TooLarge -> QrAnimResult.Error
            is QrShareGenerationResult.Error -> QrAnimResult.Error
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
                QrAnimResult.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                QrAnimResult.Error -> {
                    Text(
                        text = stringResource(R.string.viewer_qr_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is QrAnimResult.Ready -> {
                    val bitmaps = result.bitmaps
                    var frameIndex by remember(result) { mutableIntStateOf(0) }

                    LaunchedEffect(result) {
                        if (bitmaps.size <= 1) return@LaunchedEffect
                        while (true) {
                            delay(220L)
                            frameIndex = (frameIndex + 1) % bitmaps.size
                        }
                    }

                    DisposableEffect(result) {
                        onDispose {
                            bitmaps.forEach { bmp ->
                                runCatching { if (!bmp.isRecycled) bmp.recycle() }
                            }
                        }
                    }

                    Image(
                        bitmap = bitmaps[frameIndex.coerceIn(0, bitmaps.lastIndex)].asImageBitmap(),
                        contentDescription = stringResource(R.string.viewer_qr_frame),
                        modifier = Modifier
                            .size(320.dp)
                            .padding(8.dp),
                    )
                    LinearProgressIndicator(
                        progress = { (frameIndex + 1f) / bitmaps.size.toFloat() },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = "Frame ${frameIndex + 1} of ${bitmaps.size} — hold steady, the scanner picks up frames automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private sealed interface QrAnimResult {
    data object Loading : QrAnimResult
    data object Error : QrAnimResult
    data class Ready(
        val bitmaps: List<android.graphics.Bitmap>,
        val totalChunks: Int,
        val fileName: String,
    ) : QrAnimResult
}
