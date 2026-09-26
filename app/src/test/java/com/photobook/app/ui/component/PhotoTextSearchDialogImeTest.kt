package com.photobook.app.ui.component

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoTextSearchDialogImeTest {

    @Test
    fun visibleFrameWinsWhenRawImeInsetUndercountsConsumedSystemBars() {
        val padding = resolvePhotoTextSearchImeOcclusionPx(
            viewHeightPx = 1440,
            viewBottomOnScreenPx = 1440,
            visibleFrameBottomOnScreenPx = 720,
            platformImeVisible = true,
            platformImeBottomInsetPx = 594,
        )

        assertEquals(720, padding)
    }

    @Test
    fun actualDialogBottomPreventsDoubleCountingAlreadyConsumedInsets() {
        val padding = resolvePhotoTextSearchImeOcclusionPx(
            viewHeightPx = 1314,
            viewBottomOnScreenPx = 1368,
            visibleFrameBottomOnScreenPx = 720,
            platformImeVisible = true,
            platformImeBottomInsetPx = 720,
        )

        assertEquals(648, padding)
    }

    @Test
    fun navigationBarOnlyDoesNotMasqueradeAsIme() {
        val padding = resolvePhotoTextSearchImeOcclusionPx(
            viewHeightPx = 1440,
            viewBottomOnScreenPx = 1440,
            visibleFrameBottomOnScreenPx = 1368,
            platformImeVisible = false,
            platformImeBottomInsetPx = 0,
        )

        assertEquals(0, padding)
    }

    @Test
    fun rawImeInsetIsFallbackWhenVisibleFrameDoesNotMove() {
        val padding = resolvePhotoTextSearchImeOcclusionPx(
            viewHeightPx = 1200,
            viewBottomOnScreenPx = 1200,
            visibleFrameBottomOnScreenPx = 1200,
            platformImeVisible = true,
            platformImeBottomInsetPx = 510,
        )

        assertEquals(510, padding)
    }

    @Test
    fun resolvedPaddingIsClampedToDialogHeight() {
        val padding = resolvePhotoTextSearchImeOcclusionPx(
            viewHeightPx = 1000,
            viewBottomOnScreenPx = 1600,
            visibleFrameBottomOnScreenPx = 100,
            platformImeVisible = true,
            platformImeBottomInsetPx = 1400,
        )

        assertEquals(1000, padding)
    }
}
