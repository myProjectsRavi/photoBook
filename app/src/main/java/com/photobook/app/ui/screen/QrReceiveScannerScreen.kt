package com.photobook.app.ui.screen

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.photobook.app.R
import com.photobook.app.feature.qrshare.QrAssemblyResult
import com.photobook.app.feature.qrshare.QrReceivedImageStore
import com.photobook.app.feature.qrshare.QrTransferAssembler
import java.util.concurrent.Executors
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.launch

@Composable
fun QrReceiveScannerScreen(
    imageStore: QrReceivedImageStore,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val assembler = remember { QrTransferAssembler() }

    var scanResult by remember { mutableStateOf<QrAssemblyResult.Completed?>(null) }
    var progressText by remember { mutableStateOf(context.getString(R.string.scan_qr_hint)) }
    var isSaving by remember { mutableStateOf(false) }

    val previewBitmap = remember(scanResult?.transferId) {
        scanResult?.let { result ->
            BitmapFactory.decodeByteArray(result.bytes, 0, result.bytes.size)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.scan_qr_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.viewer_close),
                        )
                    }
                }

                if (scanResult == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp),
                    ) {
                        QrCameraScannerView(
                            enabled = true,
                            onPayloadScanned = { payload ->
                                when (val assembled = assembler.consume(payload)) {
                                    null -> Unit
                                    is QrAssemblyResult.Progress -> {
                                        progressText = if (assembled.totalChunks == null) {
                                            context.getString(
                                                R.string.scan_qr_progress_waiting_meta,
                                                assembled.receivedChunks,
                                            )
                                        } else {
                                            context.getString(
                                                R.string.scan_qr_progress,
                                                assembled.receivedChunks,
                                                assembled.totalChunks,
                                            )
                                        }
                                    }

                                    is QrAssemblyResult.Completed -> {
                                        scanResult = assembled
                                        progressText = context.getString(R.string.scan_qr_received)
                                    }

                                    is QrAssemblyResult.Error -> {
                                        progressText = assembled.reason
                                        assembler.reset()
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)),
                        )

                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(8.dp),
                            shape = RoundedCornerShape(14.dp),
                            tonalElevation = 2.dp,
                        ) {
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.scan_qr_received_image),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(360.dp),
                            )
                        } else {
                            Text(text = stringResource(R.string.scan_qr_preview_unavailable))
                        }

                        Text(
                            text = scanResult!!.fileName,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        Button(
                            enabled = !isSaving,
                            onClick = {
                                val payload = scanResult ?: return@Button
                                scope.launch {
                                    isSaving = true
                                    val uri = imageStore.saveToDevice(
                                        bytes = payload.bytes,
                                        preferredFileName = payload.fileName,
                                        mimeType = payload.mimeType,
                                    )
                                    isSaving = false
                                    if (uri != null) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.scan_qr_saved_success),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    } else {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.scan_qr_saved_error),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text(text = stringResource(R.string.scan_qr_save_to_device))
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                assembler.reset()
                                scanResult = null
                                progressText = context.getString(R.string.scan_qr_hint)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(text = stringResource(R.string.scan_qr_scan_another))
                        }
                    }
                }
            }
        }
    }
}

@Composable
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun QrCameraScannerView(
    enabled: Boolean,
    onPayloadScanned: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { MultiFormatReader() }
    val latestEnabled by rememberUpdatedState(enabled)
    val latestOnPayloadScanned by rememberUpdatedState(onPayloadScanned)

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    DisposableEffect(lifecycleOwner, enabled) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)

        val bindRunnable = Runnable {
            val cameraProvider = runCatching { cameraProviderFuture.get() }.getOrNull() ?: return@Runnable
            cameraProvider.unbindAll()
            if (!latestEnabled) return@Runnable

            val preview = Preview.Builder().build().also { builtPreview ->
                builtPreview.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                if (!latestEnabled) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                val mediaImage = imageProxy.image
                if (mediaImage == null) {
                    imageProxy.close()
                    return@setAnalyzer
                }

                runCatching { decodeQrPayload(imageProxy, scanner) }
                    .getOrNull()
                    ?.let(latestOnPayloadScanned)
                imageProxy.close()
            }

            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis,
            )
        }

        cameraProviderFuture.addListener(bindRunnable, executor)
        onDispose {
            if (cameraProviderFuture.isDone) {
                runCatching { cameraProviderFuture.get().unbindAll() }
            }
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun decodeQrPayload(
    imageProxy: androidx.camera.core.ImageProxy,
    reader: MultiFormatReader,
): String? {
    val plane = imageProxy.planes.firstOrNull() ?: return null
    val width = imageProxy.width
    val height = imageProxy.height
    val rowStride = plane.rowStride
    val sourceBytes = plane.buffer.let { buffer ->
        val raw = ByteArray(buffer.remaining())
        buffer.get(raw)
        if (rowStride == width) {
            raw
        } else {
            ByteArray(width * height).also { packed ->
                for (row in 0 until height) {
                    val sourceOffset = row * rowStride
                    if (sourceOffset + width <= raw.size) {
                        raw.copyInto(packed, row * width, sourceOffset, sourceOffset + width)
                    }
                }
            }
        }
    }
    val source = PlanarYUVLuminanceSource(
        sourceBytes,
        width,
        height,
        0,
        0,
        width,
        height,
        false,
    )
    val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )
    return runCatching {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    }.getOrNull().also {
        reader.reset()
    }
}
