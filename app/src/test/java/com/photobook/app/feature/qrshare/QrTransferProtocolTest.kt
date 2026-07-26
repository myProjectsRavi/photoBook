package com.photobook.app.feature.qrshare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QrTransferProtocolTest {

    @Test
    fun single_roundTrips() {
        val frame = QrTransferFrame.Single(
            transferId = "one123",
            fileName = "IMG_2026_04.jpg",
            mimeType = "image/webp",
            sha256 = "0".repeat(64),
            byteSize = 1024,
            payload = "AABBCCDD",
        )

        val encoded = QrTransferProtocol.encodeSingle(frame)
        val parsed = QrTransferProtocol.parse(encoded)

        assertThat(parsed).isEqualTo(frame)
    }

    @Test
    fun metadata_roundTrips() {
        val frame = QrTransferFrame.Metadata(
            transferId = "abc123",
            totalChunks = 4,
            fileName = "IMG_2026_04.jpg",
            mimeType = "image/jpeg",
            sha256 = "0".repeat(64),
            byteSize = 2048,
        )

        val encoded = QrTransferProtocol.encodeMetadata(frame)
        val parsed = QrTransferProtocol.parse(encoded)

        assertThat(parsed).isEqualTo(frame)
    }

    @Test
    fun data_roundTrips() {
        val frame = QrTransferFrame.Data(
            transferId = "xyz789",
            chunkIndex = 1,
            chunkPayload = "AABBCCDD",
        )

        val encoded = QrTransferProtocol.encodeData(frame)
        val parsed = QrTransferProtocol.parse(encoded)

        assertThat(parsed).isEqualTo(frame)
    }

    @Test
    fun rejectsUnsafeMetadataAndOversizedChunks() {
        val unsafeName = java.util.Base64.getUrlEncoder().withoutPadding()
            .encodeToString("../private.jpg".toByteArray())
        val unsafe = listOf(
            "PB1", "M", "tx", "1", unsafeName, "image/jpeg", "0".repeat(64), "10",
        ).joinToString("|")
        val oversizedChunk = listOf(
            "PB1", "D", "tx", "0", "A".repeat(QrTransferProtocol.MAX_CHUNK_PAYLOAD_LENGTH + 1),
        ).joinToString("|")

        assertThat(QrTransferProtocol.parse(unsafe)).isNull()
        assertThat(QrTransferProtocol.parse(oversizedChunk)).isNull()
    }
}
