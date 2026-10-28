package com.photobook.app.ui.viewmodel

internal object StartupReadinessPolicy {
    fun canBrowse(
        hasPhotoPermission: Boolean,
        publishedVisiblePhotoCount: Int,
        baseSyncComplete: Boolean,
    ): Boolean {
        if (!hasPhotoPermission) return false
        return publishedVisiblePhotoCount > 0 || baseSyncComplete
    }
}
