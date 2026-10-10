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
import org.junit.Assume
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

    private var defaultImePackage: String? = null
    private var defaultImeResolved = false

    @Before
    fun prepareScaleFixture() {
        // Once readiness has demonstrably failed in this instrumentation process, do not
        // spend another 4-45 minutes repeating the same failed prerequisite in six metrics.
        // JUnit reports skipped follow-ups and the existing strict XML gate still fails.
        Assume.assumeFalse("Index readiness failed earlier in this run", readinessFailed)
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
        // StartupMode.COLD owns target-process termination for every measured iteration.

        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(
                StartupTimingMetric(),
                MemoryUsageMetric(MemoryUsageMetric.Mode.Max),
            ),
            iterations = STARTUP_ITERATIONS,
            startupMode = StartupMode.COLD,
            setupBlock = {
                // Permission/package work can race a background process restart after very large
                // libraries. Cold startup requires the target to be stably stopped immediately
                // before Macrobenchmark begins the measured launch.
                grantRuntimePermissions(device)
                pressHome()
                ensureTargetStopped(device)
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
        // StartupMode.COLD owns target-process termination for every measured iteration.
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
                ensureTargetStopped(device)
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
        var reelsThumbnailCenter: TapPoint? = null
        var reelsPagerBounds: android.graphics.Rect? = null

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
                    enableReelsMode(device)
                    reelsModeEnabled = true
                    device.waitForIdle()
                }

                val thumbnailCenter = reelsThumbnailCenter
                    ?: waitForVisiblePhotoThumbnail(device)
                    ?: error("Reels benchmark requires a visible photo thumbnail")
                reelsThumbnailCenter = thumbnailCenter
                device.click(thumbnailCenter.x, thumbnailCenter.y)
                val viewerOpened = device.wait(Until.hasObject(By.desc("Close")), THUMBNAIL_TIMEOUT_MS)
                check(viewerOpened && device.hasObject(By.pkg(TARGET_PACKAGE))) {
                    "Reels benchmark could not open the seeded PhotoBook viewer"
                }
                // The page counter exists in both viewer orientations. Do not certify
                // vertical Reels gestures against a horizontal viewer by accident.
                val pager = device.wait(
                    Until.findObject(By.res(REELS_PAGER_TEST_TAG)),
                    UI_TIMEOUT_MS,
                ) ?: error("Vertical Reels pager is absent after enabling Reel Browsing")
                val bounds = pager.visibleBounds
                check(bounds.width() > 0 && bounds.height() > device.displayHeight / 3) {
                    "Vertical Reels pager has invalid visible bounds: $bounds"
                }
                reelsPagerBounds = android.graphics.Rect(bounds)
                val (page, count) = requireViewerPageCounter(device)
                check(count - page >= REELS_MEASURED_SWIPES) {
                    "Reels benchmark needs $REELS_MEASURED_SWIPES forward pages, found $page / $count"
                }
            },
        ) {
            var currentPage = requireViewerPageCounter(device).first
            val bounds = reelsPagerBounds ?: error("Reels pager bounds were not captured")
            repeat(REELS_MEASURED_SWIPES) { swipeIndex ->
                // Use the real pager viewport, not full-screen coordinates, and
                // move beyond half its height to cross Compose's snap threshold.
                // Stay clear of the overlaid header and bottom action controls.
                device.swipe(
                    bounds.centerX(),
                    bounds.top + (bounds.height() * 0.72f).toInt(),
                    bounds.centerX(),
                    bounds.top + (bounds.height() * 0.12f).toInt(),
                    16,
                )
                val nextPage = awaitViewerPageAdvance(device, currentPage)
                check(nextPage > currentPage) {
                    "Reels viewer did not advance after swipe ${swipeIndex + 1}: " +
                        "page=$currentPage; foreground=${device.currentPackageName}; " +
                        "scrollableNodes=${device.findObjects(By.scrollable(true)).size}"
                }
                currentPage = nextPage
                // The page counter can advance before the Compose pager finishes
                // settling. Keep the next gesture out of that animation.
                SystemClock.sleep(500)
            }
        }
    }

    private fun awaitViewerPageAdvance(device: UiDevice, previousPage: Int): Int {
        val deadline = SystemClock.elapsedRealtime() + 4_000L
        var lastPage = previousPage
        var stableForwardSamples = 0
        do {
            val observedPage = requireViewerPageCounter(device).first
            if (observedPage > previousPage && observedPage == lastPage) {
                stableForwardSamples++
            } else {
                stableForwardSamples = if (observedPage > previousPage) 1 else 0
            }
            lastPage = observedPage
            if (stableForwardSamples >= 3) return lastPage
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        return lastPage
    }

    private fun requireViewerPageCounter(device: UiDevice): Pair<Int, Int> {
        val counter = device.findObjects(
            By.text(java.util.regex.Pattern.compile("^\\d+ / \\d+$")),
        ).firstOrNull()?.text ?: error(
            "Reels viewer has no visible page counter; cannot certify vertical paging",
        )
        val parts = counter.split(" / ")
        val page = parts[0].toInt()
        val count = parts[1].toInt()
        check(page in 1..count) { "Invalid Reels viewer page counter: $counter" }
        return page to count
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
    ) {
        val deadlineMs = SystemClock.elapsedRealtime() + timeoutMs
        var lastObservedActions = ""
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            if (dismissVisibleIme(device)) {
                device.waitForIdle()
            }
            // The redesigned Photos-first shell places Reel Browsing in Tools. The old
            // benchmark waited for it on Photos, so all seven benchmarks timed out even
            // after the app compiled and displayed its gallery.
            val action = clickableAncestor(device.findObject(By.text(REELS_ACTION_TEXT)))
            if (action?.isEnabled == true) {
                val photosTab = clickableAncestor(device.findObject(By.text(PHOTOS_TAB_TEXT)))
                check(photosTab != null) { "Photos tab disappeared after gallery readiness" }
                photosTab.click()
                device.waitForIdle()
                return
            }
            if (action == null) {
                clickableAncestor(device.findObject(By.text(TOOLS_TAB_TEXT)))?.let { toolsTab ->
                    toolsTab.click()
                    device.waitForIdle()
                }
            }
            lastObservedActions = "toolsVisible=${device.hasObject(By.text(TOOLS_TAB_TEXT))} " +
                "reelsVisible=${device.hasObject(By.text(REELS_ACTION_TEXT))} " +
                "reelsEnabled=${action?.isEnabled}"
            device.waitForIdle()
            SystemClock.sleep(100)
        }
        readinessFailed = true
        error(
            "PhotoBook did not reach its ready state before benchmark measurement; " +
                "librarySize=${BenchmarkMediaSeeder.requestedLibrarySize()} " +
                "timeoutMs=$timeoutMs $lastObservedActions",
        )
    }

    private fun enableReelsMode(device: UiDevice) {
        val toolsTab = clickableAncestor(device.findObject(By.text(TOOLS_TAB_TEXT)))
            ?: error("Tools tab unavailable for Reel benchmark")
        toolsTab.click()
        device.waitForIdle()
        val action = clickableAncestor(device.wait(
            Until.findObject(By.text(REELS_ACTION_TEXT)),
            UI_TIMEOUT_MS,
        )) ?: error("Reel action unavailable on Tools tab")
        check(action.isEnabled) { "Reel action disabled after indexing" }
        action.click()
        device.waitForIdle()
        val photosTab = clickableAncestor(device.findObject(By.text(PHOTOS_TAB_TEXT)))
            ?: error("Photos tab unavailable after enabling Reels")
        photosTab.click()
        device.waitForIdle()
    }

    /**
     * Finds a visible grid card without depending on PhotoThumbnail's accessibility
     * label. The label intentionally changes from file name to ML tags as local
     * intelligence finishes, so it is not a stable benchmark identifier.
     *
     * Compose virtual semantics nodes are not reliably package-attributed in
     * UiAutomator, but the photo Card is explicitly combinedClickable. Start from
     * clickable semantics, snapshot bounds immediately, then identify a photo row
     * by the deterministic grid geometry. This keeps live UiObject2 instances out
     * of the selector result and avoids stale-node failures during recomposition.
     */
    private fun waitForVisiblePhotoThumbnail(device: UiDevice): TapPoint? {
        val deadlineMs = SystemClock.elapsedRealtime() + THUMBNAIL_TIMEOUT_MS
        var lastClickableBounds = ""
        var lastCandidateBounds = ""
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            // The app auto-focuses search when it reaches ready state. Ensure the results
            // viewport, rather than the input method, owns the lower part of the screen before
            // interpreting clickable bounds as grid geometry.
            if (dismissVisibleIme(device)) {
                device.waitForIdle()
            }

            val geometry = photoGridGeometry(device)
            val clickables = snapshotClickableBounds(device)
            lastClickableBounds = clickables
                .take(24)
                .joinToString(separator = ";") { candidate ->
                    "${candidate.left},${candidate.top},${candidate.right},${candidate.bottom}"
                }
            val candidates = clickables.filter { candidate ->
                isPhotoGridCandidate(candidate, geometry)
            }
            lastCandidateBounds = candidates
                .take(12)
                .joinToString(separator = ";") { candidate ->
                    "${candidate.left},${candidate.top},${candidate.right},${candidate.bottom}"
                }

            val visiblePhoto = candidates.firstOrNull { candidate ->
                candidates.any { other ->
                    other != candidate &&
                        abs(other.centerY - candidate.centerY) <= geometry.rowTolerancePx
                }
            }
            if (visiblePhoto != null) {
                return TapPoint(visiblePhoto.centerX, visiblePhoto.centerY)
            }

            device.waitForIdle()
            SystemClock.sleep(50)
        }

        val geometry = photoGridGeometry(device)
        println(
            "[phase3] thumbnailSelector timeout " +
                "expectedCellPx=${geometry.expectedCellPx} " +
                "expectedCardPx=${geometry.expectedCardPx} " +
                "clickableBounds=$lastClickableBounds candidateBounds=$lastCandidateBounds",
        )
        return null
    }

    /**
     * Dismiss the visible system input method without hard-coding Gboard. The default IME
     * package is resolved once from Android's secure setting; UiAutomator's package query only
     * matches it while its window is visible. This keeps Back strictly conditional so the
     * benchmark cannot accidentally leave the target app when no keyboard is present.
     */
    private fun dismissVisibleIme(device: UiDevice): Boolean {
        val imePackage = resolveDefaultImePackage(device) ?: return false
        if (!device.hasObject(By.pkg(imePackage))) return false

        device.pressBack()
        device.waitForIdle()
        SystemClock.sleep(200)
        println("[phase3] dismissed visible IME package=$imePackage")
        return true
    }

    private fun resolveDefaultImePackage(device: UiDevice): String? {
        if (defaultImeResolved) return defaultImePackage
        defaultImeResolved = true
        defaultImePackage = device.executeShellCommand("settings get secure default_input_method")
            .trim()
            .substringBefore('/')
            .takeIf { packageName ->
                packageName.isNotBlank() && packageName != "null" && '.' in packageName
            }
        return defaultImePackage
    }

    private fun snapshotClickableBounds(device: UiDevice): List<PhotoGridCandidate> =
        device.findObjects(By.clickable(true))
            .mapNotNull { node ->
                runCatching {
                    val bounds = node.visibleBounds
                    PhotoGridCandidate(
                        left = bounds.left,
                        top = bounds.top,
                        right = bounds.right,
                        bottom = bounds.bottom,
                    )
                }.getOrNull()
            }
            .distinct()

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

    private fun isPhotoGridCandidate(
        candidate: PhotoGridCandidate,
        geometry: PhotoGridGeometry,
    ): Boolean {
        val width = candidate.width
        val height = candidate.height
        if (width <= 0 || height <= 0) return false
        if (candidate.left < geometry.minLeftPx || candidate.right > geometry.maxRightPx) return false
        if (abs(width - height) > geometry.tolerancePx) return false

        val matchesOuterCell =
            abs(width - geometry.expectedCellPx) <= geometry.tolerancePx &&
                abs(height - geometry.expectedCellPx) <= geometry.tolerancePx
        val matchesPaddedCard =
            abs(width - geometry.expectedCardPx) <= geometry.tolerancePx &&
                abs(height - geometry.expectedCardPx) <= geometry.tolerancePx
        return matchesOuterCell || matchesPaddedCard
    }

    private fun ensureTargetStopped(device: UiDevice) {
        val deadlineMs = SystemClock.elapsedRealtime() + PROCESS_STOP_TIMEOUT_MS
        while (SystemClock.elapsedRealtime() < deadlineMs) {
            device.executeShellCommand("am force-stop $TARGET_PACKAGE")
            SystemClock.sleep(PROCESS_STOP_POLL_MS)
            if (device.executeShellCommand("pidof $TARGET_PACKAGE").trim().isNotEmpty()) {
                continue
            }

            // Require a brief stable-empty window. At 100k photos a pending background callback
            // can restart the app a few hundred milliseconds after the first force-stop.
            SystemClock.sleep(PROCESS_STOP_STABLE_MS)
            if (device.executeShellCommand("pidof $TARGET_PACKAGE").trim().isEmpty()) {
                return
            }
        }
        error("PhotoBook process would not remain stopped before cold-start measurement")
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

    private data class TapPoint(
        val x: Int,
        val y: Int,
    )

    private data class PhotoGridCandidate(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
    ) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
        val centerX: Int get() = left + width / 2
        val centerY: Int get() = top + height / 2
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
        @Volatile
        private var readinessFailed = false

        private const val TARGET_PACKAGE = "com.photobook.app"
        private const val TARGET_ACTIVITY = ".MainActivity"
        private const val REELS_ACTION_TEXT = "Reel Browsing"
        private const val REELS_PAGER_TEST_TAG = "photobook_reels_vertical_pager"
        private const val PHOTOS_TAB_TEXT = "Photos"
        private const val TOOLS_TAB_TEXT = "Tools"
        private const val REELS_MEASURED_SWIPES = 12
        private const val STARTUP_ITERATIONS = 10
        private const val INTERACTION_ITERATIONS = 5
        private const val UI_TIMEOUT_MS = 8_000L
        private const val THUMBNAIL_TIMEOUT_MS = 60_000L
        private const val PROCESS_STOP_TIMEOUT_MS = 8_000L
        private const val PROCESS_STOP_POLL_MS = 100L
        private const val PROCESS_STOP_STABLE_MS = 400L
        private const val MAX_ANCESTOR_DEPTH = 4
        private const val RESULTS_HORIZONTAL_INSET_DP = 20f
        private const val PHOTO_CARD_PADDING_DP = 2f
        private const val PHOTO_SIZE_TOLERANCE_DP = 10f
        private const val ROW_ALIGNMENT_TOLERANCE_DP = 10f
    }
}
