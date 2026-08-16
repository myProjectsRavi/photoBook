package com.photobook.app.feature.archive

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ArchivePaginationTest {

    @Test
    fun asymmetricCategoryPages_advanceIndependentlyWithoutSkipping() {
        val pageSize = 3
        var payments = ArchiveKeysetCursor()
        var food = ArchiveKeysetCursor()

        payments = payments.advance(
            page = listOf(
                ArchivePageKey(dateAdded = 500L, id = 50L),
                ArchivePageKey(dateAdded = 400L, id = 40L),
                ArchivePageKey(dateAdded = 300L, id = 30L),
            ),
            pageSize = pageSize,
        )
        food = food.advance(
            page = listOf(
                ArchivePageKey(dateAdded = 500L, id = 51L),
                ArchivePageKey(dateAdded = 100L, id = 10L),
            ),
            pageSize = pageSize,
        )

        assertThat(payments.beforeDateAdded).isEqualTo(300L)
        assertThat(payments.beforeId).isEqualTo(30L)
        assertThat(payments.exhausted).isFalse()

        assertThat(food.beforeDateAdded).isEqualTo(100L)
        assertThat(food.beforeId).isEqualTo(10L)
        assertThat(food.exhausted).isTrue()

        payments = payments.advance(
            page = listOf(
                ArchivePageKey(dateAdded = 250L, id = 25L),
                ArchivePageKey(dateAdded = 200L, id = 20L),
            ),
            pageSize = pageSize,
        )

        assertThat(payments.beforeDateAdded).isEqualTo(200L)
        assertThat(payments.beforeId).isEqualTo(20L)
        assertThat(payments.exhausted).isTrue()
    }

    @Test
    fun sameTimestamp_usesIdAsStableTieBreaker() {
        val cursor = ArchiveKeysetCursor().advance(
            page = listOf(
                ArchivePageKey(dateAdded = 500L, id = 9L),
                ArchivePageKey(dateAdded = 500L, id = 8L),
                ArchivePageKey(dateAdded = 500L, id = 7L),
            ),
            pageSize = 3,
        )

        assertThat(cursor.beforeDateAdded).isEqualTo(500L)
        assertThat(cursor.beforeId).isEqualTo(7L)
        assertThat(cursor.exhausted).isFalse()
    }

    @Test
    fun shortOrEmptyPage_marksOnlyThatCursorExhausted() {
        val shortPage = ArchiveKeysetCursor().advance(
            page = listOf(ArchivePageKey(dateAdded = 10L, id = 1L)),
            pageSize = 250,
        )
        val emptyPage = ArchiveKeysetCursor().advance(emptyList(), pageSize = 250)

        assertThat(shortPage.exhausted).isTrue()
        assertThat(emptyPage.exhausted).isTrue()
    }

    @Test(expected = IllegalStateException::class)
    fun nonAdvancingPage_isRejectedInsteadOfLoopingForever() {
        val cursor = ArchiveKeysetCursor(beforeDateAdded = 100L, beforeId = 10L)
        cursor.advance(
            page = listOf(ArchivePageKey(dateAdded = 100L, id = 10L)),
            pageSize = 1,
        )
    }
}
