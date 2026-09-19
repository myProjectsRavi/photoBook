package com.photobook.app.data.index

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes the narrow durable-commit + in-memory-publication boundary.
 *
 * Expensive media decoding, OCR, ML and scan work must stay outside this lock. Writers enter only when
 * they are ready to commit Room/FTS state and publish the matching PhotoIndex revision.
 */
@Singleton
class IndexCommitCoordinator @Inject constructor() {
    private val commitMutex = Mutex()

    suspend fun <T> withCommit(block: suspend () -> T): T {
        return commitMutex.withLock { block() }
    }
}
