package com.photobook.app.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GridContinuityPolicyTest {
    @Test
    fun scrubTarget_mapsTrackEdgesAndMidpointDeterministically() {
        assertThat(GridContinuityPolicy.scrubTargetIndex(0f, 1000f, 101)).isEqualTo(0)
        assertThat(GridContinuityPolicy.scrubTargetIndex(500f, 1000f, 101)).isEqualTo(50)
        assertThat(GridContinuityPolicy.scrubTargetIndex(1000f, 1000f, 101)).isEqualTo(100)
    }

    @Test
    fun scrubTarget_clampsOutOfRangeDragWithoutInvalidIndex() {
        assertThat(GridContinuityPolicy.scrubTargetIndex(-500f, 500f, 10)).isEqualTo(0)
        assertThat(GridContinuityPolicy.scrubTargetIndex(900f, 500f, 10)).isEqualTo(9)
    }

    @Test
    fun scrubTarget_emptyOrInvalidTrackFailsClosed() {
        assertThat(GridContinuityPolicy.scrubTargetIndex(10f, 100f, 0)).isNull()
        assertThat(GridContinuityPolicy.scrubTargetIndex(10f, 0f, 10)).isNull()
    }
}
