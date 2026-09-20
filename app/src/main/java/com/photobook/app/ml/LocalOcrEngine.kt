package com.photobook.app.ml

import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.photobook.app.feature.phototextsearch.PhotoTextCompleteness
import com.photobook.app.feature.phototextsearch.PhotoTextLayout
import com.photobook.app.feature.phototextsearch.TextElement
import com.photobook.app.feature.phototextsearch.UnitPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Single bundled, network-independent OCR boundary for PhotoBook.
 *
 * The Latin recognizer is packaged with the app, so recognition never depends on a model download
 * or network access. Callers own bitmap lifecycle; this class never mutates or recycles inputs.
 * Cancellation is observed only after the ML Kit task reaches a terminal state so callers cannot
 * recycle a bitmap while the recognizer may still be reading its InputImage.
 */
@Singleton
class LocalOcrEngine @Inject constructor() {

    private val recognizer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    // Native OCR owns caller bitmaps until its terminal callback. Serialize all OCR entry points so
    // indexing, Copy Text and viewer highlighting cannot overlap native bitmap ownership.
    private val nativeAdmission = Semaphore(permits = 1)

    suspend fun recognize(bitmap: Bitmap): Result<String> {
        if (!bitmap.isUsableOcrInput()) {
            return Result.failure(IllegalArgumentException("OCR bitmap is invalid"))
        }

        return nativeAdmission.withPermit {
            try {
                val result = awaitTextResult(InputImage.fromBitmap(bitmap, 0))
                currentCoroutineContext().ensureActive()
                Result.success(result.text)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                Result.failure(error)
            } catch (error: LinkageError) {
                currentCoroutineContext().ensureActive()
                Result.failure(error)
            }
        }
    }

    /**
     * Geometry-preserving OCR used only by in-photo text search.
     *
     * The returned model is immutable application data in upright normalized coordinates. No ML Kit
     * object, Bitmap, Context or Compose state escapes this boundary. Existing recognize() callers
     * keep their text-only path and do not pay geometry allocation costs.
     */
    suspend fun recognizeLayout(bitmap: Bitmap): Result<PhotoTextLayout> {
        if (!bitmap.isUsableOcrInput()) {
            return Result.failure(IllegalArgumentException("OCR bitmap is invalid"))
        }

        return nativeAdmission.withPermit {
            try {
                val result = awaitTextResult(InputImage.fromBitmap(bitmap, 0))
                currentCoroutineContext().ensureActive()
                Result.success(copyLayout(result, bitmap.width, bitmap.height))
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                Result.failure(error)
            } catch (error: LinkageError) {
                currentCoroutineContext().ensureActive()
                Result.failure(error)
            }
        }
    }

    /**
     * ML Kit's TextRecognizer task does not expose a cancellation token for process(InputImage).
     * Suspending non-cancellably here is deliberate: the caller's bitmap remains owned until one
     * terminal task callback fires. Public methods then re-check coroutine cancellation before the
     * caller can leave its try/finally and recycle the bitmap.
     */
    private suspend fun awaitTextResult(image: InputImage): Text = suspendCoroutine { continuation ->
        val task = try {
            recognizer.process(image)
        } catch (error: Exception) {
            continuation.resumeWith(Result.failure(error))
            return@suspendCoroutine
        } catch (error: LinkageError) {
            continuation.resumeWith(Result.failure(error))
            return@suspendCoroutine
        }

        task.addOnSuccessListener { result ->
            continuation.resume(result)
        }
        task.addOnFailureListener { error ->
            continuation.resumeWith(Result.failure(error))
        }
        task.addOnCanceledListener {
            continuation.resumeWith(
                Result.failure(CancellationException("OCR task cancelled")),
            )
        }
    }

    private fun copyLayout(
        recognized: Text,
        uprightWidth: Int,
        uprightHeight: Int,
    ): PhotoTextLayout {
        val elements = ArrayList<TextElement>()
        var characterCount = 0
        var completeness = PhotoTextCompleteness.COMPLETE
        var nextElementId = 0

        outer@ for ((blockIndex, block) in recognized.textBlocks.withIndex()) {
            for ((lineIndex, line) in block.lines.withIndex()) {
                for (element in line.elements) {
                    if (
                        elements.size >= MAX_LAYOUT_ELEMENTS ||
                        characterCount + element.text.length > MAX_LAYOUT_CHARACTERS
                    ) {
                        completeness = PhotoTextCompleteness.PARTIAL
                        break@outer
                    }

                    val corners = normalizedCorners(
                        cornerPoints = element.cornerPoints,
                        boundingBox = element.boundingBox,
                        width = uprightWidth,
                        height = uprightHeight,
                    )
                    if (corners.size != EXPECTED_CORNER_COUNT) {
                        completeness = PhotoTextCompleteness.PARTIAL
                        continue
                    }

                    elements += TextElement(
                        id = nextElementId++,
                        blockId = blockIndex,
                        lineId = lineIndex,
                        text = element.text,
                        corners = corners,
                    )
                    characterCount += element.text.length
                }
            }
        }

        return PhotoTextLayout(
            uprightWidth = uprightWidth,
            uprightHeight = uprightHeight,
            elements = elements,
            completeness = completeness,
        )
    }

    private fun normalizedCorners(
        cornerPoints: Array<Point>?,
        boundingBox: Rect?,
        width: Int,
        height: Int,
    ): List<UnitPoint> {
        if (width <= 0 || height <= 0) return emptyList()
        val points = when {
            cornerPoints != null && cornerPoints.size >= EXPECTED_CORNER_COUNT -> {
                cornerPoints.take(EXPECTED_CORNER_COUNT)
            }
            boundingBox != null && !boundingBox.isEmpty -> {
                listOf(
                    Point(boundingBox.left, boundingBox.top),
                    Point(boundingBox.right, boundingBox.top),
                    Point(boundingBox.right, boundingBox.bottom),
                    Point(boundingBox.left, boundingBox.bottom),
                )
            }
            else -> return emptyList()
        }

        return points.map { point ->
            UnitPoint(
                x = point.x.toFloat().div(width.toFloat()).coerceIn(0f, 1f),
                y = point.y.toFloat().div(height.toFloat()).coerceIn(0f, 1f),
            )
        }
    }

    private fun Bitmap.isUsableOcrInput(): Boolean {
        return !isRecycled && width > 0 && height > 0 && config != Bitmap.Config.HARDWARE
    }

    private companion object {
        private const val EXPECTED_CORNER_COUNT = 4
        private const val MAX_LAYOUT_ELEMENTS = 10_000
        private const val MAX_LAYOUT_CHARACTERS = 100_000
    }
}
