package com.photobook.app.baselineprofile

import android.os.Build
import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun generate() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        grantRuntimePermissions(device)

        baselineProfileRule.collect(
            packageName = TARGET_PACKAGE,
            includeInStartupProfile = true,
        ) {
            pressHome()
            startActivityAndWait()
            device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), 5_000)

            // Exercise cold start + feed scroll.
            repeat(4) {
                device.swipe(540, 1800, 540, 420, 24)
                device.waitForIdle()
            }

            // Exercise the search path.
            device.findObject(By.textContains("Search"))?.click()
            device.waitForIdle()
            device.pressKeyCode(KeyEvent.KEYCODE_T)
            device.pressKeyCode(KeyEvent.KEYCODE_O)
            device.pressKeyCode(KeyEvent.KEYCODE_D)
            device.pressKeyCode(KeyEvent.KEYCODE_A)
            device.pressKeyCode(KeyEvent.KEYCODE_Y)
            device.pressEnter()
            device.waitForIdle()

            repeat(3) {
                device.swipe(540, 1700, 540, 460, 20)
                device.waitForIdle()
            }
        }
    }

    private fun grantRuntimePermissions(device: UiDevice) {
        val permissions = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add("android.permission.READ_MEDIA_IMAGES")
            }
            add("android.permission.ACCESS_MEDIA_LOCATION")
            add("android.permission.READ_EXTERNAL_STORAGE")
        }
        permissions.forEach { permission ->
            runCatching {
                device.executeShellCommand("pm grant $TARGET_PACKAGE $permission")
            }
        }
    }

    companion object {
        private const val TARGET_PACKAGE = "com.photobook.app"
    }
}
