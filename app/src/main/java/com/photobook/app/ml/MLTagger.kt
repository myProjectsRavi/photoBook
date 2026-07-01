package com.photobook.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import com.google.android.gms.common.api.OptionalModuleApi
import com.google.android.gms.common.moduleinstall.ModuleInstall
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
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.util.Constants
import com.photobook.app.util.LocalDiagnostics
import com.photobook.app.util.PerformanceProfiler
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
        val mlStatus: IntelligenceStatus,
        val ocrStatus: IntelligenceStatus,
    )

    data class ModelAvailability(
        val mlReady: Boolean,
        val ocrReady: Boolean,
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

    private val performanceProfiler: PerformanceProfiler by lazy {
        PerformanceProfiler.from(context)
    }

    suspend fun tagPhoto(uriString: String, isFrontCamera: Boolean): List<MLTag> {
        return analyzePhoto(uriString, isFrontCamera).tags
    }

    suspend fun analyzePhoto(uriString: String, isFrontCamera: Boolean): AnalysisResult {
        return withContext(Dispatchers.IO) {
            val bitmap = loadIntelligenceBitmap(uriString) ?: return@withContext AnalysisResult(
                tags = emptyList(),
                ocrText = "",
                mlStatus = IntelligenceStatus.FAILED_RETRYABLE,
                ocrStatus = IntelligenceStatus.FAILED_RETRYABLE,
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
        val maxDimension = performanceProfiler.intelligenceBitmapMaxDimensionPx

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val fromThumbnail = runCatching {
                context.contentResolver.loadThumbnail(uri, Size(maxDimension, maxDimension), null)
            }.getOrNull()
            if (fromThumbnail != null) return fromThumbnail
        }

        return decodeSampledBitmap(uri, maxDimension)
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

    suspend fun ensureModelsReady(needsMl: Boolean, needsOcr: Boolean): ModelAvailability {
        return withContext(Dispatchers.IO) {
            val mlReady = if (needsMl) {
                areModulesAvailable(labeler, faceDetector)
            } else {
                true
            }
            val ocrReady = if (needsOcr) {
                areModulesAvailable(textRecognizer)
            } else {
                true
            }

            if (needsMl && !mlReady) {
                requestDeferredInstall(labeler, faceDetector)
            }
            if (needsOcr && !ocrReady) {
                requestDeferredInstall(textRecognizer)
            }

            ModelAvailability(
                mlReady = !needsMl || mlReady,
                ocrReady = !needsOcr || ocrReady,
            )
        }
    }

    private suspend fun areModulesAvailable(vararg apis: OptionalModuleApi): Boolean {
        return runCatching {
            ModuleInstall.getClient(context)
                .areModulesAvailable(*apis)
                .await()
                .areModulesAvailable()
        }.getOrElse { error ->
            LocalDiagnostics.record(
                context = context,
                area = "ml-model-availability",
                message = "Model availability check failed; attempting analyzer directly",
                throwable = error,
            )
            true
        }
    }

    private suspend fun requestDeferredInstall(vararg apis: OptionalModuleApi) {
        runCatching {
            ModuleInstall.getClient(context)
                .deferredInstall(*apis)
                .await()
        }.onFailure { error ->
            LocalDiagnostics.record(
                context = context,
                area = "ml-model-install",
                message = "Deferred ML Kit model install request failed",
                throwable = error,
            )
        }
    }

    suspend fun analyzeBitmap(
        bitmap: Bitmap,
        isFrontCamera: Boolean,
        analyzeMl: Boolean = true,
        analyzeOcr: Boolean = true,
    ): AnalysisResult {
        return withContext(Dispatchers.Default) {
            analyzeBitmapInternal(bitmap, isFrontCamera, analyzeMl, analyzeOcr)
        }
    }

    private suspend fun analyzeBitmapInternal(
        bitmap: Bitmap,
        isFrontCamera: Boolean,
        analyzeMl: Boolean = true,
        analyzeOcr: Boolean = true,
    ): AnalysisResult = coroutineScope {
        val input = InputImage.fromBitmap(bitmap, 0)
        val labelsResult: Result<List<com.google.mlkit.vision.label.ImageLabel>>
        val facesResult: Result<List<com.google.mlkit.vision.face.Face>>
        val textResult: Result<String>

        if (performanceProfiler.shouldRunMlSequentially) {
            labelsResult = if (analyzeMl) runCatching { labeler.process(input).await() } else Result.success(emptyList())
            facesResult = if (analyzeMl) runCatching { faceDetector.process(input).await() } else Result.success(emptyList())
            textResult = if (analyzeOcr) runCatching { textRecognizer.process(input).await().text } else Result.success("")
        } else {
            val labelsDeferred = async {
                if (analyzeMl) runCatching { labeler.process(input).await() } else Result.success(emptyList())
            }
            val facesDeferred = async {
                if (analyzeMl) runCatching { faceDetector.process(input).await() } else Result.success(emptyList())
            }
            val textDeferred = async {
                if (analyzeOcr) runCatching { textRecognizer.process(input).await().text } else Result.success("")
            }

            labelsResult = labelsDeferred.await()
            facesResult = facesDeferred.await()
            textResult = textDeferred.await()
        }

        labelsResult.exceptionOrNull()?.let { error ->
            LocalDiagnostics.record(context, "ml-labeling", "Image labeling failed", error)
        }
        facesResult.exceptionOrNull()?.let { error ->
            LocalDiagnostics.record(context, "ml-face-detection", "Face detection failed", error)
        }
        textResult.exceptionOrNull()?.let { error ->
            LocalDiagnostics.record(context, "ml-ocr", "Text recognition failed", error)
        }

        val labels = labelsResult.getOrDefault(emptyList())
        val faces = facesResult.getOrDefault(emptyList())
        val text = textResult.getOrDefault("")
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

        val mlStatus = if (!analyzeMl) {
            IntelligenceStatus.PENDING
        } else if (labelsResult.isSuccess && facesResult.isSuccess) {
            IntelligenceStatus.PROCESSED
        } else {
            IntelligenceStatus.FAILED_RETRYABLE
        }

        val ocrStatus = if (!analyzeOcr) {
            IntelligenceStatus.PENDING
        } else if (textResult.isSuccess) {
            IntelligenceStatus.PROCESSED
        } else {
            IntelligenceStatus.FAILED_RETRYABLE
        }

        AnalysisResult(
            tags = tagMap.values.toList(),
            ocrText = normalizedOcrText,
            mlStatus = mlStatus,
            ocrStatus = ocrStatus,
        )
    }

    private fun loadThumbnail(uriString: String): Bitmap? {
        return loadIntelligenceBitmap(uriString)
    }
}
