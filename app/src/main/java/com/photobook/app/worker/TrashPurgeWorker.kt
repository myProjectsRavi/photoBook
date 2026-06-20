package com.photobook.app.worker

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.photobook.app.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@HiltWorker
class TrashPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Result.success()

        return withContext(Dispatchers.IO) {
            val resolver = applicationContext.contentResolver
            val nowEpochSeconds = (System.currentTimeMillis() / 1000L).toString()
            val targetUris = mutableListOf<Uri>()

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.MediaColumns.DATE_EXPIRES,
            )
            val selection = "${MediaStore.MediaColumns.IS_TRASHED}=1 AND " +
                "${MediaStore.MediaColumns.DATE_EXPIRES} IS NOT NULL AND " +
                "${MediaStore.MediaColumns.DATE_EXPIRES} <= ?"
            val args = arrayOf(nowEpochSeconds)

            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                args,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    targetUris += Uri.withAppendedPath(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id.toString(),
                    )
                }
            }

            var failures = 0
            targetUris.forEach { uri ->
                val deleted = runCatching {
                    resolver.delete(uri, null, null)
                }.getOrDefault(0)
                if (deleted <= 0) failures += 1
            }

            if (failures > targetUris.size / 2 && runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.success()
            }
        }
    }

    companion object {
        fun enqueueDaily(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .build()

            val request = PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                Constants.TRASH_PURGE_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
