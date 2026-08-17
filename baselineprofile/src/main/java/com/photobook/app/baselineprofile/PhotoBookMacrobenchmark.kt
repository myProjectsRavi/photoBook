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
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.util.Locale
import kotlin.math.abs
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

    /**
     * Measures the one-time first-library index build without wrapping the whole
     * operation in Perfetto. At 50k/100k a tens-of-minutes trace can itself fill
     * emulator storage and invalidate the measurement. The subsequent tests reuse
     * the persisted Room index produced here and measure steady-state behavior with
     * normal Macrobenchmark traces.
     */
    @Test
    fun a_initialIndexReadyLatency() {
        val librarySize = BenchmarkMediaSeeder.requestedLibrarySize()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        device.executeShellCommand("pm clear $TARGET_PACKAGE")
        grantRuntimePermissions(device)
        device.pressHome()

        val startMs = SystemClock.elapsedRealtime()
        launchTargetApp(device)
        requireAppWindow(device)
        requireReadyLibrary(device, indexReadyTimeoutMs(librarySize))
        val elapsedMs = SystemClock.elapsedRealtime() - startMs

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
        ensureSteadyStateIndex()
        forceStopTarget(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = STARTUP_ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                // StartupMode.COLD kills the target process between setupBlock and
                // measureBlock. Keep setup process-free; the persisted index was
                // prepared before entering measureRepeated.
                grantRuntimePermissions(device)
                pressHome()
            },
        ) {
            startActivityAndWait()
        }
    }

    @Test
    fun c_warmStartup() {
        ensureSteadyStateIndex()

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
        ensureSteadyStateIndex()
        forceStopTarget(UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()))
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
                grantRuntimePermissions(device)
                pressHome()
            },
        ) {
            val startMs = SystemClock.elapsedRealtime()
            startActivityAndWait()
            requireAppWindow(device)
            check(waitForVisiblePhotoThumbnail(device) != null) {
                "No benchmark photo thumbnail became visible after cold start"
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
        ensureSteadyStateIndex()

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
        ensureSteadyStateIndex()

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
        ensureSteadyStateIndex()
        var reelsModeEnabled = false

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

                // The home action enables vertical paging; it does not itself open
                // a viewer. Enable it once, then open an actual visible photo card.
                if (!reelsModeEnabled) {
                    requireReadyLibrary(device).click()
                    reelsModeEnabled = true
                    device.waitForIdle()
                }

                val thumbnail = waitForVisiblePhotoThumbnail(device)
                    ?: error("Reels benchmark requires a visible photo thumbnail")
                thumbnail.click()
                val viewerOpened = device.wait(Until.hasObject(By.desc("Close")), THUMBNAIL_TIMEOUT_MS)
                check(viewerOpened) {
                    "Reels benchmark could not open the seeded photo viewer"
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

    /**
     * Establishes the persistent steady-state index outside a Macrobenchmark trace.
     * This is intentionally idempotent: after a_initialIndexReadyLatency it should
     * return quickly, while an individually invoked test can still self-prepare.
     */
    private fun ensureSteadyStateIndex() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        grantRuntimePermissions(device)
        device.pressHome()
        launchTargetApp(device)
        requireAppWindow(device)
        requireReadyLibrary(
            device = device,
            timeoutMs = indexReadyTimeoutMs(BenchmarkMediaSeeder.requestedLibrarySize()),
        )
        device.pressHome()
    }

    private fun MacrobenchmarkScope.prepareReadyAppForMeasurement(
        pressHomeAfterReady: Boolean = true,
    ) {
        grantRuntimePermissions(device)
        pressHome()
        startActivityAndWait()
        requireAppWindow(device)

        // A previous interaction iteration may have left the photo viewer open.
        // Close it before locating home-screen controls for the next iteration.
        device.findObject(By.desc("Close"))?.let { close ->
            close.click()
            device.waitForIdle()
        }

        requireReadyLibrary(device)
        if (pressHomeAfterReady) {
            pressHome()
        }
    }

    private fun launchTargetApp(device: UiDevice) {
        val output = device.executeShellCommand(
            "am start -W -n $TARGET_PACKAGE/$TARGET_ACTIVITY",
        )
        check(!output.contains("Error", ignoreCase = true)) {
            "Unable to launch PhotoBook: $output"
        }
    }

    private fun requireAppWindow(device: UiDevice) {
        val visible = device.wait(
            Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)),
            UI_TIMEOUT_MS,
        )
        check(visible) { "PhotoBook window did not become visible" }
    }

    private fun requireReadyLibrary(
        device: UiDevice,
        timeoutMs: Long = BenchmarkMediaSeeder.readyTimeoutMs(),
    ): UiObject2 {
        val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs
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
                "librarySize=${BenchmarkMediaSeeder.requestedLibrarySize()} timeoutMs=$timeoutMs",
        )
    }

    private fun waitForVisiblePhotoThumbnail(device: UiDevice): UiObject2? {
        val deadlineMs = SystemClock.elapsedRealtime() + THUMBNAIL_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            val geometry = photoGridGeometry(device)
            val clickable = device.findObjects(By.clickable(true))
            val candidates = clickable.filter { node -> isPhotoGridNode(node, geometry) }
            val visiblePhoto = candidates.firstOrNull { candidate ->
                val centerY = candidate.visibleBounds.centerY()
                candidates.any { other ->
                    other !== candidate &&
                        abs(other.visibleBounds.centerY() - centerY) <= geometry.rowTolerancePx
                }
            }
            if (visiblePhoto != null) return visiblePhoto
            device.waitForIdle()
            SystemClock.sleep(50)
        }

        val geometry = photoGridGeometry(device)
        val bounds = device.findObjects(By.clickable(true))
            .take(12)
            .joinToString(separator = ";") { node ->
                val rect = node.visibleBounds
                "${rect.left},${rect.top},${rect.right},${rect.bottom}"
            }
        println(
            "[phase3] thumbnailSelector timeout " +
                "expectedCellPx=${geometry.expectedCellPx} " +
                "expectedCardPx=${geometry.expectedCardPx} clickableBounds=$bounds",
        )
        return null
    }

    private fun photoGridGeometry(device: UiDevice): PhotoGridGeometry {
        val density = InstrumentationRegistry.getInstrumentation()
            .targetContext.resources.displayMetrics.density
            .coerceAtLeast(1f)
        val horizontalInsetPx = RESULTS_HORIZONTAL_INSET_DP * density
        val resultsWidthPx = (device.displayWidth - horizontalInsetPx * 2f).coerceAtLeast(1f)
        val resultsWidthDp = resultsWidthPx / density
        val columns = when {
            resultsWidthDp >= 700f -> 5
            resultsWidthDp >= 520f -> 4
            else -> 3
        }
        val expectedCellPx = resultsWidthPx / columns
        val expectedCardPx = expectedCellPx - PHOTO_CARD_PADDING_DP * 2f * density
        val tolerancePx = PHOTO_SIZE_TOLERANCE_DP * density
        return PhotoGridGeometry(
            expectedCellPx = expectedCellPx,
            expectedCardPx = expectedCardPx,
            tolerancePx = tolerancePx,
            rowTolerancePx = (ROW_ALIGNMENT_TOLERANCE_DP * density).toInt().coerceAtLeast(1),
            minLeftPx = (horizontalInsetPx - tolerancePx).toInt(),
            maxRightPx = (device.displayWidth - horizontalInsetPx + tolerancePx).toInt(),
        )
    }

    private fun isPhotoGridNode(node: UiObject2, geometry: PhotoGridGeometry): Boolean {
        if (!node.isEnabled) return false
        val bounds = node.visibleBounds
        val width = bounds.width()
        val height = bounds.height()
        if (width <= 0 || height <= 0) return false
        if (bounds.left < geometry.minLeftPx || bounds.right > geometry.maxRightPx) return false
        if (abs(width - height) > geometry.tolerancePx) return false

        val matchesOuterCell =
            abs(width - geometry.expectedCellPx) <= geometry.tolerancePx &&
                abs(height - geometry.expectedCellPx) <= geometry.tolerancePx
        val matchesPaddedCard =
            abs(width - geometry.expectedCardPx) <= geometry.tolerancePx &&
                abs(height - geometry.expectedCardPx) <= geometry.tolerancePx
        return matchesOuterCell || matchesPaddedCard
    }

    private fun forceStopTarget(device: UiDevice) {
        device.executeShellCommand("am force-stop $TARGET_PACKAGE")
        val deadlineMs = SystemClock.elapsedRealtime() + PROCESS_STOP_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (device.executeShellCommand("pidof $TARGET_PACKAGE").trim().isEmpty()) return
            SystemClock.sleep(100)
        }
        error("PhotoBook process remained alive after benchmark force-stop precondition")
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

    private fun indexReadyTimeoutMs(librarySize: Int): Long = when (librarySize) {
        in 1..10_000 -> 4L * 60_000L
        in 10_001..50_000 -> 20L * 60_000L
        else -> 45L * 60_000L
    }

    private fun percentile(sortedValues: List<Long>, percentile: Int): Long {
        require(sortedValues.isNotEmpty())
        val nearestRank = ceil((percentile / 100.0) * sortedValues.size).toInt()
        val index = (nearestRank - 1).coerceIn(0, sortedValues.lastIndex)
        return sortedValues[index]
    }

    private data class PhotoGridGeometry(
        val expectedCellPx: Float,
        val expectedCardPx: Float,
        val tolerancePx: Float,
        val rowTolerancePx: Int,
        val minLeftPx: Int,
        val maxRightPx: Int,
    )

    companion object {
        private const val TARGET_PACKAGE = "com.photobook.app"
        private const val TARGET_ACTIVITY = ".MainActivity"
        private const val REELS_ACTION_TEXT = "Reel Browsing"
        private const val STARTUP_ITERATIONS = 10
        private const val INTERACTION_ITERATIONS = 5
        private const val UI_TIMEOUT_MS = 8_000L
        private const val THUMBNAIL_TIMEOUT_MS = 60_000L
        private const val PROCESS_STOP_TIMEOUT_MS = 5_000L
        private const val MAX_ANCESTOR_DEPTH = 4
        private const val RESULTS_HORIZONTAL_INSET_DP = 20f
        private const val PHOTO_CARD_PADDING_DP = 2f
        private const val PHOTO_SIZE_TOLERANCE_DP = 10f
        private const val ROW_ALIGNMENT_TOLERANCE_DP = 10f
    }
}
