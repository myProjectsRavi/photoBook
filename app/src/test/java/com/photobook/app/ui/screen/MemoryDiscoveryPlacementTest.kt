package com.photobook.app.ui.screen

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryDiscoveryPlacementTest {

    @Test
    fun memoriesAreDiscoverableOnlyInAlbums() {
        assertThat(
            shouldShowMemoryDiscovery(
                destination = MainDestination.Albums,
                memoriesHidden = false,
                hasMemoryContent = true,
            ),
        ).isTrue()

        assertThat(
            shouldShowMemoryDiscovery(
                destination = MainDestination.Photos,
                memoriesHidden = false,
                hasMemoryContent = true,
            ),
        ).isFalse()
        assertThat(
            shouldShowMemoryDiscovery(
                destination = MainDestination.Tools,
                memoriesHidden = false,
                hasMemoryContent = true,
            ),
        ).isFalse()
    }

    @Test
    fun hiddenOrEmptyMemoriesStayOutOfAlbums() {
        assertThat(
            shouldShowMemoryDiscovery(
                destination = MainDestination.Albums,
                memoriesHidden = true,
                hasMemoryContent = true,
            ),
        ).isFalse()
        assertThat(
            shouldShowMemoryDiscovery(
                destination = MainDestination.Albums,
                memoriesHidden = false,
                hasMemoryContent = false,
            ),
        ).isFalse()
    }
}
