package com.photobook.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

object LocalDiagnostics {
    private const val TAG = "PhotoBookDiagnostics"
    private const val DIR_NAME = "diagnostics"
    private const val FILE_NAME = "diagnostics.log"
    private const val MAX_ENTRIES = 80

    private val writer = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "photobook-local-diagnostics").apply {
            isDaemon = true
        }
    }

    fun record(
        context: Context,
        area: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        val safeArea = sanitizeDiagnosticText(area)
        val safeMessage = sanitizeDiagnosticText(message)
        val throwableName = sanitizeDiagnosticText(throwable?.javaClass?.name.orEmpty())
        val throwableMessage = sanitizeDiagnosticText(throwable?.message.orEmpty())

        // Never pass the raw Throwable to Logcat: its message/stack can contain content URIs,
        // media paths or app-private paths. Keep the local diagnostic intentionally minimal.
        val throwableSummary = listOf(throwableName, throwableMessage)
            .filter { value -> value.isNotBlank() }
            .joinToString(": ")
        Log.e(
            TAG,
            buildString {
                append('[')
                append(safeArea)
                append("] ")
                append(safeMessage)
                if (throwableSummary.isNotBlank()) {
                    append(" | ")
                    append(throwableSummary)
                }
            },
        )

        val appContext = context.applicationContext
        runCatching {
            writer.execute {
                runCatching {
                    val dir = File(appContext.filesDir, DIR_NAME).apply {
                        check(exists() || mkdirs())
                    }
                    val file = File(dir, FILE_NAME)
                    val entry = buildEntry(
                        area = safeArea,
                        message = safeMessage,
                        throwableName = throwableName,
                        throwableMessage = throwableMessage,
                    )
                    val existing = if (file.exists()) {
                        file.readLines().takeLast(MAX_ENTRIES - 1)
                    } else {
                        emptyList()
                    }
                    file.writeText(
                        (existing + entry).joinToString(separator = "\n", postfix = "\n"),
                    )
                }
            }
        }
    }

    private fun buildEntry(
        area: String,
        message: String,
        throwableName: String,
        throwableMessage: String,
    ): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            .format(Date())
        return listOf(timestamp, area, message, throwableName, throwableMessage)
            .joinToString(separator = "\t")
    }
}

internal fun sanitizeDiagnosticText(value: String): String {
    return value
        .replace(Regex("content://[^\\s]+"), "[content-uri]")
        .replace(Regex("file:///[^\\s]+"), "[file-uri]")
        .replace(Regex("/storage/[^\\s]+"), "[media-path]")
        .replace(Regex("/data/user/[^\\s]+"), "[app-path]")
        .replace('\n', ' ')
        .replace('\r', ' ')
        .replace('\t', ' ')
        .take(600)
}
