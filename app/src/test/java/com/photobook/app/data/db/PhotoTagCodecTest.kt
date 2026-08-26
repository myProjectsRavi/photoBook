package com.photobook.app.data.db

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.MLTag
import org.junit.Test

class PhotoTagCodecTest {

    @Test
    fun decode_emptyPayloadsReturnEmptyWithoutChangingSemantics() {
        assertThat(PhotoTagCodec.decode("")).isEmpty()
        assertThat(PhotoTagCodec.decode("   ")).isEmpty()
        assertThat(PhotoTagCodec.decode("[]")).isEmpty()
        assertThat(PhotoTagCodec.decode("  []  ")).isEmpty()
    }

    @Test
    fun decode_nonEmptyPayloadStillRoundTrips() {
        val tags = listOf(
            MLTag("food", 0.91f),
            MLTag("people", 0.81f),
        )

        assertThat(PhotoTagCodec.decode(PhotoTagCodec.encode(tags))).isEqualTo(tags)
    }

    @Test
    fun preparedFoodEvidence_isNotSearchable() {
        val tags = listOf(
            MLTag("food", 0.91f),
            MLTag("prepared_food", 0.88f),
            MLTag("people", 0.81f),
        )

        val searchable = PhotoTagCodec.toSearchableText(tags)

        assertThat(searchable).contains("food")
        assertThat(searchable).contains("people")
        assertThat(searchable).doesNotContain("prepared_food")
        assertThat(searchable).doesNotContain("prepared")
    }
}
