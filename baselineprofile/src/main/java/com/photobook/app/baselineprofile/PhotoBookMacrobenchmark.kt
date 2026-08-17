package com.photobook.app.baselineprofile

import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.MemoryUsageMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.Locale
import kotlin.math.ceil
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@LargeTest
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@OptIn(ExperimentalMetricApi::class)
class PhotoBookMacrobenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Before
    fun prepareScaleFixture() {
        BenchmarkMediaSeeder.ensureSeeded()
    }

    @Test
    fun a_initialIndexReadyLatency() {
        val librarySize = BenchmarkMediaSeeder.requestedLibrarySize()
        var elapsedMs = -1L

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = 1,
            startupMode = StartupMode.COLD,
            setupBlock = {
                device.executeShellCommand("pm clear $TARGET_PACKAGE")
                grantRuntimePermissions(device)
                pressHome()
            },
        ) {
            val startMs = SystemClock.elapsedRealtime()
            startActivityAndWait()
            requireAppWindow()
            requireReadyLibrary()
            elapsedMs = SystemClock.elapsedRealtime() - startMs
        }

        check(elapsedMs > 0L) { "Initial index-ready latency was not captured" }
        val photosPerSecond = librarySize * 1_000.0 / elapsedMs.toDouble()
        println(
            String.format(
                Locale.US,
                "[phase3] indexReady librarySize=%d elapsedMs=%d photosPerSecond=%.2f",
                librarySize,
                elapsedMs,
                photosPerSecond,
            ),
        )
    }

    @Test
    fun b_coldStartup() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = STARTUP_ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                prepareReadyAppForMeasurement()
            },
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun c_warmStartup() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = STARTUP_ITERATIONS,
            startupMode = StartupMode.WARM,
            setupBlock = {
                prepareReadyAppForMeasurement()
            },
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun d_firstVisibleThumbnailLatency() {
        val samplesMs = mutableListOf<Long>()

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = STARTUP_ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                prepareReadyAppForMeasurement()
            },
        ) {
            val startMs = SystemClock.elapsedRealtime()
            startActivityAndWait()
            requireAppWindow()
            check(waitForVisiblePhotoThumbnail()) {
                "No clickable photo thumbnail became visible after cold start"
            }
            samplesMs += SystemClock.elapsedRealtime() - startMs
        }

        val sorted = samplesMs.sorted()
        println(
            "[phase3] firstThumbnail " +
                "librarySize=${BenchmarkMediaSeeder.requestedLibrarySize()} " +
                "p50Ms=${percentile(sorted, 50)} " +
                "p95Ms=${percentile(sorted, 95)} " +
                "maxMs=${sorted.last()}",
        )
    }

    @Test
    fun e_gridScrollFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = INTERACTION_ITERATIONS,
            startupMode = null,
            setupBlock = {
                prepareReadyAppForMeasurement(pressHomeAfterReady = false)
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
    fun f_searchTypingAndResultsFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = INTERACTION_ITERATIONS,
            startupMode = null,
            setupBlock = {
                prepareReadyAppForMeasurement(pressHomeAfterReady = false)
                val search = device.findObject(By.clazz("android.widget.EditText"))
                    ?: error("PhotoBook search EditText was not exposed to UI Automator")
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
    fun g_reelsVerticalSwipeFrameTiming() {
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                FrameTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = INTERACTION_ITERATIONS,
            startupMode = null,
            setupBlock = {
                prepareReadyAppForMeasurement(pressHomeAfterReady = false)
                val reelsToggle = requireReadyLibrary()
                reelsToggle.click()
                device.waitForIdle()

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

    private fun MacrobenchmarkScope.prepareReadyAppForMeasurement(
        pressHomeAfterReady: Boolean = true,
    ) {
        grantRuntimePermissions(device)
        pressHome()
        startActivityAndWait()
        requireAppWindow()
        requireReadyLibrary()
        if (pressHomeAfterReady) {
            pressHome()
        }
    }

    private fun MacrobenchmarkScope.requireAppWindow() {
        val visible = device.wait(
            Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)),
            UI_TIMEOUT_MS,
        )
        check(visible) { "PhotoBook window did not become visible" }
    }

    private fun MacrobenchmarkScope.requireReadyLibrary(): UiObject2 {
        val deadlineMs = SystemClock.elapsedRealtime() + BenchmarkMediaSeeder.readyTimeoutMs()
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            val action = clickableAncestor(device.findObject(By.text(REELS_ACTION_TEXT)))
            if (action?.isEnabled == true) {
                return action
            }
            device.waitForIdle()
            SystemClock.sleep(100)
        }
        error(
            "PhotoBook did not reach its ready state before benchmark measurement; " +
                "librarySize=${BenchmarkMediaSeeder.requestedLibrarySize()}",
        )
    }

    private fun MacrobenchmarkScope.waitForVisiblePhotoThumbnail(): Boolean {
        val deadlineMs = SystemClock.elapsedRealtime() + THUMBNAIL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            val visiblePhoto = device.findObjects(By.clickable(true)).any { node ->
                !node.contentDescription.isNullOrBlank()
            }
            if (visiblePhoto) return true
            device.waitForIdle()
            SystemClock.sleep(50)
        }
        return false
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

    private fun clickableAncestor(initial: UiObject2?): UiObject2? {
        var node = initial
        repeat(MAX_ANCESTOR_DEPTH) {
            val current = node ?: return null
            if (current.isClickable) return current
            node = current.parent
        }
        return null
    }

    private fun percentile(sortedValues: List<Long>, percentile: Int): Long {
        require(sortedValues.isNotEmpty())
        val nearestRank = ceil((percentile / 100.0) * sortedValues.size).toInt()
        val index = (nearestRank - 1).coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }

    companion object {
        private const val TARGET_PACKAGE = "com.photobook.app"
        private const val REELS_ACTION_TEXT = "Reel Browsing"
        private const val STARTUP_ITERATIONS = 10
        private const val INTERACTION_ITERATIONS = 5
        private const val UI_TIMEOUT_MS = 8_000L
        private const val THUMBNAIL_TIMEOUT_MS = 60_000L
        private const val MAX_ANCESTOR_DEPTH = 4
    }
}
