package com.photobook.app.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalDiagnostics {
    private const val TAG = "PhotoBookDiagnostics"
    private const val DIR_NAME = "diagnostics"
    private const val FILE_NAME = "diagnostics.log"
    private const val MAX_ENTRIES = 80

    fun record(
        context: Context,
        area: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        Log.e(TAG, "[$area] $message", throwable)
        runCatching {
            val dir = File(context.applicationContext.filesDir, DIR_NAME).apply {
                if (!exists()) mkdirs()
            }
            val file = File(dir, FILE_NAME)
            val entry = buildEntry(area, message, throwable)
            val existing = if (file.exists()) {
                file.readLines().takeLast(MAX_ENTRIES - 1)
            } else {
                emptyList()
            }
            file.writeText((existing + entry).joinToString(separator = "\n", postfix = "\n"))
        }
    }

    private fun buildEntry(area: String, message: String, throwable: Throwable?): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            .format(Date())
        val safeArea = area.sanitize()
        val safeMessage = message.sanitize()
        val throwableName = throwable?.javaClass?.name.orEmpty().sanitize()
        val throwableMessage = throwable?.message.orEmpty().sanitize()
        return listOf(timestamp, safeArea, safeMessage, throwableName, throwableMessage)
            .joinToString(separator = "\t")
    }

    private fun String.sanitize(): String {
        return replace('\n', ' ')
            .replace('\r', ' ')
            .replace('\t', ' ')
            .take(600)
    }
}
