package com.photobook.app.feature.phototextsearch

import androidx.compose.runtime.Immutable

@Immutable
data class UnitPoint(
    val x: Float,
    val y: Float,
)

enum class PhotoTextCompleteness {
    COMPLETE,
    PARTIAL,
}

@Immutable
data class TextElement(
    val id: Int,
    val blockId: Int,
    val lineId: Int,
    val text: String,
    val corners: List<UnitPoint>,
)

@Immutable
data class PhotoTextLayout(
    val uprightWidth: Int,
    val uprightHeight: Int,
    val elements: List<TextElement>,
    val completeness: PhotoTextCompleteness,
)

@Immutable
data class SearchOccurrence(
    val blockId: Int,
    val startInclusive: Int,
    val endExclusive: Int,
    val elementIds: List<Int>,
)

internal data class SearchBlock(
    val id: Int,
    val text: String,
    val ownerElementId: IntArray,
)

enum class PhotoTextSearchPhase {
    CLOSED,
    PREPARING,
    READY,
    UNAVAILABLE,
    FAILED,
}

@Immutable
data class PhotoTextSearchState(
    val phase: PhotoTextSearchPhase = PhotoTextSearchPhase.CLOSED,
    val query: String = "",
    val layout: PhotoTextLayout? = null,
    val matches: List<SearchOccurrence> = emptyList(),
    val activeMatchIndex: Int = -1,
    val isSlow: Boolean = false,
    val queryTooLong: Boolean = false,
    val isQueryPending: Boolean = false,
) {
    val isOpen: Boolean
        get() = phase != PhotoTextSearchPhase.CLOSED

    val activeMatch: SearchOccurrence?
        get() = matches.getOrNull(activeMatchIndex)
}

@Immutable
data class PhotoTextSourceKey(
    val photoId: Long,
    val uriString: String,
)

sealed interface PhotoTextLayoutLoadResult {
    data class Success(val layout: PhotoTextLayout) : PhotoTextLayoutLoadResult
    data object Unavailable : PhotoTextLayoutLoadResult
    data object Failed : PhotoTextLayoutLoadResult
}

interface PhotoTextLayoutSource {
    suspend fun load(source: PhotoTextSourceKey): PhotoTextLayoutLoadResult
}
