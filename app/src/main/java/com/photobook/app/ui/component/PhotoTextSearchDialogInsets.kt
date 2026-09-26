package com.photobook.app.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.max

/**
 * Returns one authoritative padding rectangle for a full-screen photo-search dialog.
 *
 * Insets must be resolved before any inset-padding modifier consumes them. Chaining
 * systemBarsPadding() and imePadding() can under-reserve the IME in edge-to-edge Dialog windows
 * because the second modifier sees already-consumed insets. We instead read both raw inset sets,
 * take the safe drawing edges for left/top/right, and reserve whichever bottom obstruction is
 * larger: safe drawing or the IME.
 */
@Composable
fun photoTextSearchDialogPadding(): PaddingValues {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeDrawing = WindowInsets.safeDrawing
    val ime = WindowInsets.ime

    val leftPx = safeDrawing.getLeft(density, layoutDirection)
    val topPx = safeDrawing.getTop(density)
    val rightPx = safeDrawing.getRight(density, layoutDirection)
    val safeBottomPx = safeDrawing.getBottom(density)
    val imeBottomPx = ime.getBottom(density)
    val bottomPx = max(safeBottomPx, imeBottomPx)

    val left = with(density) { leftPx.toDp() }
    val top = with(density) { topPx.toDp() }
    val right = with(density) { rightPx.toDp() }
    val bottom = with(density) { bottomPx.toDp() }

    return if (layoutDirection == LayoutDirection.Ltr) {
        PaddingValues(start = left, top = top, end = right, bottom = bottom)
    } else {
        PaddingValues(start = right, top = top, end = left, bottom = bottom)
    }
}