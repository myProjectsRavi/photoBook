package com.photobook.app.ui.component

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.max

@Immutable
internal data class PhotoTextSearchDialogOcclusionPx(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
)

/**
 * Returns one authoritative safe rectangle for the full-screen search dialog.
 *
 * The visible display frame and this Compose view are both measured in screen coordinates. That
 * matters for Dialog windows: an IME inset reported in window-local coordinates can differ from
 * the actual screen overlap when system bars have already shifted or inset the dialog.
 *
 * No systemBarsPadding()/imePadding() modifier is used alongside this function. This is the single
 * inset owner for Viewer, Reels, and Vault search content.
 */
@Composable
fun photoTextSearchDialogPadding(): PaddingValues {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    var occlusion by remember(view) {
        mutableStateOf(PhotoTextSearchDialogOcclusionPx())
    }

    DisposableEffect(view) {
        fun update() {
            if (view.width <= 0 || view.height <= 0) return

            val visibleFrame = Rect()
            view.getWindowVisibleDisplayFrame(visibleFrame)
            val location = IntArray(2)
            view.getLocationOnScreen(location)

            val rootInsets = ViewCompat.getRootWindowInsets(view)
            val safeType =
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            // Navigation bars can become not-visible/reparented while the IME owns the bottom
            // system area. The stable bar geometry is still needed because Type.ime() in this
            // dialog reports only the keyboard portion above that bar.
            val safeInsets = rootInsets?.getInsetsIgnoringVisibility(safeType)
            val imeType = WindowInsetsCompat.Type.ime()
            val imeVisible = rootInsets?.isVisible(imeType) == true
            val imeInsets = rootInsets?.getInsets(imeType)

            val next = resolvePhotoTextSearchDialogOcclusionPx(
                viewLeftOnScreen = location[0],
                viewTopOnScreen = location[1],
                viewWidth = view.width,
                viewHeight = view.height,
                visibleFrameLeftOnScreen = visibleFrame.left,
                visibleFrameTopOnScreen = visibleFrame.top,
                visibleFrameRightOnScreen = visibleFrame.right,
                visibleFrameBottomOnScreen = visibleFrame.bottom,
                visibleFrameUsable = !visibleFrame.isEmpty,
                fallbackSafeLeft = safeInsets?.left ?: 0,
                fallbackSafeTop = safeInsets?.top ?: 0,
                fallbackSafeRight = safeInsets?.right ?: 0,
                fallbackSafeBottom = safeInsets?.bottom ?: 0,
                fallbackImeBottom = imeInsets?.bottom ?: 0,
                imeVisible = imeVisible,
            )
            if (next != occlusion) {
                occlusion = next
            }
        }

        val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener { update() }
        val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            update()
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        view.addOnLayoutChangeListener(layoutChangeListener)
        view.post { update() }

        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
            }
            view.removeOnLayoutChangeListener(layoutChangeListener)
        }
    }

    val left = with(density) { occlusion.left.toDp() }
    val top = with(density) { occlusion.top.toDp() }
    val right = with(density) { occlusion.right.toDp() }
    val bottom = with(density) { occlusion.bottom.toDp() }

    return if (layoutDirection == LayoutDirection.Ltr) {
        PaddingValues(start = left, top = top, end = right, bottom = bottom)
    } else {
        PaddingValues(start = right, top = top, end = left, bottom = bottom)
    }
}

/**
 * Resolves the part of a dialog view that is not actually visible on screen.
 *
 * The visible frame is authoritative when available because it already reflects the dialog's real
 * screen origin and any system bars consumed before Compose. Root-window insets are fallback data
 * only. If an OEM reports the IME visible without shrinking the visible frame, the IME fallback is
 * used for the bottom edge.
 */
internal fun resolvePhotoTextSearchDialogOcclusionPx(
    viewLeftOnScreen: Int,
    viewTopOnScreen: Int,
    viewWidth: Int,
    viewHeight: Int,
    visibleFrameLeftOnScreen: Int,
    visibleFrameTopOnScreen: Int,
    visibleFrameRightOnScreen: Int,
    visibleFrameBottomOnScreen: Int,
    visibleFrameUsable: Boolean,
    fallbackSafeLeft: Int,
    fallbackSafeTop: Int,
    fallbackSafeRight: Int,
    fallbackSafeBottom: Int,
    fallbackImeBottom: Int,
    imeVisible: Boolean,
): PhotoTextSearchDialogOcclusionPx {
    if (viewWidth <= 0 || viewHeight <= 0) {
        return PhotoTextSearchDialogOcclusionPx()
    }

    val stableImeBottom = if (imeVisible && fallbackImeBottom > 0) {
        (fallbackImeBottom + fallbackSafeBottom).coerceIn(0, viewHeight)
    } else {
        fallbackSafeBottom.coerceIn(0, viewHeight)
    }
    val fallback = PhotoTextSearchDialogOcclusionPx(
        left = fallbackSafeLeft.coerceIn(0, viewWidth),
        top = fallbackSafeTop.coerceIn(0, viewHeight),
        right = fallbackSafeRight.coerceIn(0, viewWidth),
        bottom = stableImeBottom,
    )
    if (!visibleFrameUsable) return fallback

    val viewRightOnScreen = viewLeftOnScreen + viewWidth
    val viewBottomOnScreen = viewTopOnScreen + viewHeight
    val fromFrame = PhotoTextSearchDialogOcclusionPx(
        left = (visibleFrameLeftOnScreen - viewLeftOnScreen).coerceIn(0, viewWidth),
        top = (visibleFrameTopOnScreen - viewTopOnScreen).coerceIn(0, viewHeight),
        right = (viewRightOnScreen - visibleFrameRightOnScreen).coerceIn(0, viewWidth),
        bottom = (viewBottomOnScreen - visibleFrameBottomOnScreen).coerceIn(0, viewHeight),
    )

    // On API-35 full-screen Compose Dialogs the visible frame and Type.ime() can both stop at
    // the top of the bottom system-bar band. In that case they agree with each other while still
    // under-reporting the real screen occlusion. Add the stable bottom bar exactly in that case.
    //
    // If the visible frame already reports more occlusion than Type.ime(), it has already captured
    // the extra system-bar/window offset and remains authoritative (important for already-inset
    // dialog roots).
    val bottom = when {
        !imeVisible || fallbackImeBottom <= 0 -> fromFrame.bottom
        fromFrame.bottom > fallbackImeBottom -> fromFrame.bottom
        else -> max(fromFrame.bottom, stableImeBottom).coerceIn(0, viewHeight)
    }

    return fromFrame.copy(bottom = bottom)
}