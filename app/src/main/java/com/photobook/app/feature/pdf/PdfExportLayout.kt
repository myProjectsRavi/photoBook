package com.photobook.app.feature.pdf

internal data class PdfPageSpec(
    val width: Int,
    val height: Int,
)

internal data class PdfFitPlacement(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

internal data class PdfExportConstraints(
    val maxPageCount: Int,
    val maxImageDimensionPx: Int,
) {
    companion object {
        fun forLiteMode(isLite: Boolean): PdfExportConstraints {
            return if (isLite) {
                PdfExportConstraints(
                    maxPageCount = LITE_MAX_PAGE_COUNT,
                    maxImageDimensionPx = LITE_MAX_IMAGE_DIMENSION,
                )
            } else {
                PdfExportConstraints(
                    maxPageCount = STANDARD_MAX_PAGE_COUNT,
                    maxImageDimensionPx = STANDARD_MAX_IMAGE_DIMENSION,
                )
            }
        }

        private const val LITE_MAX_PAGE_COUNT = 25
        private const val STANDARD_MAX_PAGE_COUNT = 100
        private const val LITE_MAX_IMAGE_DIMENSION = 1600
        private const val STANDARD_MAX_IMAGE_DIMENSION = 2400
    }
}

internal object PdfPageLayout {
    const val SHORT_EDGE = 1240
    const val LONG_EDGE = 1754

    fun pageSpecFor(imageWidth: Int, imageHeight: Int): PdfPageSpec {
        val safeWidth = imageWidth.coerceAtLeast(1)
        val safeHeight = imageHeight.coerceAtLeast(1)
        return if (safeWidth > safeHeight) {
            PdfPageSpec(width = LONG_EDGE, height = SHORT_EDGE)
        } else {
            PdfPageSpec(width = SHORT_EDGE, height = LONG_EDGE)
        }
    }

    fun fitCenter(
        pageWidth: Int,
        pageHeight: Int,
        imageWidth: Int,
        imageHeight: Int,
    ): PdfFitPlacement {
        val safePageWidth = pageWidth.toFloat().coerceAtLeast(1f)
        val safePageHeight = pageHeight.toFloat().coerceAtLeast(1f)
        val safeImageWidth = imageWidth.toFloat().coerceAtLeast(1f)
        val safeImageHeight = imageHeight.toFloat().coerceAtLeast(1f)
        val scale = minOf(safePageWidth / safeImageWidth, safePageHeight / safeImageHeight)
        val drawWidth = safeImageWidth * scale
        val drawHeight = safeImageHeight * scale
        val left = (safePageWidth - drawWidth) / 2f
        val top = (safePageHeight - drawHeight) / 2f
        return PdfFitPlacement(
            left = left,
            top = top,
            right = left + drawWidth,
            bottom = top + drawHeight,
        )
    }
}

internal object PdfShareCachePolicy {
    const val DIRECTORY_NAME = "pdf_share"
    const val TTL_MS = 24L * 60L * 60L * 1000L

    fun isStale(lastModifiedMs: Long, nowMs: Long): Boolean {
        return lastModifiedMs < nowMs - TTL_MS
    }
}

internal object PdfFileNames {
    fun build(stamp: String, sourceFileName: String? = null): String {
        val base = sourceFileName
            ?.substringBeforeLast('.', missingDelimiterValue = sourceFileName)
            ?.replace(Regex("[^A-Za-z0-9._-]"), "_")
            ?.trim('_', '.', '-')
            ?.take(48)
            ?.ifBlank { null }
            ?: "PhotoBook"
        return "${base}_$stamp.pdf"
    }
}
