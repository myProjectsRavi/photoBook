package com.photobook.app.ui.component

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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

@Immutable
data class PhotoTextSearchDialogImeState(
    val isVisible: Boolean = false,
)

/**
 * Makes the containing full-screen Compose dialog resize for the IME.
 *
 * The dialog window is the single owner of keyboard avoidance. Search chrome and the photo
 * viewport therefore receive the same keyboard-reduced layout constraints, instead of trying to
 * translate a window-local IME inset into content padding a second time.
 */
@Composable
fun rememberPhotoTextSearchDialogImeState(): PhotoTextSearchDialogImeState {
    val view = LocalView.current
    var imeVisible by remember(view) { mutableStateOf(false) }

    DisposableEffect(view) {
        val window = findDialogWindow(view)
        val previousSoftInputMode = window?.attributes?.softInputMode

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        fun updateImeVisibility() {
            val insets = ViewCompat.getRootWindowInsets(view)
            val imeType = WindowInsetsCompat.Type.ime()
            val navigationType = WindowInsetsCompat.Type.navigationBars()
            val imeBottom = insets?.getInsets(imeType)?.bottom ?: 0
            val navigationBottom = insets?.getInsets(navigationType)?.bottom ?: 0
            val nextVisible =
                insets?.isVisible(imeType) == true ||
                    imeBottom > navigationBottom
            if (nextVisible != imeVisible) {
                imeVisible = nextVisible
            }
        }

        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            updateImeVisibility()
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        view.post { updateImeVisibility() }

        onDispose {
            if (view.viewTreeObserver.isAlive) {
                view.viewTreeObserver.removeOnGlobalLayoutListener(listener)
            }
            if (window != null && previousSoftInputMode != null) {
                window.setSoftInputMode(previousSoftInputMode)
            }
        }
    }

    return PhotoTextSearchDialogImeState(isVisible = imeVisible)
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
