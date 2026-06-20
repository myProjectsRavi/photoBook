package com.photobook.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.photobook.app.data.model.MLTag
import com.photobook.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MLTagger @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class AnalysisResult(
        val tags: List<MLTag>,
        val ocrText: String,
    )

    private val labeler: ImageLabeler by lazy {
        ImageLabeling.getClient(
            ImageLabelerOptions.Builder()
                .setConfidenceThreshold(0.5f)
                .build()
        )
    }

    private val faceDetector: FaceDetector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build()
        )
    }

    private val textRecognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun tagPhoto(uriString: String, isFrontCamera: Boolean): List<MLTag> {
        return analyzePhoto(uriString, isFrontCamera).tags
    }

    suspend fun analyzePhoto(uriString: String, isFrontCamera: Boolean): AnalysisResult {
        return withContext(Dispatchers.IO) {
            val bitmap = loadIntelligenceBitmap(uriString) ?: return@withContext AnalysisResult(
                tags = emptyList(),
                ocrText = "",
            )
            try {
                analyzeBitmapInternal(bitmap, isFrontCamera)
            } finally {
                bitmap.recycle()
            }
        }
    }

    fun loadIntelligenceBitmap(uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fromThumbnail = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(1024, 1024), null)
            }.getOrNull()
            if (fromThumbnail != null) return fromThumbnail
        }

        return decodeSampledBitmap(uri, 1024)
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null
        }

        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx || bounds.outHeight / sample > maxDimensionPx) {
            sample *= 2
        }

        val decodeOptions = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            }
        }.getOrNull()
    }

    suspend fun analyzeBitmap(bitmap: Bitmap, isFrontCamera: Boolean): AnalysisResult {
        return withContext(Dispatchers.Default) {
            analyzeBitmapInternal(bitmap, isFrontCamera)
        }
    }

    private suspend fun analyzeBitmapInternal(bitmap: Bitmap, isFrontCamera: Boolean): AnalysisResult = coroutineScope {
        val input = InputImage.fromBitmap(bitmap, 0)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
        val isLowRam = activityManager?.isLowRamDevice == true

        val labels: List<com.google.mlkit.vision.label.ImageLabel>
        val faces: List<com.google.mlkit.vision.face.Face>
        val text: String

        if (isLowRam) {
            labels = runCatching { labeler.process(input).await() }.getOrDefault(emptyList())
            faces = runCatching { faceDetector.process(input).await() }.getOrDefault(emptyList())
            text = runCatching { textRecognizer.process(input).await().text }.getOrDefault("")
        } else {
            val labelsDeferred = async { runCatching { labeler.process(input).await() }.getOrDefault(emptyList()) }
            val facesDeferred = async { runCatching { faceDetector.process(input).await() }.getOrDefault(emptyList()) }
            val textDeferred = async { runCatching { textRecognizer.process(input).await().text }.getOrDefault("") }

            labels = labelsDeferred.await()
            faces = facesDeferred.await()
            text = textDeferred.await()
        }

        val tagMap = linkedMapOf<String, MLTag>()
        labels.forEach { label ->
            val canonical = LabelMapping.map(label.text) ?: return@forEach
            val threshold = LabelMapping.threshold(canonical)
            if (label.confidence < threshold) return@forEach

            val existing = tagMap[canonical]
            if (existing == null || existing.confidence < label.confidence) {
                tagMap[canonical] = MLTag(canonical, label.confidence)
            }
        }

        if (faces.size == 1 && isFrontCamera) {
            tagMap["selfie"] = MLTag("selfie", 0.90f)
        }
        if (faces.size >= 2) {
            tagMap["people"] = MLTag("people", 0.90f)
        }

        val normalizedOcrText = text
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(Constants.OCR_MAX_TEXT_CHARS)

        AnalysisResult(
            tags = tagMap.values.toList(),
            ocrText = normalizedOcrText,
        )
    }

    private fun loadThumbnail(uriString: String): Bitmap? {
        return loadIntelligenceBitmap(uriString)
    }
}
