package com.photobook.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
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
    private val semanticImageLabeler: LocalSemanticImageLabeler,
    private val localOcrEngine: LocalOcrEngine,
) {
    data class AnalysisResult(
        val tags: List<MLTag>,
        val archiveFoodCandidate: Boolean,
        val ocrText: String,
        val mlStatus: IntelligenceStatus,
        val ocrStatus: IntelligenceStatus,
    )

    data class OcrResult(
        val text: String,
        val status: IntelligenceStatus,
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
                archiveFoodCandidate = false,
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

    /**
     * Search indexing uses a larger, independently bounded bitmap than semantic tagging. This keeps
     * small English text legible without increasing the memory/CPU footprint of every ML/hash pass.
     */
    suspend fun recognizePhotoText(uriString: String): OcrResult = withContext(Dispatchers.IO) {
        val bitmap = loadOcrBitmap(uriString) ?: return@withContext OcrResult(
            text = "",
            status = IntelligenceStatus.FAILED_RETRYABLE,
        )
        try {
            val result = localOcrEngine.recognize(bitmap)
            result.exceptionOrNull()?.let { error ->
                LocalDiagnostics.record(context, "ml-ocr", "Bundled local OCR failed", error)
            }
            OcrResult(
                text = normalizeOcrText(result.getOrDefault("")),
                status = if (result.isSuccess) {
                    IntelligenceStatus.PROCESSED
                } else {
                    // A single image/runtime failure must never permanently poison search indexing.
                    IntelligenceStatus.FAILED_RETRYABLE
                },
            )
        } finally {
            bitmap.recycleSafely()
        }
    }

    fun loadIntelligenceBitmap(uriString: String): Bitmap? {
        val maxDimension = performanceProfiler.intelligenceBitmapMaxDimensionPx
        return loadBitmap(Uri.parse(uriString), maxDimension)
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
        ocrTextForArchive: String? = null,
    ): AnalysisResult = withContext(Dispatchers.Default) {
        analyzeBitmapInternal(
            bitmap = bitmap,
            isFrontCamera = isFrontCamera,
            analyzeMl = analyzeMl,
            analyzeOcr = analyzeOcr,
            ocrTextForArchive = ocrTextForArchive,
        )
    }

    private suspend fun analyzeBitmapInternal(
        bitmap: Bitmap,
        isFrontCamera: Boolean,
        analyzeMl: Boolean = true,
        analyzeOcr: Boolean = true,
        ocrTextForArchive: String? = null,
    ): AnalysisResult {
        val faces = if (analyzeMl) CompactLocalIntelligence.detectFaces(bitmap) else emptyList()
        val tagMap = linkedMapOf<String, MLTag>()
        val archiveSignals = mutableListOf<MLTag>()
        if (analyzeMl) {
            CompactLocalIntelligence.labels(bitmap).forEach { label ->
                val canonical = LabelMapping.map(label.label) ?: return@forEach
                if (label.confidence >= LabelMapping.threshold(canonical)) {
                    tagMap[canonical] = MLTag(canonical, label.confidence)
                }
            }

            semanticLabels(bitmap).forEach { label ->
                val canonical = label.label
                if (label.confidence >= LabelMapping.taggingThreshold(canonical)) {
                    archiveSignals += MLTag(canonical, label.confidence)
                    if (label.isPreparedFood) {
                        archiveSignals += MLTag("prepared_food", label.confidence)
                        if (label.confidence >= ArchiveFoodSignals.MIN_PREPARED_FOOD_CONFIDENCE) {
                            tagMap["prepared_food"] = MLTag("prepared_food", label.confidence)
                        }
                    }
                }
            }

            if (faces.size == 1 && isFrontCamera) {
                tagMap["selfie"] = MLTag("selfie", 0.90f)
                archiveSignals += MLTag("selfie", 0.90f)
            }
            if (faces.size >= 2) {
                tagMap["people"] = MLTag("people", 0.90f)
                archiveSignals += MLTag("people", 0.90f)
            }
        }

        val ocrResult = if (analyzeOcr) localOcrEngine.recognize(bitmap) else Result.success("")
        ocrResult.exceptionOrNull()?.let { error ->
            LocalDiagnostics.record(context, "ml-ocr", "Bundled local OCR failed", error)
        }
        val normalizedOcrText = normalizeOcrText(ocrResult.getOrDefault(""))
        val archiveOcrText = ocrTextForArchive ?: normalizedOcrText

        return AnalysisResult(
            tags = tagMap.values.toList(),
            archiveFoodCandidate = analyzeMl && ArchiveFoodSignals.isEligible(
                tags = archiveSignals,
                ocrText = archiveOcrText,
            ),
            ocrText = normalizedOcrText,
            mlStatus = if (analyzeMl) IntelligenceStatus.PROCESSED else IntelligenceStatus.PENDING,
            ocrStatus = when {
                !analyzeOcr -> IntelligenceStatus.PENDING
                ocrResult.isSuccess -> IntelligenceStatus.PROCESSED
                else -> IntelligenceStatus.FAILED_RETRYABLE
            },
        )
    }

    private suspend fun semanticLabels(bitmap: Bitmap): List<LocalSemanticLabel> =
        semanticImageLabeler.labels(bitmap)

    private fun loadOcrBitmap(uriString: String): Bitmap? {
        val maxDimension = if (performanceProfiler.isLite) {
            LITE_OCR_BITMAP_MAX_DIMENSION_PX
        } else {
            STANDARD_OCR_BITMAP_MAX_DIMENSION_PX
        }
        return loadBitmap(Uri.parse(uriString), maxDimension)
    }

    private fun loadBitmap(uri: Uri, maxDimensionPx: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.loadThumbnail(uri, Size(maxDimensionPx, maxDimensionPx), null)
            }.getOrNull()?.let { return it }
        }
        return decodeSampledBitmap(uri, maxDimensionPx)
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
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
        }.getOrNull() ?: return null
        val oriented = applyExifOrientation(decoded, uri)
        if (oriented !== decoded) decoded.recycleSafely()
        return oriented
    }

    private fun applyExifOrientation(source: Bitmap, uri: Uri): Bitmap {
        val orientation = readExifOrientation(uri)
        if (
            orientation == ExifInterface.ORIENTATION_NORMAL ||
            orientation == ExifInterface.ORIENTATION_UNDEFINED
        ) {
            return source
        }

        val matrix = Matrix().apply {
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> preScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> preScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    preScale(-1f, 1f)
                    postRotate(270f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    preScale(-1f, 1f)
                    postRotate(90f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            }
        }

        return runCatching {
            Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }.getOrDefault(source)
    }

    private fun readExifOrientation(uri: Uri): Int {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED,
                )
            } ?: ExifInterface.ORIENTATION_UNDEFINED
        }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
    }

    private fun normalizeOcrText(rawText: String): String {
        return rawText
            .lowercase()
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(Constants.OCR_MAX_TEXT_CHARS)
    }

    private fun Bitmap.recycleSafely() {
        runCatching { if (!isRecycled) recycle() }
    }

    private companion object {
        private const val LITE_OCR_BITMAP_MAX_DIMENSION_PX = 1_280
        private const val STANDARD_OCR_BITMAP_MAX_DIMENSION_PX = 2_048
    }
}
