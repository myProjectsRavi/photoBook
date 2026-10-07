package com.photobook.app.feature.duplicates

internal object CleanupGroupPolicy {
    /**
     * Reconciles cleanup groups only after Android has confirmed that media moved to Trash.
     *
     * Groups with fewer than two surviving members are no longer actionable comparisons.
     * A removed burst hero is cleared rather than pointing at a photo that no longer exists.
     */
    fun afterConfirmedTrash(
        groups: List<DuplicatePhotoGroup>,
        removedPhotoIds: Set<Long>,
    ): List<DuplicatePhotoGroup> {
        if (removedPhotoIds.isEmpty()) return groups

        return groups.mapNotNull { group ->
            val remaining = group.photos.filterNot { photo -> photo.id in removedPhotoIds }
            if (remaining.size < 2) {
                null
            } else {
                group.copy(
                    photos = remaining,
                    heroPhotoId = group.heroPhotoId?.takeIf { heroId ->
                        remaining.any { photo -> photo.id == heroId }
                    },
                )
            }
        }
    }
}
