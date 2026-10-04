package com.photobook.app.ml

import androidx.work.ListenableWorker
import androidx.work.workDataOf

internal object TaggingRetryPolicy {
    const val MAX_RETRY_ATTEMPTS = 3

    fun result(
        hasRemainingWork: Boolean,
        runAttemptCount: Int,
    ): ListenableWorker.Result {
        if (!hasRemainingWork) return ListenableWorker.Result.success()
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            ListenableWorker.Result.retry()
        } else {
            ListenableWorker.Result.failure(
                workDataOf("reason" to "intelligence_work_incomplete"),
            )
        }
    }
}
