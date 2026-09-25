package com.photobook.app.ui.component

import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider

/**
 * Makes the containing Compose Dialog resize its content area for the IME.
 *
 * Photo search owns no additional IME padding when this effect is installed: the dialog window is
 * the single inset owner, so header/photo/footer receive the resized constraints exactly once.
 */
@Composable
fun PhotoTextSearchDialogImeResizeEffect() {
    val composeView = LocalView.current

    DisposableEffect(composeView) {
        val window = findDialogWindow(composeView)
        val previousSoftInputMode = window?.attributes?.softInputMode

        window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        onDispose {
            if (window != null && previousSoftInputMode != null) {
                window.setSoftInputMode(previousSoftInputMode)
            }
        }
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
