package com.photobook.app.feature.qrshare

import java.util.Base64

sealed interface QrTransferFrame {
    data class Metadata(
        val transferId: String,
        val totalChunks: Int,
        val fileName: String,
        val mimeType: String,
        val sha256: String,
        val byteSize: Int,
    ) : QrTransferFrame

    data class Data(
        val transferId: String,
        val chunkIndex: Int,
        val chunkPayload: String,
    ) : QrTransferFrame
}

object QrTransferProtocol {
    private const val PREFIX = "PB1"
    private const val META_TYPE = "M"
    private const val DATA_TYPE = "D"
    private const val DELIMITER = "|"

    fun encodeMetadata(frame: QrTransferFrame.Metadata): String {
        val namePayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(frame.fileName.toByteArray(Charsets.UTF_8))
        return listOf(
            PREFIX,
            META_TYPE,
            frame.transferId,
            frame.totalChunks.toString(),
            namePayload,
            frame.mimeType,
            frame.sha256,
            frame.byteSize.toString(),
        ).joinToString(DELIMITER)
    }

    fun encodeData(frame: QrTransferFrame.Data): String {
        return listOf(
            PREFIX,
            DATA_TYPE,
            frame.transferId,
            frame.chunkIndex.toString(),
            frame.chunkPayload,
        ).joinToString(DELIMITER)
    }

    fun parse(rawValue: String): QrTransferFrame? {
        val parts = rawValue.split(DELIMITER)
        if (parts.size < 5 || parts[0] != PREFIX) return null

        return when (parts[1]) {
            META_TYPE -> parseMetadata(parts)
            DATA_TYPE -> parseData(parts)
            else -> null
        }
    }

    private fun parseMetadata(parts: List<String>): QrTransferFrame.Metadata? {
        if (parts.size != 8) return null

        val transferId = parts[2].trim()
        val totalChunks = parts[3].toIntOrNull() ?: return null
        val fileName = runCatching {
            String(Base64.getUrlDecoder().decode(parts[4]), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val mimeType = parts[5].trim().ifBlank { "image/jpeg" }
        val sha256 = parts[6].trim().lowercase()
        val byteSize = parts[7].toIntOrNull() ?: return null

        if (transferId.isBlank() || totalChunks <= 0 || byteSize <= 0 || fileName.isBlank()) return null

        return QrTransferFrame.Metadata(
            transferId = transferId,
            totalChunks = totalChunks,
            fileName = fileName,
            mimeType = mimeType,
            sha256 = sha256,
            byteSize = byteSize,
        )
    }

    private fun parseData(parts: List<String>): QrTransferFrame.Data? {
        if (parts.size != 5) return null
        val transferId = parts[2].trim()
        val chunkIndex = parts[3].toIntOrNull() ?: return null
        val chunkPayload = parts[4].trim()
        if (transferId.isBlank() || chunkIndex < 0 || chunkPayload.isBlank()) return null

        return QrTransferFrame.Data(
            transferId = transferId,
            chunkIndex = chunkIndex,
            chunkPayload = chunkPayload,
        )
    }
}
