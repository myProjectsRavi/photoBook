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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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

    // Generate a single compact QR payload for this photo
    val qrResult by produceState<QrSingleResult>(
        initialValue = QrSingleResult.Loading,
        key1 = photo.id,
    ) {
        val genResult = encoder.generateSingleFrameQr(photo)
        value = when (genResult) {
            is QrShareGenerationResult.Success -> {
                val payload = genResult.packet.frames.first()
                val bitmap = withContext(Dispatchers.Default) {
                    QrBitmapEncoder.encode(payload, sizePx = 800)
                }
                QrSingleResult.Ready(bitmap)
            }
            is QrShareGenerationResult.TooLarge -> QrSingleResult.TooLarge
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                QrSingleResult.Error, QrSingleResult.TooLarge -> {
                    Text(
                        text = stringResource(R.string.viewer_qr_error),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                is QrSingleResult.Ready -> {
                    DisposableEffect(result.bitmap) {
                        onDispose {
                            runCatching {
                                if (!result.bitmap.isRecycled) result.bitmap.recycle()
                            }
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
                        text = "Ask the other person to open PhotoBook → QR Scanner to receive this photo instantly.",
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
    data object TooLarge : QrSingleResult
    data class Ready(val bitmap: android.graphics.Bitmap) : QrSingleResult
}
