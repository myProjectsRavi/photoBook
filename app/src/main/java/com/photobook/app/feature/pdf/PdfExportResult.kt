package com.photobook.app.feature.pdf

import android.net.Uri

data class PdfExportProgress(
    val totalItems: Int,
    val processedItems: Int,
    val writtenPages: Int,
    val skippedItems: Int,
)

sealed interface PdfExportResult {
    data class Success(
        val uri: Uri,
        val fileName: String,
        val pageCount: Int,
    ) : PdfExportResult

    data class PartialSuccess(
        val uri: Uri,
        val fileName: String,
        val pageCount: Int,
        val skippedCount: Int,
    ) : PdfExportResult

    data class TooManyPages(
        val requested: Int,
        val maxAllowed: Int,
    ) : PdfExportResult

    data class Error(
        val throwable: Throwable? = null,
    ) : PdfExportResult
}
