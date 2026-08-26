package com.photobook.app.ui.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupPreviewTrustTest {

    @Test
    fun exactVersionAndGenerationMatch_isTrusted() {
        assertTrue(
            canUseStartupPreview(
                currentVersion = "v1",
                currentGeneration = 42L,
                persistedVersion = "v1",
                persistedGeneration = 42L,
            ),
        )
    }

    @Test
    fun missingGeneration_failsClosed() {
        assertFalse(canUseStartupPreview("v1", null, "v1", 42L))
        assertFalse(canUseStartupPreview("v1", 42L, "v1", null))
    }

    @Test
    fun versionOrGenerationMismatch_failsClosed() {
        assertFalse(canUseStartupPreview("v2", 42L, "v1", 42L))
        assertFalse(canUseStartupPreview("v1", 43L, "v1", 42L))
        assertFalse(canUseStartupPreview("v1", 42L, null, 42L))
    }
}
