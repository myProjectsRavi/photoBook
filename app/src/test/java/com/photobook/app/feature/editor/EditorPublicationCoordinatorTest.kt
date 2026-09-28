package com.photobook.app.feature.editor

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import java.util.concurrent.Executors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Test

class EditorPublicationCoordinatorTest {

    @Test
    fun success_runsProviderWorkOffCallerThread_andClearsJournal(): Unit = runBlocking {
        val backend = FakeBackend()
        val journal = FakeJournal()
        val dispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "editor-publication-io")
        }.asCoroutineDispatcher()

        try {
            val callerThread = Thread.currentThread().name
            val result = coordinator(backend, journal, dispatcher).publish(request())

            assertThat(result).isEqualTo(EditorPublicationResult.Success(backend.destination))
            assertThat(backend.providerThreads).isNotEmpty()
            assertThat(backend.providerThreads).containsExactly("editor-publication-io")
            assertThat(backend.providerThreads).doesNotContain(callerThread)
            assertThat(journal.entries).isEmpty()
            assertThat(backend.deleted).isEmpty()
            assertThat(backend.publishCalls).isEqualTo(1)
        } finally {
            dispatcher.close()
        }
    }

    @Test
    fun failureAfterInsertBeforeDestinationJournal_deletesOnlyInsertedRow(): Unit = runBlocking {
        val backend = FakeBackend()
        val journal = FakeJournal(failPutNumber = 2)

        val result = coordinator(backend, journal).publish(request())

        assertThat(result).isEqualTo(EditorPublicationResult.Failed)
        assertThat(backend.deleted).containsExactly(backend.destination)
        assertThat(backend.publishCalls).isEqualTo(0)
    }

    @Test
    fun openFailure_cleansPendingOutput(): Unit = runBlocking {
        assertFailureStageCleans(FailureStage.OPEN)
    }

    @Test
    fun halfCopyFailure_cleansPendingOutput(): Unit = runBlocking {
        assertFailureStageCleans(FailureStage.HALF_COPY)
    }

    @Test
    fun closeFailure_cleansPendingOutput(): Unit = runBlocking {
        assertFailureStageCleans(FailureStage.CLOSE)
    }

    @Test
    fun publicationFailure_cleansPendingOutput(): Unit = runBlocking {
        assertFailureStageCleans(FailureStage.PUBLISH)
    }

    @Test
    fun emptyOutput_failsAndCleansPendingOutput(): Unit = runBlocking {
        val backend = FakeBackend(bytesCopied = 0)

        val result = coordinator(backend, FakeJournal()).publish(request())

        assertThat(result).isEqualTo(EditorPublicationResult.Failed)
        assertThat(backend.deleted).containsExactly(backend.destination)
    }

    @Test
    fun revokedAccess_returnsAccessDeniedWithoutPublishing(): Unit = runBlocking {
        val backend = FakeBackend(failureStage = FailureStage.ACCESS)

        val result = coordinator(backend, FakeJournal()).publish(request())

        assertThat(result).isEqualTo(EditorPublicationResult.AccessDenied)
        assertThat(backend.publishCalls).isEqualTo(0)
    }

    @Test
    fun unsupportedSafePublication_returnsAccessDeniedWithoutProviderMutation(): Unit = runBlocking {
        val backend = FakeBackend(supportsSafePublication = false)
        val journal = FakeJournal()

        val result = coordinator(backend, journal).publish(request())

        assertThat(result).isEqualTo(EditorPublicationResult.AccessDenied)
        assertThat(backend.insertCalls).isEqualTo(0)
        assertThat(journal.entries).isEmpty()
    }

    @Test
    fun fullStorage_returnsSpecificResultAndCleansPendingOutput(): Unit = runBlocking {
        val backend = FakeBackend(failureStage = FailureStage.NO_SPACE)

        val result = coordinator(backend, FakeJournal()).publish(request())

        assertThat(result).isEqualTo(EditorPublicationResult.InsufficientStorage)
        assertThat(backend.deleted).containsExactly(backend.destination)
    }

    @Test
    fun coroutineCancellation_isRethrownAndCleansPendingOutput(): Unit = runBlocking {
        val backend = FakeBackend(failureStage = FailureStage.CANCEL)
        val journal = FakeJournal()

        var cancelled = false
        try {
            coordinator(backend, journal).publish(request())
        } catch (_: CancellationException) {
            cancelled = true
        }

        assertThat(cancelled).isTrue()
        assertThat(backend.deleted).containsExactly(backend.destination)
        assertThat(journal.entries).isEmpty()
    }

    @Test
    fun recovery_deletesPendingButPreservesAlreadyPublishedRows(): Unit = runBlocking {
        val backend = FakeBackend().apply {
            states["content://media/pending"] = EditorPendingState.PENDING
            states["content://media/published"] = EditorPendingState.PUBLISHED
        }
        val journal = FakeJournal().apply {
            put(
                PendingEditorPublication(
                    operationId = "operation_pending",
                    outputName = "pending.jpg",
                    destinationUriString = "content://media/pending",
                    sourcePhotoId = 1,
                    sourceRevision = "rev-1",
                    createdAtMs = 1,
                ),
            )
            put(
                PendingEditorPublication(
                    operationId = "operation_published",
                    outputName = "published.jpg",
                    destinationUriString = "content://media/published",
                    sourcePhotoId = 2,
                    sourceRevision = "rev-2",
                    createdAtMs = 2,
                ),
            )
        }

        val report = coordinator(backend, journal).recoverInterruptedOutputs()

        assertThat(report.recoveredPending).isEqualTo(1)
        assertThat(report.preservedPublished).isEqualTo(1)
        assertThat(report.unresolved).isEqualTo(0)
        assertThat(backend.deleted).containsExactly("content://media/pending")
        assertThat(journal.entries).isEmpty()
    }

    @Test
    fun recovery_withoutRecordedUri_usesUniqueOutputNameOnly(): Unit = runBlocking {
        val backend = FakeBackend().apply {
            pendingByName["PhotoBook_Edit_unique.jpg"] = listOf("content://media/recovered")
            states["content://media/recovered"] = EditorPendingState.PENDING
        }
        val journal = FakeJournal().apply {
            put(
                PendingEditorPublication(
                    operationId = "operation_unique",
                    outputName = "PhotoBook_Edit_unique.jpg",
                    destinationUriString = null,
                    sourcePhotoId = 9,
                    sourceRevision = "rev-9",
                    createdAtMs = 3,
                ),
            )
        }

        val report = coordinator(backend, journal).recoverInterruptedOutputs()

        assertThat(report.recoveredPending).isEqualTo(1)
        assertThat(backend.findByNameCalls).containsExactly("PhotoBook_Edit_unique.jpg")
        assertThat(backend.deleted).containsExactly("content://media/recovered")
        assertThat(journal.entries).isEmpty()
    }

    private suspend fun assertFailureStageCleans(stage: FailureStage) {
        val backend = FakeBackend(failureStage = stage)
        val journal = FakeJournal()

        val result = coordinator(backend, journal).publish(request())

        assertThat(result).isEqualTo(EditorPublicationResult.Failed)
        assertThat(backend.deleted).containsExactly(backend.destination)
        assertThat(journal.entries).isEmpty()
    }

    private fun coordinator(
        backend: FakeBackend,
        journal: FakeJournal,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO,
    ): EditorPublicationCoordinator {
        return EditorPublicationCoordinator(
            backend = backend,
            journal = journal,
            ioDispatcher = dispatcher,
            nowMs = { 1234L },
            operationId = { "operation_12345678" },
        )
    }

    private fun request() = EditorPublicationRequest(
        sourcePhotoId = 42L,
        sourceRevision = "42:100:2048:16x8",
        editState = PhotoEditState(rotationQuarterTurns = 1),
        renderedUriString = "content://rendered/input",
    )

    private enum class FailureStage {
        OPEN,
        HALF_COPY,
        CLOSE,
        PUBLISH,
        ACCESS,
        NO_SPACE,
        CANCEL,
    }

    private class FakeBackend(
        private val failureStage: FailureStage? = null,
        private val bytesCopied: Long = 128L,
        private val supportsSafePublication: Boolean = true,
    ) : EditorPublicationBackend {
        val destination = "content://media/output"
        val deleted = mutableListOf<String>()
        val states = mutableMapOf<String, EditorPendingState>()
        val pendingByName = mutableMapOf<String, List<String>>()
        val findByNameCalls = mutableListOf<String>()
        val providerThreads = linkedSetOf<String>()
        var publishCalls = 0
        var insertCalls = 0

        override fun supportsSafePublication(): Boolean = supportsSafePublication

        override fun insertPending(outputName: String, mimeType: String): String {
            recordThread()
            insertCalls += 1
            if (failureStage == FailureStage.ACCESS) {
                throw EditorPublicationAccessDeniedException()
            }
            states[destination] = EditorPendingState.PENDING
            return destination
        }

        override suspend fun copyAndFlush(
            sourceUriString: String,
            destinationUriString: String,
        ): Long {
            recordThread()
            when (failureStage) {
                FailureStage.OPEN -> throw IOException("open failed")
                FailureStage.HALF_COPY -> throw IOException("copy failed after bytes")
                FailureStage.CLOSE -> throw IOException("close failed")
                FailureStage.NO_SPACE -> throw EditorPublicationInsufficientStorageException()
                FailureStage.CANCEL -> throw CancellationException("cancel")
                else -> Unit
            }
            return bytesCopied
        }

        override fun verifyReadableImage(destinationUriString: String): Boolean {
            recordThread()
            return true
        }

        override fun publish(destinationUriString: String): Boolean {
            recordThread()
            publishCalls += 1
            if (failureStage == FailureStage.PUBLISH) return false
            states[destinationUriString] = EditorPendingState.PUBLISHED
            return true
        }

        override fun pendingState(destinationUriString: String): EditorPendingState {
            recordThread()
            return states[destinationUriString] ?: EditorPendingState.MISSING
        }

        override fun findPendingByOutputName(outputName: String): List<String> {
            recordThread()
            findByNameCalls += outputName
            return pendingByName[outputName].orEmpty()
        }

        override fun delete(destinationUriString: String): Boolean {
            recordThread()
            deleted += destinationUriString
            states[destinationUriString] = EditorPendingState.MISSING
            return true
        }

        private fun recordThread() {
            providerThreads += Thread.currentThread().name
        }
    }

    private class FakeJournal(
        private val failPutNumber: Int? = null,
    ) : EditorPublicationJournal {
        val entries = linkedMapOf<String, PendingEditorPublication>()
        private var putCount = 0

        override fun put(entry: PendingEditorPublication) {
            putCount += 1
            if (putCount == failPutNumber) {
                throw IOException("journal put failure")
            }
            entries[entry.operationId] = entry
        }

        override fun remove(operationId: String) {
            entries.remove(operationId)
        }

        override fun readAll(): List<PendingEditorPublication> = entries.values.toList()
    }
}
