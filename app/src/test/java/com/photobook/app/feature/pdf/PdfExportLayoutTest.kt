package com.photobook.app.feature.pdf

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PdfExportLayoutTest {

    @Test
    fun pageSpecFor_portraitImage_usesPortraitPage() {
        val spec = PdfPageLayout.pageSpecFor(imageWidth = 1080, imageHeight = 2400)

        assertThat(spec).isEqualTo(
            PdfPageSpec(
                width = PdfPageLayout.SHORT_EDGE,
                height = PdfPageLayout.LONG_EDGE,
            ),
        )
    }

    @Test
    fun pageSpecFor_landscapeImage_usesLandscapePage() {
        val spec = PdfPageLayout.pageSpecFor(imageWidth = 2400, imageHeight = 1080)

        assertThat(spec).isEqualTo(
            PdfPageSpec(
                width = PdfPageLayout.LONG_EDGE,
                height = PdfPageLayout.SHORT_EDGE,
            ),
        )
    }

    @Test
    fun pageSpecFor_squareImage_usesPortraitPage() {
        val spec = PdfPageLayout.pageSpecFor(imageWidth = 1200, imageHeight = 1200)

        assertThat(spec).isEqualTo(
            PdfPageSpec(
                width = PdfPageLayout.SHORT_EDGE,
                height = PdfPageLayout.LONG_EDGE,
            ),
        )
    }

    @Test
    fun fitCenter_wideImage_preservesFullImageWithoutCrop() {
        val placement = PdfPageLayout.fitCenter(
            pageWidth = 1240,
            pageHeight = 1754,
            imageWidth = 2000,
            imageHeight = 1000,
        )

        assertThat(placement.left).isWithin(0.01f).of(0f)
        assertThat(placement.right).isWithin(0.01f).of(1240f)
        assertThat(placement.top).isWithin(0.01f).of(567f)
        assertThat(placement.bottom).isWithin(0.01f).of(1187f)
    }

    @Test
    fun constraintsForLiteMode_capsPagesAndDecodeSize() {
        val lite = PdfExportConstraints.forLiteMode(isLite = true)
        val standard = PdfExportConstraints.forLiteMode(isLite = false)

        assertThat(lite.maxPageCount).isLessThan(standard.maxPageCount)
        assertThat(lite.maxImageDimensionPx).isLessThan(standard.maxImageDimensionPx)
        assertThat(lite.maxPageCount).isEqualTo(25)
        assertThat(standard.maxPageCount).isEqualTo(100)
    }

    @Test
    fun fileName_sanitizesSourceNameAndKeepsPdfExtension() {
        val fileName = PdfFileNames.build(
            stamp = "20260701_101530",
            sourceFileName = "My ID / Front.jpg",
        )

        assertThat(fileName).isEqualTo("My_ID___Front_20260701_101530.pdf")
    }

    @Test
    fun shareCachePolicy_marksOnlyExpiredFilesStale() {
        val now = 1_000_000_000L

        assertThat(
            PdfShareCachePolicy.isStale(
                lastModifiedMs = now - PdfShareCachePolicy.TTL_MS - 1,
                nowMs = now,
            ),
        ).isTrue()
        assertThat(
            PdfShareCachePolicy.isStale(
                lastModifiedMs = now - PdfShareCachePolicy.TTL_MS + 1,
                nowMs = now,
            ),
        ).isFalse()
    }
}
