package com.photobook.app.ml

import android.content.Context
import android.os.BatteryManager
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.photobook.app.data.index.IndexPersistence
import com.photobook.app.data.index.PhotoIndex
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

        val photos = photoIndex.snapshot()
        if (photos.isEmpty()) {
            return Result.success()
        }

        var processed = 0
        photos.forEachIndexed { index, photo ->
            if (isStopped) return Result.retry()

            val needsMl = !photo.isMlProcessed
            val needsOcr = !photo.isOcrProcessed
            if (needsMl || needsOcr) {
                val analysis = mlTagger.analyzePhoto(photo.uriString, photo.isFrontCamera)
                photoIndex.updatePhotoIntelligence(
                    id = photo.id,
                    tags = if (needsMl) analysis.tags else null,
                    isMlProcessed = if (needsMl) true else null,
                    ocrText = if (needsOcr) analysis.ocrText else null,
                    isOcrProcessed = if (needsOcr) true else null,
                )
                processed += 1
            }

            if (processed > 0 && processed % Constants.BATCH_SIZE == 0) {
                indexPersistence.save(photoIndex.snapshot())
                if (isBatteryTooLow()) return Result.retry()
                delay(Constants.BATCH_DELAY_MS)
            }

            if (index % 500 == 0) {
                setProgress(androidx.work.workDataOf("processed" to index, "total" to photos.size))
            }
        }

        indexPersistence.save(photoIndex.snapshot())
        return Result.success()
    }

    private fun isBatteryTooLow(): Boolean {
        val batteryManager = applicationContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            ?: return false
        val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return level in 1..14
    }

    companion object {
        fun enqueue(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
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
    }
}
