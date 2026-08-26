package com.photobook.app.baselineprofile

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream

/**
 * Seeds a deterministic, isolated MediaStore library for Phase-3 scale benchmarks.
 *
 * This code lives only in the benchmark test APK. It is never packaged into the
 * PhotoBook production application.
 */
object BenchmarkMediaSeeder {
    const val ARG_LIBRARY_SIZE = "photobook.librarySize"
    const val DISPLAY_NAME_PREFIX = "PBENCH_"

    private const val DEFAULT_LIBRARY_SIZE = 10_000
    private const val MAX_LIBRARY_SIZE = 100_000
    private const val BENCHMARK_RELATIVE_PATH = "Pictures/PhotoBookBenchmark/"
    private const val FIXTURE_WIDTH = 256
    private const val FIXTURE_HEIGHT = 256

    fun requestedLibrarySize(): Int {
        val raw = InstrumentationRegistry.getArguments().getString(ARG_LIBRARY_SIZE)
        val requested = raw?.toIntOrNull() ?: DEFAULT_LIBRARY_SIZE
        require(requested in 1..MAX_LIBRARY_SIZE) {
            "$ARG_LIBRARY_SIZE must be between 1 and $MAX_LIBRARY_SIZE; was $requested"
        }
        return requested
    }

    fun readyTimeoutMs(): Long = when (requestedLibrarySize()) {
        in 1..10_000 -> 3L * 60_000L
        in 10_001..50_000 -> 12L * 60_000L
        else -> 30L * 60_000L
    }

    fun ensureSeeded() {
        check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Phase-3 deterministic MediaStore seeding requires Android 10 / API 29 or newer"
        }

        val context = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val requested = requestedLibrarySize()

        assertBenchmarkPathIsIsolated()
        val existing = countBenchmarkRows()

        if (existing == requested) {
            println("[phase3] MediaStore fixture already seeded: count=$requested")
            return
        }

        if (existing > 0) {
            val deleted = resolver.delete(
                collection,
                "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                    "${MediaStore.Images.Media.DISPLAY_NAME} GLOB ?",
                arrayOf(BENCHMARK_RELATIVE_PATH, "$DISPLAY_NAME_PREFIX*"),
            )
            println("[phase3] removed stale PhotoBook benchmark media rows: count=$deleted")
        }

        val payload = createFixturePng()
        val nowMs = System.currentTimeMillis()
        println(
            "[phase3] seeding deterministic MediaStore fixture: " +
                "count=$requested bytesPerImage=${payload.size}",
        )

        repeat(requested) { zeroBasedIndex ->
            val ordinal = zeroBasedIndex + 1
            val takenMs = nowMs - zeroBasedIndex * 5L * 60_000L
            val values = ContentValues().apply {
                put(
                    MediaStore.Images.Media.DISPLAY_NAME,
                    "$DISPLAY_NAME_PREFIX${ordinal.toString().padStart(6, '0')}.png",
                )
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, BENCHMARK_RELATIVE_PATH)
                put(MediaStore.Images.Media.DATE_TAKEN, takenMs)
                put(MediaStore.Images.Media.DATE_ADDED, takenMs / 1_000L)
                put(MediaStore.Images.Media.DATE_MODIFIED, takenMs / 1_000L)
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }

            val uri = checkNotNull(resolver.insert(collection, values)) {
                "MediaStore insert returned null at benchmark fixture $ordinal/$requested"
            }

            try {
                checkNotNull(resolver.openOutputStream(uri, "w")).use { stream ->
                    stream.write(payload)
                }
                val published = resolver.update(
                    uri,
                    ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                    null,
                    null,
                )
                check(published == 1) {
                    "MediaStore failed to publish benchmark fixture $ordinal/$requested"
                }
            } catch (error: Throwable) {
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }

            if (ordinal == requested || ordinal % 1_000 == 0) {
                println("[phase3] seeded $ordinal/$requested benchmark images")
            }
        }

        val finalCount = countBenchmarkRows()
        check(finalCount == requested) {
            "Phase-3 MediaStore fixture count mismatch: requested=$requested actual=$finalCount"
        }
        println("[phase3] MediaStore fixture ready: count=$finalCount")
    }

    /**
     * Never let deterministic benchmark setup delete a user's file that happens to
     * share the benchmark folder. The benchmark path may contain only PhotoBook-owned
     * PBENCH_* fixtures; otherwise fail without logging the foreign filename.
     */
    private fun assertBenchmarkPathIsIsolated() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
            "${MediaStore.Images.Media.RELATIVE_PATH} = ?",
            arrayOf(BENCHMARK_RELATIVE_PATH),
            null,
        )?.use { cursor ->
            val displayNameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val displayName = cursor.getString(displayNameIndex)
                check(displayName?.startsWith(DISPLAY_NAME_PREFIX) == true) {
                    "Benchmark path contains non-PhotoBook media; refusing to modify it"
                }
            }
        }
    }

    private fun countBenchmarkRows(): Int {
        val context = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        return resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Images.Media._ID),
            "${MediaStore.Images.Media.RELATIVE_PATH} = ? AND " +
                "${MediaStore.Images.Media.DISPLAY_NAME} GLOB ?",
            arrayOf(BENCHMARK_RELATIVE_PATH, "$DISPLAY_NAME_PREFIX*"),
            null,
        )?.use { cursor -> cursor.count } ?: 0
    }

    private fun createFixturePng(): ByteArray {
        val bitmap = Bitmap.createBitmap(
            FIXTURE_WIDTH,
            FIXTURE_HEIGHT,
            Bitmap.Config.ARGB_8888,
        )
        bitmap.eraseColor(Color.rgb(76, 110, 245))
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    "Unable to encode deterministic benchmark PNG"
                }
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}
