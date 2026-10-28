package com.photobook.app.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ViewerTaggingSchedulingPolicyTest {

    @Test
    fun ordinaryViewer_allowsFocusedTagging() {
        assertTrue(shouldEnqueueFocusedTagging(reelsEnabled = false))
    }

    @Test
    fun reelsViewer_defersPerPageFocusedTagging() {
        assertFalse(shouldEnqueueFocusedTagging(reelsEnabled = true))
    }
}
