package com.photobook.app.ml

internal object IntelligenceSchedulingPolicy {
    fun shouldRunLibraryMaintenance(
        appInForeground: Boolean,
        batteryTooLow: Boolean,
    ): Boolean = !appInForeground && !batteryTooLow
}
