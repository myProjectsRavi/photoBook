package com.photobook.app.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerWindowRecenterPolicyTest {
    private fun recenter(
        index: Int,
        size: Int = 51,
        atStart: Boolean = false,
        atEnd: Boolean = false,
    ) = shouldRecentreViewerWindow(
        currentIndex = index,
        windowSize = size,
        atVisibleStart = atStart,
        atVisibleEnd = atEnd,
        threshold = 12,
    )

    @Test
    fun anchoredStartDoesNotRebuildOnEveryEarlySwipe() {
        for (index in 0..12) assertFalse(recenter(index, atStart = true))
        assertTrue(recenter(38, atStart = true))
    }

    @Test
    fun anchoredEndDoesNotRebuildOnEveryLateSwipe() {
        for (index in 38..50) assertFalse(recenter(index, atEnd = true))
        assertTrue(recenter(12, atEnd = true))
    }

    @Test
    fun middleWindowRecentersAtEitherEdgeOnly() {
        assertTrue(recenter(12, size = 101))
        assertTrue(recenter(88, size = 101))
        assertFalse(recenter(50, size = 101))
    }

    @Test
    fun smallOrFullyBoundedWindowDoesNotRecenter() {
        assertFalse(recenter(0, size = 25))
        assertFalse(recenter(24, size = 25))
        assertFalse(recenter(0, atStart = true, atEnd = true))
        assertFalse(recenter(50, atStart = true, atEnd = true))
        assertFalse(recenter(-1))
    }
}
