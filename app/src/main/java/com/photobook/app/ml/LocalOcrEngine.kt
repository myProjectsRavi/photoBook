package com.photobook.app.ml

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Single bundled, network-independent OCR boundary for PhotoBook.
 *
 * The Latin recognizer is packaged with the app, so recognition never depends on a model download
 * or network access. Callers own bitmap lifecycle; this class never mutates or recycles inputs.
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
            Result.success(awaitText(InputImage.fromBitmap(bitmap, 0)))
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        } catch (error: LinkageError) {
            Result.failure(error)
        }
    }

    private suspend fun awaitText(image: InputImage): String = suspendCancellableCoroutine { continuation ->
        val task = try {
            recognizer.process(image)
        } catch (error: Exception) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            return@suspendCancellableCoroutine
        } catch (error: LinkageError) {
            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
            return@suspendCancellableCoroutine
        }

        task.addOnSuccessListener { result ->
            if (continuation.isActive) continuation.resume(result.text)
        }
        task.addOnFailureListener { error ->
            if (continuation.isActive) continuation.resumeWith(Result.failure(error))
        }
        task.addOnCanceledListener {
            if (continuation.isActive) continuation.cancel(CancellationException("OCR task cancelled"))
        }
    }
}
