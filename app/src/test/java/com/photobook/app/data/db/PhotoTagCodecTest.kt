package com.photobook.app.data.db

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.model.MLTag
import org.junit.Test

class PhotoTagCodecTest {

    @Test
    fun preparedFoodEvidence_isPersistedButNotSearchable() {
        val tags = listOf(
            MLTag("food", 0.91f),
            MLTag("prepared_food", 0.88f),
            MLTag("people", 0.81f),
        )

        val encoded = PhotoTagCodec.encode(tags)
        val decoded = PhotoTagCodec.decode(encoded)
        val searchable = PhotoTagCodec.toSearchableText(decoded)

        assertThat(decoded.map { it.label }).contains("prepared_food")
        assertThat(searchable).contains("food")
        assertThat(searchable).contains("people")
        assertThat(searchable).doesNotContain("prepared_food")
        assertThat(searchable).doesNotContain("prepared")
    }
}
