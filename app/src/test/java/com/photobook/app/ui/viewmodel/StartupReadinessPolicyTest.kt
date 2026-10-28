package com.photobook.app.ui.viewmodel

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StartupReadinessPolicyTest {
    @Test
    fun noPermission_neverPublishesBrowseReadiness() {
        assertThat(StartupReadinessPolicy.canBrowse(false, 100, true)).isFalse()
    }

    @Test
    fun accessSafePersistedRows_allowBrowsingWhileBaseSyncContinues() {
        assertThat(StartupReadinessPolicy.canBrowse(true, 1, false)).isTrue()
    }

    @Test
    fun emptyFirstLaunch_waitsForBaseSync() {
        assertThat(StartupReadinessPolicy.canBrowse(true, 0, false)).isFalse()
        assertThat(StartupReadinessPolicy.canBrowse(true, 0, true)).isTrue()
    }
}
