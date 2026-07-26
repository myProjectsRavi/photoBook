package com.photobook.app.feature.qrshare

import java.util.Base64

sealed interface QrTransferFrame {
    data class Single(
        val transferId: String,
        val fileName: String,
        val mimeType: String,
        val sha256: String,
        val byteSize: Int,
        val payload: String,
    ) : QrTransferFrame

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
    private const val SINGLE_TYPE = "S"
    private const val META_TYPE = "M"
    private const val DATA_TYPE = "D"
    private const val DELIMITER = "|"

    const val MAX_TRANSFER_ID_LENGTH = 64
    const val MAX_FILE_NAME_LENGTH = 120
    const val MAX_TOTAL_CHUNKS = 512
    const val MAX_CHUNK_PAYLOAD_LENGTH = 2_048
    const val MAX_TRANSFER_BYTES = 120_000
    private const val MAX_FRAME_LENGTH = 200_000
    private val SHA256_PATTERN = Regex("[0-9a-f]{64}")
    private val TRANSFER_ID_PATTERN = Regex("[A-Za-z0-9_-]{1,$MAX_TRANSFER_ID_LENGTH}")
    private val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")

    fun encodeSingle(frame: QrTransferFrame.Single): String {
        val namePayload = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(frame.fileName.toByteArray(Charsets.UTF_8))
        return listOf(
            PREFIX,
            SINGLE_TYPE,
            frame.transferId,
            namePayload,
            frame.mimeType,
            frame.sha256,
            frame.byteSize.toString(),
            frame.payload,
        ).joinToString(DELIMITER)
    }

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
        if (rawValue.length > MAX_FRAME_LENGTH) return null
        val parts = rawValue.split(DELIMITER)
        if (parts.size < 5 || parts[0] != PREFIX) return null

        return when (parts[1]) {
            SINGLE_TYPE -> parseSingle(parts)
            META_TYPE -> parseMetadata(parts)
            DATA_TYPE -> parseData(parts)
            else -> null
        }
    }

    private fun parseSingle(parts: List<String>): QrTransferFrame.Single? {
        if (parts.size != 8) return null

        val transferId = parts[2].trim()
        val fileName = runCatching {
            String(Base64.getUrlDecoder().decode(parts[3]), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val mimeType = parts[4].trim().lowercase()
        val sha256 = parts[5].trim().lowercase()
        val byteSize = parts[6].toIntOrNull() ?: return null
        val payload = parts[7].trim()
        if (!isValidTransferId(transferId) || !isSafeFileName(fileName) ||
            mimeType !in ALLOWED_MIME_TYPES || !SHA256_PATTERN.matches(sha256) ||
            byteSize !in 1..MAX_TRANSFER_BYTES ||
            payload.isBlank() || payload.length > MAX_TRANSFER_BYTES * 2
        ) return null

        return QrTransferFrame.Single(
            transferId = transferId,
            fileName = fileName,
            mimeType = mimeType,
            sha256 = sha256,
            byteSize = byteSize,
            payload = payload,
        )
    }

    private fun parseMetadata(parts: List<String>): QrTransferFrame.Metadata? {
        if (parts.size != 8) return null

        val transferId = parts[2].trim()
        val totalChunks = parts[3].toIntOrNull() ?: return null
        val fileName = runCatching {
            String(Base64.getUrlDecoder().decode(parts[4]), Charsets.UTF_8)
        }.getOrNull() ?: return null
        val mimeType = parts[5].trim().lowercase()
        val sha256 = parts[6].trim().lowercase()
        val byteSize = parts[7].toIntOrNull() ?: return null

        if (!isValidTransferId(transferId) || totalChunks !in 1..MAX_TOTAL_CHUNKS ||
            !isSafeFileName(fileName) || mimeType !in ALLOWED_MIME_TYPES ||
            !SHA256_PATTERN.matches(sha256) || byteSize !in 1..MAX_TRANSFER_BYTES
        ) return null

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
        if (!isValidTransferId(transferId) || chunkIndex !in 0 until MAX_TOTAL_CHUNKS ||
            chunkPayload.isBlank() || chunkPayload.length > MAX_CHUNK_PAYLOAD_LENGTH
        ) return null

        return QrTransferFrame.Data(
            transferId = transferId,
            chunkIndex = chunkIndex,
            chunkPayload = chunkPayload,
        )
    }

    private fun isValidTransferId(value: String): Boolean = TRANSFER_ID_PATTERN.matches(value)

    private fun isSafeFileName(value: String): Boolean {
        return value.isNotBlank() && value.length <= MAX_FILE_NAME_LENGTH &&
            value != "." && value != ".." &&
            !value.contains('/') && !value.contains('\\') &&
            value.none { it.isISOControl() }
    }
}
