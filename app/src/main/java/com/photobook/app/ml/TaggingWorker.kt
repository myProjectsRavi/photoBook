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
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.index.PhotoIndex
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.util.Constants
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
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
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

        var processed = 0
        val pendingUpdates = mutableListOf<PhotoRecord>()
        photos.forEachIndexed { index, photo ->
            if (isStopped) return Result.retry()

            val needsMl = !photo.isMlProcessed
            val needsOcr = !photo.isOcrProcessed
            if (needsMl || needsOcr) {
                val analysis = mlTagger.analyzePhoto(photo.uriString, photo.isFrontCamera)
                val updated = photoIndex.updatePhotoIntelligence(
                    id = photo.id,
                    tags = if (needsMl) analysis.tags else null,
                    isMlProcessed = if (needsMl) true else null,
                    ocrText = if (needsOcr) analysis.ocrText else null,
                    isOcrProcessed = if (needsOcr) true else null,
                )
                if (updated != null) {
                    pendingUpdates += updated
                }
                processed += 1
            }

            if (processed > 0 && processed % Constants.BATCH_SIZE == 0) {
                if (pendingUpdates.isNotEmpty()) {
                    indexPersistence.upsertAll(pendingUpdates.toList())
                    pendingUpdates.clear()
                }
                if (isBatteryTooLow()) return Result.retry()
                delay(Constants.BATCH_DELAY_MS)
            }

            if (index % 500 == 0) {
                setProgress(androidx.work.workDataOf("processed" to index, "total" to photos.size))
            }
        }

        if (pendingUpdates.isNotEmpty()) {
            indexPersistence.upsertAll(pendingUpdates.toList())
            pendingUpdates.clear()
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

        fun enqueueLibraryMaintenance(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
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

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val input: Data = workDataOf(
                KEY_TARGET_PHOTO_IDS to longArrayOf(photoId),
            )

            val request = OneTimeWorkRequestBuilder<TaggingWorker>()
                .setInputData(input)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "$PRIORITY_WORK_NAME_PREFIX-$photoId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
