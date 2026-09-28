package com.photobook.app.feature.editor

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.system.ErrnoException
import android.system.OsConstants
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Immutable publication request captured before rendering/publishing begins.
 *
 * [sourceRevision] is deliberately metadata-only and must not contain a file path,
 * OCR text, note text, or other user content.
 */
data class EditorPublicationRequest(
    val sourcePhotoId: Long,
    val sourceRevision: String,
    val editState: PhotoEditState,
    val renderedUriString: String,
    val mimeType: String = "image/jpeg",
)

sealed interface EditorPublicationResult {
    data class Success(val uriString: String) : EditorPublicationResult
    data object Cancelled : EditorPublicationResult
    data object InsufficientStorage : EditorPublicationResult
    data object AccessDenied : EditorPublicationResult
    data object Failed : EditorPublicationResult
}

data class EditorPublicationRecoveryReport(
    val recoveredPending: Int,
    val preservedPublished: Int,
    val unresolved: Int,
)

internal data class PendingEditorPublication(
    val operationId: String,
    val outputName: String,
    val destinationUriString: String?,
    val sourcePhotoId: Long,
    val sourceRevision: String,
    val createdAtMs: Long,
)

internal enum class EditorPendingState {
    PENDING,
    PUBLISHED,
    MISSING,
    UNKNOWN,
}

internal interface EditorPublicationJournal {
    fun put(entry: PendingEditorPublication)
    fun remove(operationId: String)
    fun readAll(): List<PendingEditorPublication>
}

internal interface EditorPublicationBackend {
    fun supportsSafePublication(): Boolean
    fun insertPending(outputName: String, mimeType: String): String
    suspend fun copyAndFlush(sourceUriString: String, destinationUriString: String): Long
    fun verifyReadableImage(destinationUriString: String): Boolean
    fun publish(destinationUriString: String): Boolean
    fun pendingState(destinationUriString: String): EditorPendingState
    fun findPendingByOutputName(outputName: String): List<String>
    fun delete(destinationUriString: String): Boolean
}

internal class EditorPublicationInsufficientStorageException(
    cause: Throwable? = null,
) : IOException("Insufficient storage for edited copy", cause)

internal class EditorPublicationAccessDeniedException(
    cause: Throwable? = null,
) : IOException("Storage access denied for edited copy", cause)

internal class EditorPublicationCancelledException : IOException("Edited copy publication cancelled")

internal class FileEditorPublicationJournal(
    private val directory: File,
) : EditorPublicationJournal {

    override fun put(entry: PendingEditorPublication) {
        synchronized(FILE_LOCK) {
            if (!directory.exists() && !directory.mkdirs()) {
                throw IOException("Unable to create editor publication journal")
            }
            val finalFile = journalFile(entry.operationId)
            val tempFile = File(directory, "." + entry.operationId + ".tmp")
            FileOutputStream(tempFile).use { raw ->
                DataOutputStream(BufferedOutputStream(raw)).use { output ->
                    output.writeInt(FORMAT_VERSION)
                    output.writeUTF(entry.operationId)
                    output.writeUTF(entry.outputName)
                    output.writeBoolean(entry.destinationUriString != null)
                    if (entry.destinationUriString != null) {
                        output.writeUTF(entry.destinationUriString)
                    }
                    output.writeLong(entry.sourcePhotoId)
                    output.writeUTF(entry.sourceRevision)
                    output.writeLong(entry.createdAtMs)
                    output.flush()
                    raw.fd.sync()
                }
            }
            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                throw IOException("Unable to commit editor publication journal")
            }
        }
    }

    override fun remove(operationId: String) {
        synchronized(FILE_LOCK) {
            val file = journalFile(operationId)
            if (file.exists() && !file.delete()) {
                throw IOException("Unable to remove editor publication journal entry")
            }
        }
    }

    override fun readAll(): List<PendingEditorPublication> {
        synchronized(FILE_LOCK) {
            if (!directory.isDirectory) return emptyList()
            return directory.listFiles()
                .orEmpty()
                .filter { it.isFile && it.name.endsWith(JOURNAL_SUFFIX) }
                .sortedBy { it.name }
                .mapNotNull(::readEntry)
        }
    }

    private fun readEntry(file: File): PendingEditorPublication? {
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                check(input.readInt() == FORMAT_VERSION) { "Unsupported editor journal format" }
                val operationId = input.readUTF()
                val outputName = input.readUTF()
                val destination = if (input.readBoolean()) input.readUTF() else null
                PendingEditorPublication(
                    operationId = operationId,
                    outputName = outputName,
                    destinationUriString = destination,
                    sourcePhotoId = input.readLong(),
                    sourceRevision = input.readUTF(),
                    createdAtMs = input.readLong(),
                )
            }
        }.getOrNull()
    }

    private fun journalFile(operationId: String): File {
        require(OPERATION_ID_PATTERN.matches(operationId)) { "Invalid editor operation ID" }
        return File(directory, operationId + JOURNAL_SUFFIX)
    }

    private companion object {
        const val FORMAT_VERSION = 1
        const val JOURNAL_SUFFIX = ".pending"
        val OPERATION_ID_PATTERN = Regex("^[a-zA-Z0-9_-]{8,80}$")
        val FILE_LOCK = Any()
    }
}

internal class AndroidEditorPublicationBackend(
    private val context: Context,
) : EditorPublicationBackend {

    private val resolver = context.contentResolver

    override fun supportsSafePublication(): Boolean {
        // PhotoBook deliberately does not request broad legacy WRITE_EXTERNAL_STORAGE.
        // API 29+ MediaStore pending rows provide the recoverable publication boundary
        // required by S03. Older Android versions fail clearly rather than broadening
        // storage permissions or exposing a partially written public image.
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    }

    override fun insertPending(outputName: String, mimeType: String): String {
        if (!supportsSafePublication()) {
            throw EditorPublicationAccessDeniedException()
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, outputName)
            put(MediaStore.Images.Media.MIME_TYPE, mimeType)
            put(MediaStore.Images.Media.RELATIVE_PATH, EDITOR_RELATIVE_PATH)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        return providerCall {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.toString()
                ?: throw IOException("MediaStore insert returned null")
        }
    }

    override suspend fun copyAndFlush(
        sourceUriString: String,
        destinationUriString: String,
    ): Long {
        return providerCall {
            val sourceUri = Uri.parse(sourceUriString)
            val destinationUri = Uri.parse(destinationUriString)
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(destinationUri, "w")?.use { output ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        output.write(buffer, 0, read)
                        total += read
                    }
                    output.flush()
                    total
                } ?: throw IOException("Unable to open edited-copy destination")
            } ?: throw EditorPublicationAccessDeniedException()
        }
    }

    override fun verifyReadableImage(destinationUriString: String): Boolean {
        return providerCall {
            val destinationUri = Uri.parse(destinationUriString)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(destinationUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return@providerCall false
            options.outWidth > 0 && options.outHeight > 0
        }
    }

    override fun publish(destinationUriString: String): Boolean {
        return providerCall {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@providerCall false
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(Uri.parse(destinationUriString), values, null, null) == 1
        }
    }

    override fun pendingState(destinationUriString: String): EditorPendingState {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return EditorPendingState.UNKNOWN
        return try {
            resolver.query(
                Uri.parse(destinationUriString),
                arrayOf(MediaStore.Images.Media.IS_PENDING),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) {
                    EditorPendingState.MISSING
                } else {
                    val index = cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                    if (index < 0) {
                        EditorPendingState.UNKNOWN
                    } else if (cursor.getInt(index) == 1) {
                        EditorPendingState.PENDING
                    } else {
                        EditorPendingState.PUBLISHED
                    }
                }
            } ?: EditorPendingState.UNKNOWN
        } catch (_: SecurityException) {
            EditorPendingState.UNKNOWN
        } catch (_: RuntimeException) {
            EditorPendingState.UNKNOWN
        }
    }

    override fun findPendingByOutputName(outputName: String): List<String> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        return providerCall {
            val matches = mutableListOf<String>()
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.IS_PENDING),
                MediaStore.Images.Media.DISPLAY_NAME + " = ? AND " +
                    MediaStore.Images.Media.RELATIVE_PATH + " = ?",
                arrayOf(outputName, EDITOR_RELATIVE_PATH),
                null,
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val pendingIndex = cursor.getColumnIndex(MediaStore.Images.Media.IS_PENDING)
                while (idIndex >= 0 && pendingIndex >= 0 && cursor.moveToNext()) {
                    if (cursor.getInt(pendingIndex) == 1) {
                        matches += ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(idIndex),
                        ).toString()
                    }
                }
            }
            matches
        }
    }

    override fun delete(destinationUriString: String): Boolean {
        return try {
            val deleted = resolver.delete(Uri.parse(destinationUriString), null, null)
            deleted == 1 || pendingState(destinationUriString) == EditorPendingState.MISSING
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }

    private inline fun <T> providerCall(block: () -> T): T {
        try {
            return block()
        } catch (error: SecurityException) {
            throw EditorPublicationAccessDeniedException(error)
        } catch (error: IOException) {
            if (error.isNoSpaceFailure()) {
                throw EditorPublicationInsufficientStorageException(error)
            }
            throw error
        } catch (error: RuntimeException) {
            if (error.isNoSpaceFailure()) {
                throw EditorPublicationInsufficientStorageException(error)
            }
            throw error
        }
    }

    private fun Throwable.isNoSpaceFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ErrnoException && current.errno == OsConstants.ENOSPC) return true
            val message = current.message.orEmpty()
            if (
                message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space left", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        const val EDITOR_RELATIVE_PATH = "Pictures/PhotoBook/"
        const val COPY_BUFFER_BYTES = 64 * 1024
    }
}

internal class EditorPublicationCoordinator(
    private val backend: EditorPublicationBackend,
    private val journal: EditorPublicationJournal,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val operationId: () -> String = {
        UUID.randomUUID().toString().replace("-", "")
    },
) {

    suspend fun publish(request: EditorPublicationRequest): EditorPublicationResult {
        return PROCESS_MUTEX.withLock {
            withContext(ioDispatcher) {
                publishLocked(request)
            }
        }
    }

    suspend fun recoverInterruptedOutputs(): EditorPublicationRecoveryReport {
        return PROCESS_MUTEX.withLock {
            withContext(ioDispatcher) {
                recoverLocked()
            }
        }
    }

    private suspend fun publishLocked(request: EditorPublicationRequest): EditorPublicationResult {
        currentCoroutineContext().ensureActive()
        if (!backend.supportsSafePublication()) {
            return EditorPublicationResult.AccessDenied
        }

        val id = operationId()
        val outputName = "PhotoBook_Edit_" + nowMs() + "_" + id.take(12) + ".jpg"
        var entry = PendingEditorPublication(
            operationId = id,
            outputName = outputName,
            destinationUriString = null,
            sourcePhotoId = request.sourcePhotoId,
            sourceRevision = request.sourceRevision,
            createdAtMs = nowMs(),
        )
        var destination: String? = null
        var published = false

        return try {
            // Durable intent is written before MediaStore insertion. If the process dies
            // immediately after insertion, recovery can find the uniquely named pending row.
            journal.put(entry)
            currentCoroutineContext().ensureActive()

            destination = backend.insertPending(outputName, request.mimeType)
            entry = entry.copy(destinationUriString = destination)
            journal.put(entry)
            currentCoroutineContext().ensureActive()

            val bytesCopied = backend.copyAndFlush(
                sourceUriString = request.renderedUriString,
                destinationUriString = destination,
            )
            if (bytesCopied <= 0L) {
                return EditorPublicationResult.Failed
            }
            currentCoroutineContext().ensureActive()

            if (!backend.verifyReadableImage(destination)) {
                return EditorPublicationResult.Failed
            }
            currentCoroutineContext().ensureActive()

            if (!backend.publish(destination)) {
                return EditorPublicationResult.Failed
            }
            published = true

            // A crash or journal-delete failure after publication is harmless: recovery
            // checks IS_PENDING and preserves already published media.
            runCatching { journal.remove(id) }
            EditorPublicationResult.Success(destination)
        } catch (error: CancellationException) {
            throw error
        } catch (_: EditorPublicationCancelledException) {
            EditorPublicationResult.Cancelled
        } catch (_: EditorPublicationInsufficientStorageException) {
            EditorPublicationResult.InsufficientStorage
        } catch (_: EditorPublicationAccessDeniedException) {
            EditorPublicationResult.AccessDenied
        } catch (error: IOException) {
            if (error.isNoSpaceFailure()) {
                EditorPublicationResult.InsufficientStorage
            } else {
                EditorPublicationResult.Failed
            }
        } catch (_: SecurityException) {
            EditorPublicationResult.AccessDenied
        } catch (_: RuntimeException) {
            EditorPublicationResult.Failed
        } finally {
            if (!published) {
                cleanupFailedOperation(entry, destination)
            }
        }
    }

    private fun cleanupFailedOperation(
        entry: PendingEditorPublication,
        destination: String?,
    ) {
        val cleaned = if (destination == null) {
            true
        } else {
            runCatching { backend.delete(destination) }.getOrDefault(false)
        }
        if (cleaned) {
            runCatching { journal.remove(entry.operationId) }
        } else {
            runCatching { journal.put(entry.copy(destinationUriString = destination)) }
        }
    }

    private fun recoverLocked(): EditorPublicationRecoveryReport {
        var recovered = 0
        var preserved = 0
        var unresolved = 0

        for (entry in journal.readAll()) {
            val destinations = entry.destinationUriString?.let(::listOf)
                ?: backend.findPendingByOutputName(entry.outputName)

            if (destinations.isEmpty()) {
                runCatching { journal.remove(entry.operationId) }
                continue
            }

            var operationUnresolved = false
            var operationRecovered = false
            var operationPreserved = false

            for (destination in destinations) {
                when (backend.pendingState(destination)) {
                    EditorPendingState.PENDING -> {
                        if (backend.delete(destination)) {
                            operationRecovered = true
                        } else {
                            operationUnresolved = true
                        }
                    }

                    EditorPendingState.PUBLISHED -> operationPreserved = true
                    EditorPendingState.MISSING -> Unit
                    EditorPendingState.UNKNOWN -> operationUnresolved = true
                }
            }

            if (operationUnresolved) {
                unresolved += 1
            } else {
                runCatching { journal.remove(entry.operationId) }
                if (operationRecovered) recovered += 1
                if (operationPreserved) preserved += 1
            }
        }

        return EditorPublicationRecoveryReport(
            recoveredPending = recovered,
            preservedPublished = preserved,
            unresolved = unresolved,
        )
    }

    private fun Throwable.isNoSpaceFailure(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is ErrnoException && current.errno == OsConstants.ENOSPC) return true
            val message = current.message.orEmpty()
            if (
                message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("No space left", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }

    private companion object {
        val PROCESS_MUTEX = Mutex()
    }
}

/**
 * Production facade. Provider, stream and journal I/O is coordinated on Dispatchers.IO.
 */
class EditorOutputPublisher(
    context: Context,
) {
    private val coordinator = EditorPublicationCoordinator(
        backend = AndroidEditorPublicationBackend(context.applicationContext),
        journal = FileEditorPublicationJournal(
            File(context.applicationContext.filesDir, "editor_publication_journal"),
        ),
    )

    suspend fun publish(request: EditorPublicationRequest): EditorPublicationResult {
        return coordinator.publish(request)
    }

    suspend fun recoverInterruptedOutputs(): EditorPublicationRecoveryReport {
        return coordinator.recoverInterruptedOutputs()
    }
}
