package com.photobook.app.verification

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.common.truth.Truth.assertThat
import org.junit.After
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
        device.executeShellCommand("am force-stop ${targetContext.packageName}")
        val launchIntent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
            ?.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        assertThat(launchIntent).isNotNull()
        targetContext.startActivity(launchIntent)
        device.wait(Until.hasObject(By.text("Photos")), 10_000)
    }

    @After
    fun stopApp() {
        device.executeShellCommand("am force-stop ${targetContext.packageName}")
    }

    @Test
    fun photosFirstShell_navigatesWithoutAutomaticImeOrProBadge() {
        assertThat(device.hasObject(By.text("Photos"))).isTrue()
        assertThat(device.hasObject(By.text("Albums"))).isTrue()
        assertThat(device.hasObject(By.text("Tools"))).isTrue()
        assertThat(device.hasObject(By.text("PRO"))).isFalse()

        val inputMethodBeforeNavigation = device.executeShellCommand("dumpsys input_method")
        assertThat(inputMethodBeforeNavigation).doesNotContain("mInputShown=true")

        device.findObject(By.text("Albums")).click()
        assertThat(device.wait(Until.hasObject(By.text("Screenshots")), 5_000)).isTrue()

        device.findObject(By.text("Tools")).click()
        assertThat(device.wait(Until.hasObject(By.text("Vault")), 5_000)).isTrue()
        assertThat(device.hasObject(By.text("Trash"))).isTrue()

        device.findObject(By.text("Photos")).click()
        assertThat(device.wait(Until.hasObject(By.text("Photos")), 5_000)).isTrue()

        val inputMethodAfterNavigation = device.executeShellCommand("dumpsys input_method")
        assertThat(inputMethodAfterNavigation).doesNotContain("mInputShown=true")
    }
}
