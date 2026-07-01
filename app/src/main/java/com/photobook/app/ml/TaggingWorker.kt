package com.photobook.app.ml

import android.content.Context
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.photobook.app.data.model.IntelligenceStatus
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.feature.duplicates.BlurScoreComputer
import com.photobook.app.feature.duplicates.PerceptualHashComputer
import com.photobook.app.util.Constants
import com.photobook.app.util.LocalDiagnostics
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay

@HiltWorker
class TaggingWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val photoIndex: PhotoIndex,
    private val indexPersistence: IndexPersistence,
    private val mlTagger: MLTagger,
    private val perceptualHashComputer: PerceptualHashComputer,
    private val blurScoreComputer: BlurScoreComputer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            runTaggingWork()
        }.getOrElse { error ->
            LocalDiagnostics.record(
                context = applicationContext,
                area = "tagging-worker",
                message = "Unhandled tagging worker failure",
                throwable = error,
            )
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) Result.retry() else Result.failure(
                workDataOf("reason" to "tagging_worker_failure"),
            )
        }
    }

    private suspend fun runTaggingWork(): Result {
        if (isBatteryTooLow()) {
            return Result.retry()
        }

        val requestedIds = inputData.getLongArray(KEY_TARGET_PHOTO_IDS)?.toSet().orEmpty()
        val photos = if (requestedIds.isEmpty()) {
            photoIndex.snapshot()
        } else {
            photoIndex.snapshot().filter { photo -> photo.id in requestedIds }
        }
        if (photos.isEmpty()) {
            return Result.success()
        }

        val pendingIndexUpdates = mutableListOf<PhotoIndex.PhotoIntelligenceUpdate>()

        photos.forEachIndexed { index, photo ->
            if (isStopped) return Result.retry()

            val needsMl = photo.mlStatus.shouldProcess
            val needsOcr = photo.ocrStatus.shouldProcess
            val needsPerceptualHash = photo.perceptualHash == null
            val needsBlurScore = photo.blurScore == null

            if (needsMl || needsOcr || needsPerceptualHash || needsBlurScore) {
                val bitmap = mlTagger.loadIntelligenceBitmap(photo.uriString)

                val modelAvailability = if (bitmap != null && (needsMl || needsOcr)) {
                    mlTagger.ensureModelsReady(needsMl = needsMl, needsOcr = needsOcr)
                } else null

                val analyzeMl = needsMl && modelAvailability?.mlReady == true
                val analyzeOcr = needsOcr && modelAvailability?.ocrReady == true
                val analysis = if (bitmap != null && (analyzeMl || analyzeOcr)) {
                    mlTagger.analyzeBitmap(
                        bitmap = bitmap,
                        isFrontCamera = photo.isFrontCamera,
                        analyzeMl = analyzeMl,
                        analyzeOcr = analyzeOcr,
                    )
                } else null

                val mlStatus = when {
                    !needsMl -> null
                    modelAvailability?.mlReady == false -> IntelligenceStatus.MODEL_PREPARING
                    bitmap == null -> IntelligenceStatus.FAILED_RETRYABLE
                    analyzeMl -> analysis?.mlStatus ?: IntelligenceStatus.FAILED_RETRYABLE
                    else -> null
                }
                val ocrStatus = when {
                    !needsOcr -> null
                    modelAvailability?.ocrReady == false -> IntelligenceStatus.MODEL_PREPARING
                    bitmap == null -> IntelligenceStatus.FAILED_RETRYABLE
                    analyzeOcr -> analysis?.ocrStatus ?: IntelligenceStatus.FAILED_RETRYABLE
                    else -> null
                }
                val perceptualHash = if (bitmap != null && needsPerceptualHash) {
                    perceptualHashComputer.computeFromBitmap(bitmap)
                } else {
                    null
                }
                val blurScore = if (bitmap != null && needsBlurScore) {
                    blurScoreComputer.computeFromBitmap(bitmap)
                } else {
                    null
                }
                
                bitmap?.recycle()

                pendingIndexUpdates += PhotoIndex.PhotoIntelligenceUpdate(
                    id = photo.id,
                    tags = if (needsMl) analysis?.tags else null,
                    isMlProcessed = mlStatus?.let { it == IntelligenceStatus.PROCESSED },
                    mlStatus = mlStatus,
                    ocrText = if (needsOcr) analysis?.ocrText else null,
                    isOcrProcessed = ocrStatus?.let { it == IntelligenceStatus.PROCESSED },
                    ocrStatus = ocrStatus,
                    perceptualHash = perceptualHash,
                    blurScore = blurScore,
                )
            }

            if (pendingIndexUpdates.size >= Constants.BATCH_SIZE) {
                val updatedRecords = photoIndex.updatePhotosIntelligence(pendingIndexUpdates)
                if (updatedRecords.isNotEmpty()) {
                    indexPersistence.upsertAll(updatedRecords)
                }
                pendingIndexUpdates.clear()

                if (isBatteryTooLow()) return Result.retry()
                delay(Constants.BATCH_DELAY_MS)
            }

            if (index % 500 == 0) {
                setProgress(androidx.work.workDataOf("processed" to index, "total" to photos.size))
            }
        }

        if (pendingIndexUpdates.isNotEmpty()) {
            val updatedRecords = photoIndex.updatePhotosIntelligence(pendingIndexUpdates)
            if (updatedRecords.isNotEmpty()) {
                indexPersistence.upsertAll(updatedRecords)
            }
            pendingIndexUpdates.clear()
        }
        return Result.success()
    }

    private fun isBatteryTooLow(): Boolean {
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return false
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1..14
    }

    companion object {
        private const val KEY_TARGET_PHOTO_IDS = "target_photo_ids"
        private const val PRIORITY_WORK_NAME_PREFIX = "photobook_ml_worker_priority"
        private const val MAX_RETRY_ATTEMPTS = 3

        fun enqueueLibraryMaintenance(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .build()

            val request = OneTimeWorkRequestBuilder<TaggingWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                Constants.ML_WORKER_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun enqueueFocusedPhoto(context: Context, photoId: Long) {
            if (photoId <= 0L) return

            // Expedited WorkRequests are NOT allowed to set requiresBatteryNotLow / requiresCharging
            // / requiresDeviceIdle / requiresStorageNotLow. Doing so throws IllegalArgumentException
            // from WorkRequest.Builder.build(), which previously crashed the app every time the
            // user tapped a photo. Keep this request constraint-light; the worker itself bails out
            // when the battery is critically low (see isBatteryTooLow()).
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()

            val input: Data = workDataOf(
                KEY_TARGET_PHOTO_IDS to longArrayOf(photoId),
            )

            val request = OneTimeWorkRequestBuilder<TaggingWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            runCatching {
                WorkManager.getInstance(context).enqueueUniqueWork(
                    "$PRIORITY_WORK_NAME_PREFIX-$photoId",
                    ExistingWorkPolicy.REPLACE,
                    request,
                )
            }
            // Silently swallow any enqueue failure: the viewer must never crash because of an
            // optional background tagging request.
        }
    }
}
