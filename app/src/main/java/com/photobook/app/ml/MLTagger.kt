package com.photobook.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Size
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.model.MLTag
import com.photobook.app.util.Constants
import com.photobook.app.util.LocalDiagnostics
import com.photobook.app.util.PerformanceProfiler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MLTagger @Inject constructor(
    @ApplicationContext private val context: Context,
    private val onDeviceIntelligence: BundledOnDeviceIntelligence,
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

    private val performanceProfiler: PerformanceProfiler by lazy { PerformanceProfiler.from(context) }

    suspend fun tagPhoto(uriString: String, isFrontCamera: Boolean): List<MLTag> =
        analyzePhoto(uriString, isFrontCamera).tags

    suspend fun analyzePhoto(uriString: String, isFrontCamera: Boolean): AnalysisResult =
        withContext(Dispatchers.IO) {
            val bitmap = loadIntelligenceBitmap(uriString) ?: return@withContext AnalysisResult(
                tags = emptyList(),
                ocrText = "",
                mlStatus = IntelligenceStatus.FAILED_RETRYABLE,
                ocrStatus = IntelligenceStatus.FAILED_RETRYABLE,
            )
            try {
                analyzeBitmapInternal(bitmap, isFrontCamera)
            } finally {
                bitmap.recycleSafely()
            }
        }

    fun loadIntelligenceBitmap(uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)
        val maxDimension = performanceProfiler.intelligenceBitmapMaxDimensionPx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(maxDimension, maxDimension), null)
            }.getOrNull()?.let { return it }
        }
        return decodeSampledBitmap(uri, maxDimension)
    }

    suspend fun ensureModelsReady(needsMl: Boolean, needsOcr: Boolean): ModelAvailability {
        val availability = onDeviceIntelligence.ensureReady(needsMl, needsOcr)
        return ModelAvailability(availability.mlReady, availability.ocrReady)
    }

    suspend fun analyzeBitmap(
        bitmap: Bitmap,
        isFrontCamera: Boolean,
        analyzeMl: Boolean = true,
        analyzeOcr: Boolean = true,
    ): AnalysisResult = withContext(Dispatchers.Default) {
        analyzeBitmapInternal(bitmap, isFrontCamera, analyzeMl, analyzeOcr)
    }

    private fun analyzeBitmapInternal(
        bitmap: Bitmap,
        isFrontCamera: Boolean,
        analyzeMl: Boolean = true,
        analyzeOcr: Boolean = true,
    ): AnalysisResult {
        val faces = if (analyzeMl) CompactLocalIntelligence.detectFaces(bitmap) else emptyList()
        val tagMap = linkedMapOf<String, MLTag>()
        if (analyzeMl) {
            CompactLocalIntelligence.labels(bitmap).forEach { label ->
                val canonical = LabelMapping.map(label.label) ?: return@forEach
                if (label.confidence >= LabelMapping.threshold(canonical)) {
                    tagMap[canonical] = MLTag(canonical, label.confidence)
                }
            }
            if (faces.size == 1 && isFrontCamera) tagMap["selfie"] = MLTag("selfie", 0.90f)
            if (faces.size >= 2) tagMap["people"] = MLTag("people", 0.90f)
        }

        val ocrResult = if (analyzeOcr) CompactLocalIntelligence.ocr(bitmap) else Result.success("")
        ocrResult.exceptionOrNull()?.let { error ->
            LocalDiagnostics.record(context, "ml-ocr", "Compact local OCR is unavailable", error)
        }
        val normalizedOcrText = ocrResult.getOrDefault("")
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(Constants.OCR_MAX_TEXT_CHARS)

        return AnalysisResult(
            tags = tagMap.values.toList(),
            ocrText = normalizedOcrText,
            mlStatus = if (analyzeMl) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
            ocrStatus = when {
                !analyzeOcr -> IntelligenceStatus.PENDING
                ocrResult.isSuccess -> IntelligenceStatus.PROCESSED
                else -> IntelligenceStatus.FAILED_PERMANENT
            },
        )
    }

    private fun decodeSampledBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimensionPx || bounds.outHeight / sample > maxDimensionPx) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample.coerceAtLeast(1)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull()
    }

    private fun Bitmap.recycleSafely() {
        runCatching { if (!isRecycled) recycle() }
    }
}
