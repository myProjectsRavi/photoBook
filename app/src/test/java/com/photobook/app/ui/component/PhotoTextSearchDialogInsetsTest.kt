package com.photobook.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoTextSearchDialogInsetsTest {

    @Test
    fun fullScreenDialogUsesActualVisibleFrameForImeOverlap() {
        val result = resolvePhotoTextSearchDialogOcclusionPx(
            viewLeftOnScreen = 0,
            viewTopOnScreen = 0,
            viewWidth = 1440,
            viewHeight = 2560,
            visibleFrameLeftOnScreen = 0,
            visibleFrameTopOnScreen = 63,
            visibleFrameRightOnScreen = 1440,
            visibleFrameBottomOnScreen = 1677,
            visibleFrameUsable = true,
            fallbackSafeLeft = 0,
            fallbackSafeTop = 63,
            fallbackSafeRight = 0,
            fallbackSafeBottom = 63,
            fallbackImeBottom = 758,
            imeVisible = true,
        )

        assertEquals(
            PhotoTextSearchDialogOcclusionPx(
                left = 0,
                top = 63,
                right = 0,
                bottom = 883,
            ),
            result,
        )
    }

    @Test
    fun alreadyInsetDialogDoesNotDoubleCountConsumedSystemBars() {
        val result = resolvePhotoTextSearchDialogOcclusionPx(
            viewLeftOnScreen = 0,
            viewTopOnScreen = 63,
            viewWidth = 1440,
            viewHeight = 2434,
            visibleFrameLeftOnScreen = 0,
            visibleFrameTopOnScreen = 63,
            visibleFrameRightOnScreen = 1440,
            visibleFrameBottomOnScreen = 1677,
            visibleFrameUsable = true,
            fallbackSafeLeft = 0,
            fallbackSafeTop = 63,
            fallbackSafeRight = 0,
            fallbackSafeBottom = 63,
            fallbackImeBottom = 758,
            imeVisible = true,
        )

        assertEquals(
            PhotoTextSearchDialogOcclusionPx(
                left = 0,
                top = 0,
                right = 0,
                bottom = 820,
            ),
            result,
        )
    }

    @Test
    fun hiddenImeStillRespectsVisibleSystemBarFrame() {
        val result = resolvePhotoTextSearchDialogOcclusionPx(
            viewLeftOnScreen = 0,
            viewTopOnScreen = 0,
            viewWidth = 1440,
            viewHeight = 2560,
            visibleFrameLeftOnScreen = 0,
            visibleFrameTopOnScreen = 63,
            visibleFrameRightOnScreen = 1440,
            visibleFrameBottomOnScreen = 2497,
            visibleFrameUsable = true,
            fallbackSafeLeft = 0,
            fallbackSafeTop = 63,
            fallbackSafeRight = 0,
            fallbackSafeBottom = 63,
            fallbackImeBottom = 0,
            imeVisible = false,
        )

        assertEquals(
            PhotoTextSearchDialogOcclusionPx(
                left = 0,
                top = 63,
                right = 0,
                bottom = 63,
            ),
            result,
        )
    }

    @Test
    fun imeInsetIsFallbackWhenVisibleFrameDoesNotExposeKeyboard() {
        val result = resolvePhotoTextSearchDialogOcclusionPx(
            viewLeftOnScreen = 0,
            viewTopOnScreen = 0,
            viewWidth = 1440,
            viewHeight = 2560,
            visibleFrameLeftOnScreen = 0,
            visibleFrameTopOnScreen = 63,
            visibleFrameRightOnScreen = 1440,
            visibleFrameBottomOnScreen = 2497,
            visibleFrameUsable = true,
            fallbackSafeLeft = 0,
            fallbackSafeTop = 63,
            fallbackSafeRight = 0,
            fallbackSafeBottom = 63,
            fallbackImeBottom = 883,
            imeVisible = true,
        )

        assertEquals(883, result.bottom)
        assertEquals(63, result.top)
    }

    @Test
    fun rootInsetsAreUsedOnlyWhenVisibleFrameIsUnavailable() {
        val result = resolvePhotoTextSearchDialogOcclusionPx(
            viewLeftOnScreen = 0,
            viewTopOnScreen = 0,
            viewWidth = 1000,
            viewHeight = 1600,
            visibleFrameLeftOnScreen = 0,
            visibleFrameTopOnScreen = 0,
            visibleFrameRightOnScreen = 0,
            visibleFrameBottomOnScreen = 0,
            visibleFrameUsable = false,
            fallbackSafeLeft = 10,
            fallbackSafeTop = 40,
            fallbackSafeRight = 20,
            fallbackSafeBottom = 50,
            fallbackImeBottom = 700,
            imeVisible = true,
        )

        assertEquals(
            PhotoTextSearchDialogOcclusionPx(
                left = 10,
                top = 40,
                right = 20,
                bottom = 700,
            ),
            result,
        )
    }
}
