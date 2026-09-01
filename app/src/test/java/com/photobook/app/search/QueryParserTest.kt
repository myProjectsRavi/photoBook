package com.photobook.app.search

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueryParserTest {

    private val parser = QueryParser()

    @Test
    fun tokenize_combinesRelativeDatePattern() {
        val tokens = parser.tokenize("last 7 days selfie")

        assertThat(tokens).contains("last_7_days")
        assertThat(tokens).contains("selfie")
    }

    @Test
    fun normalize_combinesKnownPhrases() {
        val normalized = parser.normalize("  This   Week  near me  ")

        assertThat(normalized).isEqualTo("this_week near_me")
    }

    @Test
    fun normalize_isCaseInsensitiveForEnglishTextAndNumbers() {
        assertThat(parser.normalize("PhotoBook ABC xyz 12345"))
            .isEqualTo("photobook abc xyz 12345")
        assertThat(parser.normalize("PHoToBoOk 12345"))
            .isEqualTo("photobook 12345")
    }
}
