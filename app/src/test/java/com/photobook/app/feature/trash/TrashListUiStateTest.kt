package com.photobook.app.feature.trash

import org.junit.Assert.assertEquals
import org.junit.Test

class TrashListUiStateTest {

    @Test
    fun successfulEmptyTrash_remainsReadyInsteadOfError() {
        assertEquals(
            TrashListUiState.READY,
            TrashListResult.Success(emptyList()).toTrashListUiState(),
        )
    }

    @Test
    fun unsupportedAndroid_remainsDistinctFromEmptyTrash() {
        assertEquals(
            TrashListUiState.UNSUPPORTED,
            TrashListResult.UnsupportedAndroid.toTrashListUiState(),
        )
    }

    @Test
    fun providerFailure_remainsDistinctFromEmptyTrash() {
        assertEquals(
            TrashListUiState.ERROR,
            TrashListResult.Error(IllegalStateException("query failed")).toTrashListUiState(),
        )
    }

    @Test
    fun fullPage_hasNoNextOffsetUntilLookaheadFindsAnotherItem() {
        assertEquals(null, nextTrashOffset(offset = 0, pageSize = 120, fetchedCount = 120))
        assertEquals(120, nextTrashOffset(offset = 0, pageSize = 120, fetchedCount = 121))
    }

    @Test
    fun nextPage_advancesByExactlyOnePage() {
        assertEquals(360, nextTrashOffset(offset = 240, pageSize = 120, fetchedCount = 121))
    }
}
