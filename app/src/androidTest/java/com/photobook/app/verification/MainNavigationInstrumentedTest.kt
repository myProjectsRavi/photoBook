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
        // A launcher or system UI can also display a Photos label. Require the
        // PhotoBook-owned node, not just a matching string in another package.
        // Keep the foreground-package assertion and bounded 30-second wait.
        val photosVisible = device.wait(Until.hasObject(By.pkg(targetContext.packageName).text("Photos")), 30_000)
        val correctForeground = device.currentPackageName == targetContext.packageName
        assertTrue(
            "PhotoBook did not render Photos after launch; " +
                "foreground=${device.currentPackageName}; " +
                "component=${component.flattenToShortString()}; " +
                "amStart=${launchOutput.take(300)}" +
                if (!photosVisible || !correctForeground) "; ${failureOnlyWindowEvidence()}" else "",
            photosVisible && correctForeground,
        )
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
            // After HOME, only an app-owned Photos node proves the relaunch succeeded.
            val photosVisible = device.wait(Until.hasObject(By.pkg(targetContext.packageName).text("Photos")), 30_000)
            assertTrue(
                "Photos absent at 200% font; foreground=${device.currentPackageName}; " +
                    "launch=$launchOutput" +
                    if (!photosVisible) "; ${failureOnlyWindowEvidence()}" else "",
                photosVisible,
            )
            assertTrue(device.wait(Until.hasObject(By.text("Albums")), 10_000))
            assertTrue(device.wait(Until.hasObject(By.text("Tools")), 10_000))

            device.findObject(By.text("Albums")).click()
            // Album catalogs are asynchronous and may be populated by earlier
            // instrumentation. Assert the Albums destination's stable controls,
            // not an empty-state message that appears only with zero albums.
            assertTrue(
                "Albums destination did not render Memories at 200% font",
                device.wait(Until.hasObject(By.text("Memories")), 10_000),
            )
            assertTrue(
                "Albums memory controls missing at 200% font",
                device.hasObject(By.text("Shown on this device")) ||
                    device.hasObject(By.text("Hidden on this device")),
            )
            device.findObject(By.text("Tools")).click()
            assertTrue(device.wait(Until.hasObject(By.text("Vault")), 5_000))

            val window = device.executeShellCommand("dumpsys window")
            assertFalse(window.contains("mSystemUiVisibility=0x0"))
        } finally {
            val restored = originalFontScale.toFloatOrNull()?.toString() ?: "1.0"
            device.executeShellCommand("settings put system font_scale $restored")
        }
    }


    /**
     * Capture only categorical OS/window readiness evidence when a navigation assertion
     * is already failing. Never print raw dumpsys data or accessibility text: those can
     * contain filenames, media text, and third-party app/window titles.
     */
    private fun failureOnlyWindowEvidence(): String {
        val ownPackage = targetContext.packageName
        val window = device.executeShellCommand("dumpsys window windows")
        val activity = device.executeShellCommand("dumpsys activity activities")
        fun focusOwner(marker: String): String {
            val line = window.lineSequence().firstOrNull { it.contains(marker) } ?: return "not-reported"
            return when {
                line.contains(ownPackage) -> "photobook"
                line.contains("null", ignoreCase = true) -> "null"
                line.contains("android", ignoreCase = true) -> "system"
                else -> "other"
            }
        }
        val activityLines = activity.lineSequence()
            .filter { line ->
                line.contains("mResumedActivity") ||
                    line.contains("topResumedActivity") ||
                    line.contains("ResumedActivity")
            }
            .toList()
        return "focusEvidence(" +
            "currentFocus=${focusOwner("mCurrentFocus")}, " +
            "focusedApp=${focusOwner("mFocusedApp")}, " +
            "resumedPhotoBook=${activityLines.any { it.contains(ownPackage) }}, " +
            "resumedEntryCount=${activityLines.size}, " +
            "accessibilityHasPhotoBookNode=${device.hasObject(By.pkg(ownPackage))}, " +
            "accessibilityHasSystemNode=${device.hasObject(By.pkg("android"))}, " +
            "fontScale=${device.executeShellCommand("settings get system font_scale").trim().toFloatOrNull() ?: -1f}" +
            ")"
    }
}
