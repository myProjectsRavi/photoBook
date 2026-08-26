package com.photobook.app.ui.viewmodel

/**
 * A persisted Room preview is safe to display only with Full photo access and when the MediaStore
 * identity + generation exactly match the sync token recorded after the last authoritative
 * reconciliation.
 *
 * Missing generation support deliberately fails closed: in that case PhotoBook keeps the existing
 * indexing screen until the full MediaStore reconciliation completes.
 */
internal fun canUseStartupPreview(
    hasFullPhotoAccess: Boolean,
    currentVersion: String,
    currentGeneration: Long?,
    persistedVersion: String?,
    persistedGeneration: Long?,
): Boolean {
    return hasFullPhotoAccess &&
        persistedVersion != null &&
        currentVersion == persistedVersion &&
        currentGeneration != null &&
        persistedGeneration != null &&
        currentGeneration == persistedGeneration
}
