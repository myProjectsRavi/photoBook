package com.photobook.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.photobook.app.util.Constants
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Legacy worker retained only so upgrades can safely neutralize any previously scheduled
 * PhotoBook trash-purge work.
 *
 * Permanent media deletion must remain an explicit foreground, Android-confirmed user action.
 * Archive retention records due state separately and must never rely on this worker for deletion.
 */
@HiltWorker
class TrashPurgeWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    /**
     * Defense in depth for already-created WorkRequests from older installs. Even if one starts
     * before cancellation is observed, it performs no MediaStore query and no deletion.
     */
    override suspend fun doWork(): Result = Result.success()

    companion object {
        /**
         * Historical call site name is intentionally retained for upgrade compatibility. It no
         * longer schedules destructive maintenance; instead it cancels the old unique periodic
         * work so previously registered requests cannot survive an app upgrade.
         */
        fun enqueueDaily(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(Constants.TRASH_PURGE_WORK_NAME)
        }
    }
}
