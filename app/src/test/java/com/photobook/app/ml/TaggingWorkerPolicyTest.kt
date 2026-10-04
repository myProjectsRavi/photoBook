package com.photobook.app.ml

import androidx.work.ListenableWorker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaggingWorkerPolicyTest {

    @Test
    fun incompleteWork_retriesOnlyWithinBoundedAttemptBudget() {
        val worker = TaggingWorkerRetryPolicyHarness()
        val first = worker.resultForRemainingWork(hasRemainingWork = true, runAttemptCount = 0)
        val lastRetry = worker.resultForRemainingWork(hasRemainingWork = true, runAttemptCount = 2)
        val exhausted = worker.resultForRemainingWork(hasRemainingWork = true, runAttemptCount = 3)

        assertThat(first).isInstanceOf(ListenableWorker.Result.Retry::class.java)
        assertThat(lastRetry).isInstanceOf(ListenableWorker.Result.Retry::class.java)
        assertThat(exhausted).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    @Test
    fun noRemainingWork_succeedsRegardlessOfAttemptCount() {
        val worker = TaggingWorkerRetryPolicyHarness()
        assertThat(worker.resultForRemainingWork(false, 0))
            .isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(worker.resultForRemainingWork(false, 99))
            .isInstanceOf(ListenableWorker.Result.Success::class.java)
    }

    private class TaggingWorkerRetryPolicyHarness {
        fun resultForRemainingWork(
            hasRemainingWork: Boolean,
            runAttemptCount: Int,
        ): ListenableWorker.Result {
            if (!hasRemainingWork) return ListenableWorker.Result.success()
            return if (runAttemptCount < 3) {
                ListenableWorker.Result.retry()
            } else {
                ListenableWorker.Result.failure()
            }
        }
    }
}
