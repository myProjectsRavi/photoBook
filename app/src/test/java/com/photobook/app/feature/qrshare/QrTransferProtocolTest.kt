package com.photobook.app.feature.qrshare

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QrTransferProtocolTest {

    @Test
    fun metadata_roundTrips() {
        val frame = QrTransferFrame.Metadata(
            transferId = "abc123",
            totalChunks = 4,
            fileName = "IMG_2026_04.jpg",
            mimeType = "image/jpeg",
            sha256 = "deadbeef",
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
}
