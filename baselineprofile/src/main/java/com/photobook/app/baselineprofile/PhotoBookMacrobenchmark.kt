package com.photobook.app.baselineprofile

import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
class PhotoBookMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = STARTUP_ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun warmStartup() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            iterations = STARTUP_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun gridScrollFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = INTERACTION_ITERATIONS,
            startupMode = null,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                requireAppWindow()
                requireReadyLibrary()
            },
        ) {
            repeat(8) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.82f).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.32f).toInt(),
                    14,
                )
            }
            device.waitForIdle()
        }
    }

    @Test
    fun searchTypingAndResultsFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = INTERACTION_ITERATIONS,
            startupMode = null,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                requireAppWindow()
                requireReadyLibrary()
                val search = device.findObject(By.textContains("Search"))
                    ?: error("Search field was not exposed to UI Automator")
                search.click()
                repeat(32) { device.pressKeyCode(KeyEvent.KEYCODE_DEL) }
                device.waitForIdle()
            },
        ) {
            device.pressKeyCode(KeyEvent.KEYCODE_T)
            device.pressKeyCode(KeyEvent.KEYCODE_O)
            device.pressKeyCode(KeyEvent.KEYCODE_D)
            device.pressKeyCode(KeyEvent.KEYCODE_A)
            device.pressKeyCode(KeyEvent.KEYCODE_Y)
            device.pressEnter()
            device.waitForIdle()
        }
    }

    @Test
    fun reelsVerticalSwipeFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = INTERACTION_ITERATIONS,
            startupMode = null,
            setupBlock = {
                pressHome()
                startActivityAndWait()
                requireAppWindow()
                val reelsToggle = requireReadyLibrary()
                reelsToggle.click()
                device.waitForIdle()

                // Phase-0 benchmark devices are seeded with deterministic media fixtures.
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.82f).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.55f).toInt(),
                    10,
                )
                device.click(
                    device.displayWidth / 6,
                    (device.displayHeight * 0.72f).toInt(),
                )
                val viewerOpened = device.wait(Until.hasObject(By.desc("Close")), UI_TIMEOUT_MS)
                check(viewerOpened) {
                    "Reels benchmark requires a seeded fixture library and a visible first photo"
                }
            },
        ) {
            repeat(12) {
                device.swipe(
                    device.displayWidth / 2,
                    (device.displayHeight * 0.78f).toInt(),
                    device.displayWidth / 2,
                    (device.displayHeight * 0.24f).toInt(),
                    12,
                )
            }
            device.waitForIdle()
        }
    }

    private fun MacrobenchmarkScope.requireAppWindow() {
        val visible = device.wait(
            Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)),
            UI_TIMEOUT_MS,
        )
        check(visible) { "PhotoBook window did not become visible" }
    }

    private fun MacrobenchmarkScope.requireReadyLibrary(): androidx.test.uiautomator.UiObject2 {
        val deadlineMs = SystemClock.elapsedRealtime() + READY_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            val action = device.findObject(By.text(REELS_ACTION_TEXT))
            if (action?.isEnabled == true) {
                return action
            }
            device.waitForIdle()
            SystemClock.sleep(100)
        }
        error(
            "PhotoBook did not reach its ready state before benchmark measurement; " +
                "the seeded fixture library may still be indexing",
        )
    }

    companion object {
        private const val TARGET_PACKAGE = "com.photobook.app"
        private const val REELS_ACTION_TEXT = "Reel Browsing"
        private const val STARTUP_ITERATIONS = 10
        private const val INTERACTION_ITERATIONS = 5
        private const val UI_TIMEOUT_MS = 8_000L
        private const val READY_TIMEOUT_MS = 30_000L
    }
}
