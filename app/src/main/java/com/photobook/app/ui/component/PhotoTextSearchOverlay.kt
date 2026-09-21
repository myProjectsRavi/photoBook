package com.photobook.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
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
    viewportScale: Float = 1f,
    viewportTranslation: Offset = Offset.Zero,
) {
    val byId = remember(layout) {
        layout.elements.associateBy { element -> element.id }
    }
    val active = matches.getOrNull(activeMatchIndex)
    val activeIds = remember(active) {
        active?.elementIds.orEmpty().toSet()
    }

    Box(
        modifier = modifier.drawWithCache {
            if (
                matches.isEmpty() ||
                byId.isEmpty() ||
                size.width <= 0f ||
                size.height <= 0f
            ) {
                return@drawWithCache onDrawBehind { }
            }

            val safeScale = viewportScale
                .takeIf { it.isFinite() && it > 0f }
                ?: 1f
            val safeTranslation = Offset(
                x = viewportTranslation.x.takeIf { it.isFinite() } ?: 0f,
                y = viewportTranslation.y.takeIf { it.isFinite() } ?: 0f,
            )
            val viewportCenter = Offset(size.width / 2f, size.height / 2f)
            val mappedById = HashMap<Int, MappedElement>()

            fun mappedElement(elementId: Int): MappedElement? {
                mappedById[elementId]?.let { return it }
                val element = byId[elementId] ?: return null
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
                    return null
                }

                val path = Path().apply {
                    moveTo(mapped.first().x, mapped.first().y)
                    mapped.drop(1).forEach { point -> lineTo(point.x, point.y) }
                    close()
                }
                val transformedX = mapped.map { point ->
                    viewportCenter.x +
                        (point.x - viewportCenter.x) * safeScale +
                        safeTranslation.x
                }
                val transformedY = mapped.map { point ->
                    viewportCenter.y +
                        (point.y - viewportCenter.y) * safeScale +
                        safeTranslation.y
                }
                val result = MappedElement(
                    id = elementId,
                    path = path,
                    transformedMinX = transformedX.min(),
                    transformedMaxX = transformedX.max(),
                    transformedMinY = transformedY.min(),
                    transformedMaxY = transformedY.max(),
                )
                mappedById[elementId] = result
                return result
            }

            fun isVisible(element: MappedElement): Boolean {
                return element.transformedMaxX >= 0f &&
                    element.transformedMaxY >= 0f &&
                    element.transformedMinX <= size.width &&
                    element.transformedMinY <= size.height
            }

            val chosenIds = LinkedHashSet<Int>()
            var visibleOccurrenceCount = 0
            for (occurrence in matches) {
                if (visibleOccurrenceCount >= MAX_PAINTED_OCCURRENCES) break
                val elements = occurrence.elementIds.mapNotNull(::mappedElement)
                if (elements.any(::isVisible)) {
                    occurrence.elementIds.forEach { elementId -> chosenIds.add(elementId) }
                    visibleOccurrenceCount += 1
                }
            }
            active?.elementIds?.forEach { elementId -> chosenIds.add(elementId) }

            val paths = chosenIds.mapNotNull { elementId ->
                mappedElement(elementId)?.let { element -> element.id to element.path }
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

private data class MappedElement(
    val id: Int,
    val path: Path,
    val transformedMinX: Float,
    val transformedMaxX: Float,
    val transformedMinY: Float,
    val transformedMaxY: Float,
)

private val HIGHLIGHT_COLOR = Color(0xFFFFEB3B)
private const val HIGHLIGHT_ALPHA = 0.28f
private const val ACTIVE_HIGHLIGHT_ALPHA = 0.42f
private const val MAX_PAINTED_OCCURRENCES = 200
