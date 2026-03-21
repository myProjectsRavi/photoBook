package com.photobook.app.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Size
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.photobook.app.data.model.MLTag
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MLTagger @Inject constructor(
    @ApplicationContext private val context: Context,
) {

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

    suspend fun tagPhoto(uriString: String, isFrontCamera: Boolean): List<MLTag> {
        return withContext(Dispatchers.IO) {
            val bitmap = loadThumbnail(uriString) ?: return@withContext emptyList()
            val input = InputImage.fromBitmap(bitmap, 0)

            val labels = runCatching { labeler.process(input).await() }.getOrDefault(emptyList())
            val faces = runCatching { faceDetector.process(input).await() }.getOrDefault(emptyList())

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

            tagMap.values.toList()
        }
    }

    private fun loadThumbnail(uriString: String): Bitmap? {
        val uri = Uri.parse(uriString)

        val fromThumbnail = runCatching {
            context.contentResolver.loadThumbnail(uri, Size(224, 224), null)
        }.getOrNull()
        if (fromThumbnail != null) return fromThumbnail

        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
        }.getOrNull()
    }
}
