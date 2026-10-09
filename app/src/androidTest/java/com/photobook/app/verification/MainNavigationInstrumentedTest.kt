package com.photobook.app.verification

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val targetContext = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun launchMainActivity() {
        val readPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "android.permission.READ_MEDIA_IMAGES"
        } else {
            "android.permission.READ_EXTERNAL_STORAGE"
        }
        val readGrant = device.executeShellCommand(
            "pm grant ${targetContext.packageName} $readPermission",
        )
        assertFalse("Photo permission grant failed: $readGrant", readGrant.contains("Exception"))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            device.executeShellCommand(
                "pm grant ${targetContext.packageName} android.permission.ACCESS_MEDIA_LOCATION",
            )
        }
        val launchIntent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
        assertNotNull(launchIntent)
        val component = launchIntent?.component
        assertNotNull(component)
        val launchOutput = device.executeShellCommand(
            "am start -W -f 0x10008000 -n ${component!!.flattenToShortString()}",
        )
        assertFalse(launchOutput.contains("Error", ignoreCase = true))
        assertTrue(device.wait(Until.hasObject(By.text("Photos")), 15_000))
    }


    @Test
    fun photosFirstShell_navigatesWithoutAutomaticImeOrProBadge() {
        assertTrue(device.hasObject(By.text("Photos")))
        assertTrue(device.hasObject(By.text("Albums")))
        assertTrue(device.hasObject(By.text("Tools")))
        assertFalse(device.hasObject(By.text("PRO")))

        val inputMethodBeforeNavigation = device.executeShellCommand("dumpsys input_method")
        assertFalse(inputMethodBeforeNavigation.contains("mInputShown=true"))

        device.findObject(By.text("Albums")).click()
        assertTrue(device.wait(Until.hasObject(By.text("No albums in current access")), 5_000))

        device.findObject(By.text("Tools")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Vault")), 5_000))
        val vault = device.findObject(By.text("Vault"))
        val toolsRowY = vault.visibleCenter.y
        device.swipe(
            device.displayWidth * 4 / 5,
            toolsRowY,
            device.displayWidth / 5,
            toolsRowY,
            20,
        )
        assertTrue(device.wait(Until.hasObject(By.text("Trash")), 5_000))

        device.findObject(By.text("Photos")).click()
        assertTrue(device.wait(Until.hasObject(By.text("Photos")), 5_000))

        val inputMethodAfterNavigation = device.executeShellCommand("dumpsys input_method")
        assertFalse(inputMethodAfterNavigation.contains("mInputShown=true"))
    }

    @Test
    fun mainShell_largeFont_keepsPrimaryNavigationReachable() {
        val originalFontScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            device.executeShellCommand("settings put system font_scale 2.0")
            assertTrue(
                device.executeShellCommand("settings get system font_scale")
                    .trim()
                    .toFloatOrNull() == 2.0f,
            )
            // Do not force-stop the target package: instrumentation is hosted in the same
            // package process and force-stop would terminate the test runner itself.
            // API 26/27 launcher may restart on font changes. Blocking shell HOME
            // can hang indefinitely; use UiAutomator with a bounded idle wait.
            // Keep the serialized HOME transition for API 28+ (API 33 race fix).
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                device.pressHome()
                device.waitForIdle(5_000)
            } else {
                val homeOutput = device.executeShellCommand(
                    "am start -W -a android.intent.action.MAIN -c android.intent.category.HOME",
                )
                assertFalse("Could not display HOME: $homeOutput", homeOutput.contains("Error", true))
                device.waitForIdle()
            }
            val launchIntent = targetContext.packageManager
                .getLaunchIntentForPackage(targetContext.packageName)
            assertNotNull(launchIntent)
            val component = launchIntent?.component
            assertNotNull(component)
            val launchOutput = device.executeShellCommand(
                "am start -W -f 0x10008000 -n ${component!!.flattenToShortString()}",
            )
            assertFalse(launchOutput.contains("Error", ignoreCase = true))

            // Older API emulators can recreate the activity slowly after a 200% font
            // configuration change. Keep the actual accessibility assertions, but wait
            // for the target window instead of assuming a 10-second launch.
            val photosVisible = device.wait(Until.hasObject(By.text("Photos")), 30_000)
            assertTrue(
                "Photos absent at 200% font; foreground=${device.currentPackageName}; " +
                    "launch=$launchOutput",
                photosVisible,
            )
            assertTrue(device.wait(Until.hasObject(By.text("Albums")), 10_000))
            assertTrue(device.wait(Until.hasObject(By.text("Tools")), 10_000))

            device.findObject(By.text("Albums")).click()
            assertTrue(device.wait(Until.hasObject(By.text("No albums in current access")), 5_000))
            device.findObject(By.text("Tools")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Vault")), 5_000))

            val window = device.executeShellCommand("dumpsys window")
            assertFalse(window.contains("mSystemUiVisibility=0x0"))
        } finally {
            val restored = originalFontScale.toFloatOrNull()?.toString() ?: "1.0"
            device.executeShellCommand("settings put system font_scale $restored")
        }
    }

}
