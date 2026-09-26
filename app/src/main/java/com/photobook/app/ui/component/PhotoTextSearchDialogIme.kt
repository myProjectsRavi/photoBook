package com.photobook.app.ui.component

import android.graphics.Rect
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Immutable
data class PhotoTextSearchDialogImeState(
    val isVisible: Boolean = false,
    val bottomPadding: Dp = 0.dp,
)

private data class PlatformImeSnapshot(
    val isVisible: Boolean,
    val bottomInsetPx: Int,
)

/**
 * Tracks the IME from the actual Compose dialog window.
 *
 * Full-screen dialogs on edge-to-edge Android versions can keep their root view at full height
 * while the keyboard overlays it. In that case neither legacy adjustResize nor Compose-only
 * imePadding is sufficient on every device. This state reads the platform root insets directly
 * and falls back to the visible display frame, so search chrome can reserve the real obscured
 * region exactly once.
 */
@Composable
fun rememberPhotoTextSearchDialogImeState(): PhotoTextSearchDialogImeState {
    val view = LocalView.current
    val density = LocalDensity.current
    var snapshot by remember(view) {
        mutableStateOf(PlatformImeSnapshot(isVisible = false, bottomInsetPx = 0))
    }

    DisposableEffect(view) {
        val window = findDialogWindow(view)
        val previousSoftInputMode = window?.attributes?.softInputMode

        // Keep one deterministic owner for IME avoidance. The Compose layout below reserves the
        // measured platform inset, so the window itself must not independently resize or pan.
        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        fun updateImeSnapshot() {
            val next = readPlatformImeSnapshot(view)
            if (next != snapshot) {
                snapshot = next
            }
        }

        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            updateImeSnapshot()
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        view.post { updateImeSnapshot() }

        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
            if (window != null && previousSoftInputMode != null) {
                window.setSoftInputMode(previousSoftInputMode)
            }
        }
    }

    return PhotoTextSearchDialogImeState(
        isVisible = snapshot.isVisible,
        bottomPadding = with(density) { snapshot.bottomInsetPx.toDp() },
    )
}

private fun readPlatformImeSnapshot(view: View): PlatformImeSnapshot {
    val insets = ViewCompat.getRootWindowInsets(view)
    val imeType = WindowInsetsCompat.Type.ime()
    val platformImeVisible = insets?.isVisible(imeType) == true
    val platformImeBottomInset = insets?.getInsets(imeType)?.bottom ?: 0

    // WindowInsets bottom values are local to the dialog window. On edge-to-edge/full-screen
    // dialogs that window can already have consumed status/navigation insets, so applying the raw
    // value as full-screen padding can under-reserve the keyboard by exactly those consumed bars.
    //
    // Compute the occluded portion in one coordinate system instead: both values below are screen
    // coordinates. This also handles dialogs whose root view is offset or shorter than the display.
    val visibleFrame = Rect()
    view.getWindowVisibleDisplayFrame(visibleFrame)
    val location = IntArray(2)
    view.getLocationOnScreen(location)
    val viewBottomOnScreen = location[1] + view.height
    val resolvedBottomPadding = resolvePhotoTextSearchImeOcclusionPx(
        viewHeightPx = view.height,
        viewBottomOnScreenPx = viewBottomOnScreen,
        visibleFrameBottomOnScreenPx = visibleFrame.bottom,
        platformImeVisible = platformImeVisible,
        platformImeBottomInsetPx = platformImeBottomInset,
    )

    return PlatformImeSnapshot(
        isVisible = resolvedBottomPadding > 0,
        bottomInsetPx = resolvedBottomPadding,
    )
}

/**
 * Returns the number of pixels of this dialog view that are actually obscured by the IME.
 *
 * [visibleFrameBottomOnScreenPx] and [viewBottomOnScreenPx] deliberately use screen coordinates.
 * The platform IME inset is only a fallback for devices where the visible frame does not move.
 */
internal fun resolvePhotoTextSearchImeOcclusionPx(
    viewHeightPx: Int,
    viewBottomOnScreenPx: Int,
    visibleFrameBottomOnScreenPx: Int,
    platformImeVisible: Boolean,
    platformImeBottomInsetPx: Int,
): Int {
    if (viewHeightPx <= 0) return 0

    val frameOcclusion = (viewBottomOnScreenPx - visibleFrameBottomOnScreenPx)
        .coerceIn(0, viewHeightPx)
    val minimumFallbackImeHeight = (viewHeightPx * 0.15f).toInt()

    return when {
        // The visible frame is the authoritative geometry because it already accounts for this
        // dialog's actual on-screen origin and any status/navigation insets consumed upstream.
        platformImeVisible && frameOcclusion > 0 -> frameOcclusion
        platformImeVisible -> platformImeBottomInsetPx.coerceIn(0, viewHeightPx)
        // Some OEMs publish the visible-frame change one layout before Type.ime() becomes visible.
        frameOcclusion > minimumFallbackImeHeight -> frameOcclusion
        else -> 0
    }
}

private fun findDialogWindow(start: View): android.view.Window? {
    var current: View? = start
    while (current != null) {
        if (current is DialogWindowProvider) {
            return current.window
        }
        current = current.parent as? View
    }
    return null
}