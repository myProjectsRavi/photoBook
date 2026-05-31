package com.photobook.app.feature.qrshare

import com.google.common.truth.Truth.assertThat
import java.util.Base64
import org.junit.Test

class QrTransferAssemblerTest {

    @Test
    fun consume_completesSingleFrameTransfer() {
        val assembler = QrTransferAssembler()
        val bytes = "hello single photobook".toByteArray()
        val frame = QrTransferFrame.Single(
            transferId = "single1",
            fileName = "demo.webp",
            mimeType = "image/webp",
            sha256 = QrPayloadHash.sha256(bytes),
            byteSize = bytes.size,
            payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
        )

        val completed = assembler.consume(
            QrTransferProtocol.encodeSingle(frame)
        ) as QrAssemblyResult.Completed

        assertThat(completed.transferId).isEqualTo("single1")
        assertThat(completed.fileName).isEqualTo("demo.webp")
        assertThat(completed.bytes).isEqualTo(bytes)
    }

    @Test
    fun consume_reassemblesPayload_whenAllChunksArrive() {
        val assembler = QrTransferAssembler()
        val bytes = "hello photobook".toByteArray()
        val transferId = "tx123"
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val chunks = payload.chunked(4)

        val metadata = QrTransferFrame.Metadata(
            transferId = transferId,
            totalChunks = chunks.size,
            fileName = "demo.jpg",
            mimeType = "image/jpeg",
            sha256 = QrPayloadHash.sha256(bytes),
            byteSize = bytes.size,
        )
        assembler.consume(QrTransferProtocol.encodeMetadata(metadata))

        var finalResult: QrAssemblyResult? = null
        chunks.forEachIndexed { index, chunk ->
            finalResult = assembler.consume(
                QrTransferProtocol.encodeData(
                    QrTransferFrame.Data(
                        transferId = transferId,
                        chunkIndex = index,
                        chunkPayload = chunk,
                    )
                )
            )
        }

        val completed = finalResult as QrAssemblyResult.Completed
        assertThat(completed.transferId).isEqualTo(transferId)
        assertThat(completed.fileName).isEqualTo("demo.jpg")
        assertThat(completed.bytes).isEqualTo(bytes)
    }
}
