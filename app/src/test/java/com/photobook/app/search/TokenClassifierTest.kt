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
}
