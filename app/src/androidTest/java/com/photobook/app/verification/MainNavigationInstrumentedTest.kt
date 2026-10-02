package com.photobook.app.verification

import android.content.Intent
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
        device.executeShellCommand("pm grant ${targetContext.packageName} android.permission.READ_MEDIA_IMAGES")
        device.executeShellCommand("pm grant ${targetContext.packageName} android.permission.ACCESS_MEDIA_LOCATION")
        val launchIntent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        assertNotNull(launchIntent)
        targetContext.startActivity(launchIntent)
        device.wait(Until.hasObject(By.text("Photos")), 10_000)
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
        assertTrue(device.wait(Until.hasObject(By.text("Screenshots")), 5_000))

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
}
