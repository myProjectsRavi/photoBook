package com.photobook.app.feature.archive

/**
 * Per-category keyset cursor for bounded Archive scans.
 *
 * Payments and Food are backed by independent queries, so they must never share a cursor.
 * Advancing one category from another category's oldest row can skip unvisited records.
 */
internal data class ArchivePageKey(
    val dateAdded: Long,
    val id: Long,
)

internal data class ArchiveKeysetCursor(
    val beforeDateAdded: Long = Long.MAX_VALUE,
    val beforeId: Long = Long.MAX_VALUE,
    val exhausted: Boolean = false,
) {
    fun advance(page: List<ArchivePageKey>, pageSize: Int): ArchiveKeysetCursor {
        require(pageSize > 0) { "pageSize must be positive" }
        if (exhausted) return this
        if (page.isEmpty()) return copy(exhausted = true)

        val oldest = page.minWithOrNull(
            compareBy<ArchivePageKey> { key -> key.dateAdded }
                .thenBy { key -> key.id },
        ) ?: return copy(exhausted = true)

        val movedBackward = oldest.dateAdded < beforeDateAdded ||
            (oldest.dateAdded == beforeDateAdded && oldest.id < beforeId)
        check(movedBackward) { "Archive keyset page did not advance" }

        return ArchiveKeysetCursor(
            beforeDateAdded = oldest.dateAdded,
            beforeId = oldest.id,
            exhausted = page.size < pageSize,
        )
    }
}
