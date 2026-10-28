package com.photobook.app.ml

import androidx.work.ListenableWorker
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TaggingWorkerPolicyTest {

    @Test
    fun incompleteWork_retriesOnlyWithinBoundedAttemptBudget() {
        assertThat(TaggingRetryPolicy.result(true, 0))
            .isInstanceOf(ListenableWorker.Result.Retry::class.java)
        assertThat(TaggingRetryPolicy.result(true, 2))
            .isInstanceOf(ListenableWorker.Result.Retry::class.java)

        val exhausted = TaggingRetryPolicy.result(true, TaggingRetryPolicy.MAX_RETRY_ATTEMPTS)
        assertThat(exhausted).isInstanceOf(ListenableWorker.Result.Failure::class.java)
    }

    @Test
    fun noRemainingWork_succeedsRegardlessOfAttemptCount() {
        assertThat(TaggingRetryPolicy.result(false, 0))
            .isInstanceOf(ListenableWorker.Result.Success::class.java)
        assertThat(TaggingRetryPolicy.result(false, 99))
            .isInstanceOf(ListenableWorker.Result.Success::class.java)
    }
}
