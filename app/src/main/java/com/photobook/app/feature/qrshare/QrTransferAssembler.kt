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

class QrTransferAssembler {
    private val sessions = mutableMapOf<String, Session>()

    fun reset() {
        sessions.clear()
    }

    fun consume(rawValue: String): QrAssemblyResult? {
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
            return QrAssemblyResult.Completed(
                transferId = frame.transferId,
                fileName = frame.fileName,
                mimeType = frame.mimeType,
                bytes = bytes,
            )
        }

        val session = sessions.getOrPut(transferId) { Session() }

        when (frame) {
            is QrTransferFrame.Single -> Unit
            is QrTransferFrame.Metadata -> {
                session.metadata = frame
            }

            is QrTransferFrame.Data -> {
                session.chunks.putIfAbsent(frame.chunkIndex, frame.chunkPayload)
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

        if (metadata.totalChunks <= 0) {
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
        return QrAssemblyResult.Completed(
            transferId = transferId,
            fileName = metadata.fileName,
            mimeType = metadata.mimeType,
            bytes = bytes,
        )
    }

    private class Session {
        var metadata: QrTransferFrame.Metadata? = null
        val chunks = linkedMapOf<Int, String>()
    }
}
