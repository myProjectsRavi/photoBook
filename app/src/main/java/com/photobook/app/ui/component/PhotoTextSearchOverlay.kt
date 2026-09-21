package com.photobook.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
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
    val byId = remember(layout) {
        layout.elements.associateBy { element -> element.id }
    }
    val active = matches.getOrNull(activeMatchIndex)
    val activeIds = remember(active) {
        active?.elementIds.orEmpty().toSet()
    }
    val drawableIds = remember(matches, active) {
        val ids = LinkedHashSet<Int>()
        matches.take(MAX_PAINTED_OCCURRENCES).forEach { occurrence ->
            ids.addAll(occurrence.elementIds)
        }
        active?.elementIds?.let(ids::addAll)
        ids.toList()
    }

    Box(
        modifier = modifier.drawWithCache {
            if (
                drawableIds.isEmpty() ||
                byId.isEmpty() ||
                size.width <= 0f ||
                size.height <= 0f
            ) {
                return@drawWithCache onDrawBehind { }
            }

            val paths = drawableIds.mapNotNull { elementId ->
                val element = byId[elementId] ?: return@mapNotNull null
                val mapped = PhotoTextCoordinateMapper.mapPolygonToFitViewport(
                    corners = element.corners,
                    uprightWidth = layout.uprightWidth,
                    uprightHeight = layout.uprightHeight,
                    viewportWidth = size.width,
                    viewportHeight = size.height,
                )
                if (
                    mapped.size < 3 ||
                    mapped.any { point -> !point.x.isFinite() || !point.y.isFinite() }
                ) {
                    return@mapNotNull null
                }

                val minX = mapped.minOf { it.x }
                val maxX = mapped.maxOf { it.x }
                val minY = mapped.minOf { it.y }
                val maxY = mapped.maxOf { it.y }
                if (maxX < 0f || maxY < 0f || minX > size.width || minY > size.height) {
                    return@mapNotNull null
                }

                val path = Path().apply {
                    moveTo(mapped.first().x, mapped.first().y)
                    mapped.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    close()
                }
                elementId to path
            }

            onDrawBehind {
                paths.forEach { (elementId, path) ->
                    drawPath(
                        path = path,
                        color = HIGHLIGHT_COLOR.copy(
                            alpha = if (elementId in activeIds) {
                                ACTIVE_HIGHLIGHT_ALPHA
                            } else {
                                HIGHLIGHT_ALPHA
                            },
                        ),
                        style = Fill,
                    )
                }
            }
        },
    )
}

private val HIGHLIGHT_COLOR = Color(0xFFFFEB3B)
private const val HIGHLIGHT_ALPHA = 0.28f
private const val ACTIVE_HIGHLIGHT_ALPHA = 0.42f
private const val MAX_PAINTED_OCCURRENCES = 200
