package com.photobook.app.feature.cleanup

internal object CleanupSelectionPolicy {
    /**
     * Cleanup refreshes may keep an explicit user selection only while its candidates remain valid.
     * Newly discovered cleanup candidates are never selected automatically.
     */
    fun retainExplicitSelection(
        selectedPhotoIds: Set<Long>,
        candidatePhotoIds: Set<Long>,
    ): Set<Long> = selectedPhotoIds.intersect(candidatePhotoIds)
}
