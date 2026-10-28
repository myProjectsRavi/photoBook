package com.photobook.app.ui.component

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GridContinuityPolicyTest {
    @Test
    fun loadedItemAt_ignoresStaleIndexDuringEmptySnapshotSwap() {
        assertThat(GridContinuityPolicy.loadedItemAt(0, emptyList<Long>(), 0)).isNull()
        assertThat(GridContinuityPolicy.loadedItemAt(2, emptyList<Long>(), 0)).isNull()
        assertThat(GridContinuityPolicy.loadedItemAt(-1, listOf(1L), 0)).isNull()
        assertThat(GridContinuityPolicy.loadedItemAt(0, listOf(1L), -1)).isNull()
    }

    @Test
    fun loadedItemAt_handlesPlaceholderOffsetsWithoutAccidentalRebinding() {
        assertThat(GridContinuityPolicy.loadedItemAt(4, listOf(17L, 18L), 5)).isNull()
        assertThat(GridContinuityPolicy.loadedItemAt(5, listOf(17L, 18L), 5)).isEqualTo(17L)
        assertThat(GridContinuityPolicy.loadedItemAt(6, listOf(17L, 18L), 5)).isEqualTo(18L)
        assertThat(GridContinuityPolicy.loadedItemAt(7, listOf(17L, 18L), 5)).isNull()
    }

    @Test
    fun loadedAnchor_tracksStablePhotoAcrossStructuralInsertion() {
        val before = listOf(10L, 20L, 30L, 40L)
        val after = listOf(5L, 10L, 20L, 30L, 40L)

        assertThat(GridContinuityPolicy.loadedAnchorIndex(30L, before, 0)).isEqualTo(2)
        assertThat(GridContinuityPolicy.loadedAnchorIndex(30L, after, 0)).isEqualTo(3)
        assertThat(GridContinuityPolicy.loadedAnchorIndex(30L, after, 7)).isEqualTo(10)
    }

    @Test
    fun loadedAnchor_missingIdentityDoesNotGuessByIndex() {
        assertThat(GridContinuityPolicy.loadedAnchorIndex(99L, listOf(10L, 20L), 0)).isNull()
    }

    @Test
    fun scrubIndicator_tracksOrdinaryScrollAndAccountsForVisibleViewport() {
        assertThat(
            GridContinuityPolicy.scrubIndicatorY(
                firstVisibleIndex = 450,
                visibleItemCount = 100,
                itemCount = 1000,
                trackHeightPx = 600f,
                canScrollBackward = true,
                canScrollForward = true,
            ),
        ).isWithin(0.001f).of(300f)
    }

    @Test
    fun scrubIndicator_staysAtTopWhenGalleryCannotScrollBackward() {
        assertThat(
            GridContinuityPolicy.scrubIndicatorY(
                firstVisibleIndex = 0,
                visibleItemCount = 30,
                itemCount = 100,
                trackHeightPx = 400f,
                canScrollBackward = false,
                canScrollForward = true,
            ),
        ).isEqualTo(0f)
    }

    @Test
    fun scrubIndicator_reachesBottomWhenLastViewportIsVisible() {
        assertThat(
            GridContinuityPolicy.scrubIndicatorY(
                firstVisibleIndex = 90,
                visibleItemCount = 10,
                itemCount = 100,
                trackHeightPx = 400f,
                canScrollBackward = true,
                canScrollForward = false,
            ),
        ).isEqualTo(400f)
    }

    @Test
    fun scrubIndicator_clampsStalePagingIndexAndFailsClosedOnInvalidTrack() {
        assertThat(
            GridContinuityPolicy.scrubIndicatorY(9999, 10, 100, 400f, true, true),
        ).isEqualTo(400f)
        assertThat(
            GridContinuityPolicy.scrubIndicatorY(50, 10, 100, Float.NaN, true, true),
        ).isEqualTo(0f)
        assertThat(
            GridContinuityPolicy.scrubIndicatorY(0, 0, 0, 400f, false, false),
        ).isEqualTo(0f)
        assertThat(
            GridContinuityPolicy.scrubIndicatorY(0, 10, 10, 400f, false, false),
        ).isEqualTo(0f)
    }

    @Test
    fun scrubTarget_invertsPassiveViewportThumbWithoutJump() {
        val thumbY = GridContinuityPolicy.scrubIndicatorY(
            firstVisibleIndex = 450,
            visibleItemCount = 100,
            itemCount = 1000,
            trackHeightPx = 600f,
            canScrollBackward = true,
            canScrollForward = true,
        )
        assertThat(thumbY).isWithin(0.001f).of(300f)
        assertThat(
            GridContinuityPolicy.scrubTargetIndex(
                rawY = thumbY,
                trackHeightPx = 600f,
                itemCount = 1000,
                visibleItemCount = 100,
            ),
        ).isEqualTo(450)
    }

    @Test
    fun scrubTarget_viewportAwareMappingStillReachesTrueEnds() {
        assertThat(GridContinuityPolicy.scrubTargetIndex(0f, 600f, 1000, 100)).isEqualTo(0)
        assertThat(GridContinuityPolicy.scrubTargetIndex(600f, 600f, 1000, 100)).isEqualTo(999)
    }

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
    fun scrubGestureTargetTracker_repeatsTargetOnNewGestureAfterOrdinaryScroll() {
        val tracker = ScrubGestureTargetTracker()
        tracker.reset() // First thumb gesture.
        assertThat(tracker.shouldDispatch(57)).isTrue()
        assertThat(tracker.shouldDispatch(57)).isFalse() // Same gesture, same request.
        assertThat(tracker.shouldDispatch(58)).isTrue()
        assertThat(tracker.shouldDispatch(58)).isFalse()

        // Grid is scrolled normally between gestures; photo count stays constant.
        tracker.reset() // The next onDragStart must request target 58 again.
        assertThat(tracker.shouldDispatch(58)).isTrue()
        assertThat(tracker.shouldDispatch(58)).isFalse()
    }

    @Test
    fun scrubGestureTargetTracker_resetAlsoHandlesPagingCountChange() {
        val tracker = ScrubGestureTargetTracker()
        assertThat(tracker.shouldDispatch(10)).isTrue()
        assertThat(tracker.shouldDispatch(10)).isFalse()
        tracker.reset() // Called when a new paging item count arrives.
        assertThat(tracker.shouldDispatch(10)).isTrue()
    }

    @Test
    fun scrubTarget_nonFinitePointerAndTrackFailClosedWithoutRounding() {
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { invalid ->
            assertThat(GridContinuityPolicy.scrubTargetIndex(invalid, 500f, 10)).isNull()
            assertThat(GridContinuityPolicy.scrubTargetIndex(250f, invalid, 10)).isNull()
        }
    }

    @Test
    fun scrubTarget_emptyOrInvalidTrackFailsClosed() {
        assertThat(GridContinuityPolicy.scrubTargetIndex(10f, 100f, 0)).isNull()
        assertThat(GridContinuityPolicy.scrubTargetIndex(10f, 0f, 10)).isNull()
    }
}
