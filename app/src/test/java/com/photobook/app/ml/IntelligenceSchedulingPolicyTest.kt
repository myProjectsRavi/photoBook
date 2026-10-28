package com.photobook.app.ml

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class IntelligenceSchedulingPolicyTest {
    @Test
    fun libraryMaintenance_yieldsToForegroundBrowsing() {
        assertThat(
            IntelligenceSchedulingPolicy.shouldRunLibraryMaintenance(
                appInForeground = true,
                batteryTooLow = false,
            ),
        ).isFalse()
    }

    @Test
    fun libraryMaintenance_runsOnlyWhenBackgroundAndBatterySafe() {
        assertThat(
            IntelligenceSchedulingPolicy.shouldRunLibraryMaintenance(
                appInForeground = false,
                batteryTooLow = false,
            ),
        ).isTrue()
        assertThat(
            IntelligenceSchedulingPolicy.shouldRunLibraryMaintenance(
                appInForeground = false,
                batteryTooLow = true,
            ),
        ).isFalse()
    }
}
