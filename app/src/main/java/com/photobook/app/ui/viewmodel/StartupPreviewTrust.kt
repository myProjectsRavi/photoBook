package com.photobook.app.ui.viewmodel

/**
 * A persisted Room preview is safe to display only when the MediaStore identity and generation
 * exactly match the sync token recorded after the last authoritative reconciliation.
 *
 * Missing generation support deliberately fails closed: in that case PhotoBook keeps the existing
 * indexing screen until the full MediaStore reconciliation completes.
 */
internal fun canUseStartupPreview(
    currentVersion: String,
    currentGeneration: Long?,
    persistedVersion: String?,
    persistedGeneration: Long?,
): Boolean {
    return persistedVersion != null &&
        currentVersion == persistedVersion &&
        currentGeneration != null &&
        persistedGeneration != null &&
        currentGeneration == persistedGeneration
}
