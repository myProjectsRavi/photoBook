package com.photobook.app.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.photobook.app.feature.phototextsearch.PhotoTextCoordinateMapper
import com.photobook.app.feature.phototextsearch.PhotoTextLayout
import com.photobook.app.feature.phototextsearch.SearchOccurrence

@Composable
fun PhotoTextSearchOverlay(
    layout: PhotoTextLayout,
    matches: List<SearchOccurrence>,
    activeMatchIndex: Int,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        if (matches.isEmpty() || layout.elements.isEmpty()) return@Canvas
        val byId = layout.elements.associateBy { element -> element.id }
        val active = matches.getOrNull(activeMatchIndex)
        val paintable = buildList {
            matches.take(MAX_PAINTED_OCCURRENCES).forEach(::add)
            if (active != null && active !in this) add(active)
        }

        paintable.forEach { occurrence ->
            val isActive = occurrence === active || occurrence == active
            occurrence.elementIds.forEach { elementId ->
                val element = byId[elementId] ?: return@forEach
                val mapped = PhotoTextCoordinateMapper.mapPolygonToFitViewport(
                    corners = element.corners,
                    uprightWidth = layout.uprightWidth,
                    uprightHeight = layout.uprightHeight,
                    viewportWidth = size.width,
                    viewportHeight = size.height,
                )
                if (mapped.size < 3) return@forEach

                val path = Path().apply {
                    moveTo(mapped.first().x, mapped.first().y)
                    mapped.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    close()
                }
                drawPath(
                    path = path,
                    color = HIGHLIGHT_COLOR.copy(alpha = HIGHLIGHT_ALPHA),
                    style = Fill,
                )
                if (isActive) {
                    drawPath(
                        path = path,
                        color = ACTIVE_OUTLINE_COLOR,
                        style = Stroke(width = ACTIVE_STROKE_WIDTH.toPx()),
                    )
                }
            }
        }
    }
}

private val HIGHLIGHT_COLOR = Color(0xFFFFEB3B)
private val ACTIVE_OUTLINE_COLOR = Color(0xFFFFB300)
private const val HIGHLIGHT_ALPHA = 0.32f
private val ACTIVE_STROKE_WIDTH = 2.dp
private const val MAX_PAINTED_OCCURRENCES = 200
