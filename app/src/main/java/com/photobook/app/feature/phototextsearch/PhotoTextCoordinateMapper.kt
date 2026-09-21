package com.photobook.app.feature.phototextsearch

import androidx.compose.runtime.Immutable
import kotlin.math.min

@Immutable
data class PixelPoint(
    val x: Float,
    val y: Float,
)

object PhotoTextCoordinateMapper {

    fun mapToFitViewport(
        point: UnitPoint,
        uprightWidth: Int,
        uprightHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
    ): PixelPoint {
        if (
            uprightWidth <= 0 ||
            uprightHeight <= 0 ||
            viewportWidth <= 0f ||
            viewportHeight <= 0f
        ) {
            return PixelPoint(0f, 0f)
        }

        val imageWidth = uprightWidth.toFloat()
        val imageHeight = uprightHeight.toFloat()
        val fit = min(viewportWidth / imageWidth, viewportHeight / imageHeight)
        val displayedWidth = imageWidth * fit
        val displayedHeight = imageHeight * fit
        val dx = (viewportWidth - displayedWidth) / 2f
        val dy = (viewportHeight - displayedHeight) / 2f
        val unitX = point.x.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        val unitY = point.y.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
        return PixelPoint(
            x = dx + unitX * displayedWidth,
            y = dy + unitY * displayedHeight,
        )
    }

    fun mapViewportPointToUnit(
        point: PixelPoint,
        uprightWidth: Int,
        uprightHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
    ): UnitPoint? {
        if (
            uprightWidth <= 0 ||
            uprightHeight <= 0 ||
            viewportWidth <= 0f ||
            viewportHeight <= 0f ||
            !point.x.isFinite() ||
            !point.y.isFinite()
        ) {
            return null
        }

        val imageWidth = uprightWidth.toFloat()
        val imageHeight = uprightHeight.toFloat()
        val fit = min(viewportWidth / imageWidth, viewportHeight / imageHeight)
        val displayedWidth = imageWidth * fit
        val displayedHeight = imageHeight * fit
        if (
            fit <= 0f ||
            !fit.isFinite() ||
            displayedWidth <= 0f ||
            displayedHeight <= 0f ||
            !displayedWidth.isFinite() ||
            !displayedHeight.isFinite()
        ) {
            return null
        }

        val dx = (viewportWidth - displayedWidth) / 2f
        val dy = (viewportHeight - displayedHeight) / 2f
        return UnitPoint(
            x = ((point.x - dx) / displayedWidth).coerceIn(0f, 1f),
            y = ((point.y - dy) / displayedHeight).coerceIn(0f, 1f),
        )
    }

    fun mapPolygonToFitViewport(
        corners: List<UnitPoint>,
        uprightWidth: Int,
        uprightHeight: Int,
        viewportWidth: Float,
        viewportHeight: Float,
    ): List<PixelPoint> {
        return corners.map { point ->
            mapToFitViewport(
                point = point,
                uprightWidth = uprightWidth,
                uprightHeight = uprightHeight,
                viewportWidth = viewportWidth,
                viewportHeight = viewportHeight,
            )
        }
    }
}
