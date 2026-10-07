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
}
