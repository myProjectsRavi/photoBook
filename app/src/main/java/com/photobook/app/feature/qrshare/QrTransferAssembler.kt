package com.photobook.app.feature.qrshare

import java.util.Base64
import java.util.LinkedHashMap

sealed interface QrAssemblyResult {
    data class Progress(
        val transferId: String,
        val receivedChunks: Int,
        val totalChunks: Int?,
    ) : QrAssemblyResult

    data class Completed(
        val transferId: String,
        val fileName: String,
        val mimeType: String,
        val bytes: ByteArray,
    ) : QrAssemblyResult

    data class Error(
        val transferId: String?,
        val reason: String,
    ) : QrAssemblyResult
}

class QrTransferAssembler {
    private val sessions = linkedMapOf<String, Session>()
    private val completedTransfers = LinkedHashMap<String, Long>()

    fun reset() {
        sessions.clear()
        completedTransfers.clear()
    }

    fun consume(rawValue: String): QrAssemblyResult? {
        pruneExpired()
        val frame = QrTransferProtocol.parse(rawValue) ?: return null
        val transferId = when (frame) {
            is QrTransferFrame.Single -> frame.transferId
            is QrTransferFrame.Metadata -> frame.transferId
            is QrTransferFrame.Data -> frame.transferId
        }

        if (frame is QrTransferFrame.Single) {
            val bytes = runCatching {
                Base64.getUrlDecoder().decode(frame.payload)
            }.getOrElse {
                return QrAssemblyResult.Error(
                    transferId = frame.transferId,
                    reason = "Corrupted transfer payload.",
                )
            }
            if (bytes.size != frame.byteSize) {
                return QrAssemblyResult.Error(
                    transferId = frame.transferId,
                    reason = "Transfer size verification failed.",
                )
            }
            val digest = QrPayloadHash.sha256(bytes)
            if (digest != frame.sha256.lowercase()) {
                return QrAssemblyResult.Error(
                    transferId = frame.transferId,
                    reason = "Transfer integrity check failed.",
                )
            }
            rememberCompleted(frame.transferId)
            return QrAssemblyResult.Completed(
                transferId = frame.transferId,
                fileName = frame.fileName,
                mimeType = frame.mimeType,
                bytes = bytes,
            )
        }

        if (completedTransfers.containsKey(transferId)) {
            return QrAssemblyResult.Error(transferId, "Transfer session has already completed.")
        }

        val session = sessions[transferId] ?: run {
            if (sessions.size >= MAX_SESSIONS) {
                return QrAssemblyResult.Error(transferId, "Too many active transfer sessions.")
            }
            Session(now = System.currentTimeMillis()).also { sessions[transferId] = it }
        }

        when (frame) {
            is QrTransferFrame.Single -> Unit
            is QrTransferFrame.Metadata -> {
                val existing = session.metadata
                if (existing != null && existing != frame) {
                    sessions.remove(transferId)
                    return QrAssemblyResult.Error(transferId, "Transfer metadata changed.")
                }
                session.metadata = frame
            }

            is QrTransferFrame.Data -> {
                val metadata = session.metadata
                if (metadata != null && frame.chunkIndex >= metadata.totalChunks) {
                    sessions.remove(transferId)
                    return QrAssemblyResult.Error(transferId, "Transfer chunk index is invalid.")
                }
                session.chunks.putIfAbsent(frame.chunkIndex, frame.chunkPayload)
                if (session.chunks.size > QrTransferProtocol.MAX_TOTAL_CHUNKS) {
                    sessions.remove(transferId)
                    return QrAssemblyResult.Error(transferId, "Transfer contains too many chunks.")
                }
            }
        }

        val metadata = session.metadata
        if (metadata == null) {
            return QrAssemblyResult.Progress(
                transferId = transferId,
                receivedChunks = session.chunks.size,
                totalChunks = null,
            )
        }

        if (metadata.totalChunks !in 1..QrTransferProtocol.MAX_TOTAL_CHUNKS ||
            metadata.byteSize !in 1..QrTransferProtocol.MAX_TRANSFER_BYTES
        ) {
            sessions.remove(transferId)
            return QrAssemblyResult.Error(
                transferId = transferId,
                reason = "Invalid transfer metadata.",
            )
        }

        if (session.chunks.size < metadata.totalChunks) {
            return QrAssemblyResult.Progress(
                transferId = transferId,
                receivedChunks = session.chunks.size,
                totalChunks = metadata.totalChunks,
            )
        }

        val orderedChunks = ArrayList<String>(metadata.totalChunks)
        for (index in 0 until metadata.totalChunks) {
            val chunk = session.chunks[index]
            if (chunk.isNullOrBlank()) {
                return QrAssemblyResult.Progress(
                    transferId = transferId,
                    receivedChunks = session.chunks.size,
                    totalChunks = metadata.totalChunks,
                )
            }
            orderedChunks += chunk
        }

        val payload = orderedChunks.joinToString(separator = "")
        val bytes = runCatching {
            Base64.getUrlDecoder().decode(payload)
        }.getOrElse {
            sessions.remove(transferId)
            return QrAssemblyResult.Error(
                transferId = transferId,
                reason = "Corrupted transfer payload.",
            )
        }

        if (bytes.size != metadata.byteSize) {
            sessions.remove(transferId)
            return QrAssemblyResult.Error(
                transferId = transferId,
                reason = "Transfer size verification failed.",
            )
        }

        val digest = QrPayloadHash.sha256(bytes)
        if (digest != metadata.sha256.lowercase()) {
            sessions.remove(transferId)
            return QrAssemblyResult.Error(
                transferId = transferId,
                reason = "Transfer integrity check failed.",
            )
        }

        sessions.remove(transferId)
        rememberCompleted(transferId)
        return QrAssemblyResult.Completed(
            transferId = transferId,
            fileName = metadata.fileName,
            mimeType = metadata.mimeType,
            bytes = bytes,
        )
    }

    private class Session {
        val now: Long
        var metadata: QrTransferFrame.Metadata? = null
        val chunks = linkedMapOf<Int, String>()

        constructor(now: Long) {
            this.now = now
        }
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { now - it.value.now > SESSION_TTL_MS }
        completedTransfers.entries.removeIf { now - it.value > SESSION_TTL_MS }
    }

    private fun rememberCompleted(transferId: String) {
        completedTransfers[transferId] = System.currentTimeMillis()
        while (completedTransfers.size > MAX_COMPLETED_TRANSFERS) {
            completedTransfers.remove(completedTransfers.entries.first().key)
        }
    }

    companion object {
        private const val MAX_SESSIONS = 4
        private const val MAX_COMPLETED_TRANSFERS = 8
        private const val SESSION_TTL_MS = 2 * 60 * 1000L
    }
}
