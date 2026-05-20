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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QrShareSheet(
    photo: PhotoRecord,
    encoder: QrShareEncoder,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Instant single-frame QR — generate one tiny QR (≤2KB JPEG) that scans in <1s.
    val qrResult by produceState<QrSingleResult>(
        initialValue = QrSingleResult.Loading,
        key1 = photo.id,
    ) {
        value = when (val gen = encoder.generateSingleFrameQr(photo)) {
            is QrShareGenerationResult.Success -> {
                val payload = gen.packet.frames.firstOrNull()
                if (payload.isNullOrEmpty()) {
                    QrSingleResult.Error
                } else {
                    val bitmap = withContext(Dispatchers.Default) {
                        QrBitmapEncoder.encode(payload, sizePx = 600)
                    }
                    QrSingleResult.Ready(bitmap = bitmap, fileName = gen.packet.fileName)
                }
            }
            is QrShareGenerationResult.TooLarge -> QrSingleResult.Error
            is QrShareGenerationResult.Error -> QrSingleResult.Error
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
                QrSingleResult.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
                QrSingleResult.Error -> {
                    Text(
                        text = stringResource(R.string.viewer_qr_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is QrSingleResult.Ready -> {
                    DisposableEffect(result) {
                        onDispose {
                            runCatching { if (!result.bitmap.isRecycled) result.bitmap.recycle() }
                        }
                    }
                    Image(
                        bitmap = result.bitmap.asImageBitmap(),
                        contentDescription = stringResource(R.string.viewer_qr_frame),
                        modifier = Modifier
                            .size(320.dp)
                            .padding(8.dp),
                    )
                    Text(
                        text = "Scan to receive ${result.fileName}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private sealed interface QrSingleResult {
    data object Loading : QrSingleResult
    data object Error : QrSingleResult
    data class Ready(
        val bitmap: android.graphics.Bitmap,
        val fileName: String,
    ) : QrSingleResult
}
