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
    fun completedTransfer_isRejectedAfterResetAndNewAssembler() {
        QrReplayGuard.clearForTests()
        val bytes = "replay-protected".toByteArray()
        val encoded = QrTransferProtocol.encodeSingle(
            QrTransferFrame.Single(
                transferId = "replay1",
                fileName = "preview.webp",
                mimeType = "image/webp",
                sha256 = QrPayloadHash.sha256(bytes),
                byteSize = bytes.size,
                payload = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            ),
        )

        val firstAssembler = QrTransferAssembler()
        assertThat(firstAssembler.consume(encoded)).isInstanceOf(QrAssemblyResult.Completed::class.java)

        firstAssembler.reset()
        assertThat(firstAssembler.consume(encoded)).isInstanceOf(QrAssemblyResult.Error::class.java)

        val recreatedAssembler = QrTransferAssembler()
        assertThat(recreatedAssembler.consume(encoded)).isInstanceOf(QrAssemblyResult.Error::class.java)
        QrReplayGuard.clearForTests()
    }

    @Test
    fun conflictingChunk_blocksTransferIdAcrossAssemblerRecreation() {
        QrReplayGuard.clearForTests()
        val assembler = QrTransferAssembler()
        val first = QrTransferProtocol.encodeData(
            QrTransferFrame.Data("conflict1", 0, "AAAA"),
        )
        val conflict = QrTransferProtocol.encodeData(
            QrTransferFrame.Data("conflict1", 0, "BBBB"),
        )

        assertThat(assembler.consume(first)).isInstanceOf(QrAssemblyResult.Progress::class.java)
        assertThat(assembler.consume(conflict)).isInstanceOf(QrAssemblyResult.Error::class.java)

        val recreated = QrTransferAssembler()
        assertThat(recreated.consume(first)).isInstanceOf(QrAssemblyResult.Error::class.java)
        QrReplayGuard.clearForTests()
    }

    @Test
    fun activeSessionTtl_refreshesOnValidFrames() {
        QrReplayGuard.clearForTests()
        var nowMs = 1_000L
        val assembler = QrTransferAssembler(clock = { nowMs })

        val first = QrTransferProtocol.encodeData(
            QrTransferFrame.Data("ttl1", 0, "AAAA"),
        )
        val second = QrTransferProtocol.encodeData(
            QrTransferFrame.Data("ttl1", 1, "BBBB"),
        )

        assertThat(assembler.consume(first)).isInstanceOf(QrAssemblyResult.Progress::class.java)
        nowMs += QrTransferAssembler.SESSION_TTL_MS - 1L
        assertThat(assembler.consume(second)).isInstanceOf(QrAssemblyResult.Progress::class.java)

        nowMs += QrTransferAssembler.SESSION_TTL_MS - 1L
        val duplicateSecond = assembler.consume(second) as QrAssemblyResult.Progress
        assertThat(duplicateSecond.receivedChunks).isEqualTo(2)
        QrReplayGuard.clearForTests()
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
