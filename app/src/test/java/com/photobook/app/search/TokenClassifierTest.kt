package com.photobook.app.search

import com.google.common.truth.Truth.assertThat
import com.photobook.app.data.index.PhotoIndex
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TokenClassifierTest {

    @Test
    fun classify_detectsTemporalMonthAndYear() = runTest {
        val index = PhotoIndex()
        val classifier = TokenClassifier(index)

        assertThat(classifier.classify("today")).isInstanceOf(TemporalToken::class.java)
        assertThat((classifier.classify("january") as MonthToken).month).isEqualTo(1)
        assertThat((classifier.classify("2023") as YearToken).year).isEqualTo(2023)
    }

    @Test
    fun classify_detectsSourceTokens() = runTest {
        val index = PhotoIndex()
        val classifier = TokenClassifier(index)

        assertThat(classifier.classify("source:whatsapp"))
            .isEqualTo(SourceToken(PhotoSource.WhatsApp))
        assertThat(classifier.classify("downloads"))
            .isEqualTo(SourceToken(PhotoSource.Downloads))
    }

    @Test
    fun classify_keepsSingleDigitAsTextButRejectsSingleLetterNoise() = runTest {
        val classifier = TokenClassifier(PhotoIndex())

        assertThat(classifier.classify("7")).isEqualTo(TextToken("7"))
        assertThat(classifier.classify("a")).isEqualTo(UnknownToken("a"))
    }
}
