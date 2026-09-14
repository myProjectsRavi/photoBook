package com.photobook.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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

    suspend fun recognize(bitmap: Bitmap): Result<String> {
        if (bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0) {
            return Result.failure(IllegalArgumentException("OCR bitmap is invalid"))
        }

        return try {
            val text = awaitText(InputImage.fromBitmap(bitmap, 0))
            currentCoroutineContext().ensureActive()
            Result.success(text)
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

    /**
     * ML Kit's TextRecognizer task does not expose a cancellation token for process(InputImage).
     * Suspending non-cancellably here is deliberate: the caller's bitmap remains owned until one
     * terminal task callback fires. recognize() then re-checks coroutine cancellation before the
     * caller can leave its try/finally and recycle the bitmap.
     */
    private suspend fun awaitText(image: InputImage): String = suspendCoroutine { continuation ->
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
            continuation.resume(result.text)
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
}
