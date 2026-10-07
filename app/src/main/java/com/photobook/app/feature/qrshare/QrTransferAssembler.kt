package com.photobook.app.feature.qrshare

import java.util.Base64

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

class QrTransferAssembler(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val sessions = linkedMapOf<String, Session>()

    /**
     * Reset only in-flight assembly. Completed/conflicted transfer IDs remain process-local
     * replay-protected across "scan another" and receiver lifecycle recreation.
     */
    fun reset() {
        sessions.clear()
    }

    fun consume(rawValue: String): QrAssemblyResult? {
        pruneExpired()
        val frame = QrTransferProtocol.parse(rawValue) ?: return null
        val transferId = when (frame) {
            is QrTransferFrame.Single -> frame.transferId
            is QrTransferFrame.Metadata -> frame.transferId
            is QrTransferFrame.Data -> frame.transferId
        }

        val nowMs = clock()
        if (QrReplayGuard.isBlocked(transferId, nowMs)) {
            return QrAssemblyResult.Error(transferId, "Transfer session has already completed or conflicted.")
        }

        if (frame is QrTransferFrame.Single) {
            if (sessions.remove(transferId) != null) {
                QrReplayGuard.block(transferId, nowMs)
                return QrAssemblyResult.Error(transferId, "Transfer frame type changed.")
            }
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
            QrReplayGuard.block(frame.transferId, nowMs)
            return QrAssemblyResult.Completed(
                transferId = frame.transferId,
                fileName = frame.fileName,
                mimeType = frame.mimeType,
                bytes = bytes,
            )
        }

        val session = sessions[transferId] ?: run {
            if (sessions.size >= MAX_SESSIONS) {
                return QrAssemblyResult.Error(transferId, "Too many active transfer sessions.")
            }
            Session(lastTouchedMs = nowMs).also { sessions[transferId] = it }
        }

        when (frame) {
            is QrTransferFrame.Single -> Unit
            is QrTransferFrame.Metadata -> {
                val existing = session.metadata
                if (existing != null && existing != frame) {
                    sessions.remove(transferId)
                    QrReplayGuard.block(transferId, nowMs)
                    return QrAssemblyResult.Error(transferId, "Transfer metadata changed.")
                }
                session.metadata = frame
                session.lastTouchedMs = nowMs
                if (session.encodedPayloadLength > QrTransferProtocol.maxEncodedPayloadLength(frame.byteSize)) {
                    sessions.remove(transferId)
                    QrReplayGuard.block(transferId, nowMs)
                    return QrAssemblyResult.Error(transferId, "Transfer payload exceeds declared size.")
                }
            }

            is QrTransferFrame.Data -> {
                val metadata = session.metadata
                if (metadata != null && frame.chunkIndex >= metadata.totalChunks) {
                    sessions.remove(transferId)
                    QrReplayGuard.block(transferId, nowMs)
                    return QrAssemblyResult.Error(transferId, "Transfer chunk index is invalid.")
                }
                val existingPayload = session.chunks[frame.chunkIndex]
                if (existingPayload != null && existingPayload != frame.chunkPayload) {
                    sessions.remove(transferId)
                    QrReplayGuard.block(transferId, nowMs)
                    return QrAssemblyResult.Error(transferId, "Transfer chunk changed.")
                }
                if (existingPayload == null) {
                    session.chunks[frame.chunkIndex] = frame.chunkPayload
                    session.encodedPayloadLength += frame.chunkPayload.length
                }
                session.lastTouchedMs = nowMs
                if (session.chunks.size > QrTransferProtocol.MAX_TOTAL_CHUNKS ||
                    session.encodedPayloadLength > QrTransferProtocol.MAX_ENCODED_PAYLOAD_LENGTH
                ) {
                    sessions.remove(transferId)
                    QrReplayGuard.block(transferId, nowMs)
                    return QrAssemblyResult.Error(transferId, "Transfer payload is too large.")
                }
                if (metadata != null &&
                    session.encodedPayloadLength > QrTransferProtocol.maxEncodedPayloadLength(metadata.byteSize)
                ) {
                    sessions.remove(transferId)
                    QrReplayGuard.block(transferId, nowMs)
                    return QrAssemblyResult.Error(transferId, "Transfer payload exceeds declared size.")
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
        if (payload.length > QrTransferProtocol.maxEncodedPayloadLength(metadata.byteSize)) {
            sessions.remove(transferId)
            return QrAssemblyResult.Error(
                transferId = transferId,
                reason = "Transfer payload exceeds declared size.",
            )
        }
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
        QrReplayGuard.block(transferId, nowMs)
        return QrAssemblyResult.Completed(
            transferId = transferId,
            fileName = metadata.fileName,
            mimeType = metadata.mimeType,
            bytes = bytes,
        )
    }

    private class Session(
        var lastTouchedMs: Long,
    ) {
        var metadata: QrTransferFrame.Metadata? = null
        val chunks = linkedMapOf<Int, String>()
        var encodedPayloadLength: Int = 0
    }

    private fun pruneExpired() {
        val now = clock()
        sessions.entries.removeIf { now - it.value.lastTouchedMs > SESSION_TTL_MS }
    }

    companion object {
        private const val MAX_SESSIONS = 4
        internal const val SESSION_TTL_MS = 2 * 60 * 1000L
    }
}
