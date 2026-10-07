package com.photobook.app.feature.memories

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MemoryStoryLaunchPolicyTest {

    @Test
    fun exactCurrentVisibleIds_areAcceptedInOrder() {
        val requested = listOf(7L, 2L, 9L)

        assertThat(
            MemoryStoryLaunchPolicy.validateExternal(
                requestedIds = requested,
                resolvedIds = requested,
                memoriesHidden = false,
            ),
        ).containsExactlyElementsIn(requested).inOrder()
    }

    @Test
    fun missingOrReorderedCurrentIds_areRejected() {
        assertThat(
            MemoryStoryLaunchPolicy.validateExternal(
                requestedIds = listOf(7L, 2L, 9L),
                resolvedIds = listOf(7L, 9L),
                memoriesHidden = false,
            ),
        ).isNull()
        assertThat(
            MemoryStoryLaunchPolicy.validateExternal(
                requestedIds = listOf(7L, 2L, 9L),
                resolvedIds = listOf(2L, 7L, 9L),
                memoriesHidden = false,
            ),
        ).isNull()
    }

    @Test
    fun hiddenMemories_rejectExternalStoryLaunch() {
        assertThat(
            MemoryStoryLaunchPolicy.validateExternal(
                requestedIds = listOf(1L, 2L, 3L),
                resolvedIds = listOf(1L, 2L, 3L),
                memoriesHidden = true,
            ),
        ).isNull()
    }

    @Test
    fun malformedExternalIds_areRejected() {
        assertThat(
            MemoryStoryLaunchPolicy.validateExternal(
                requestedIds = listOf(1L, 1L),
                resolvedIds = listOf(1L, 1L),
                memoriesHidden = false,
            ),
        ).isNull()
        assertThat(
            MemoryStoryLaunchPolicy.validateExternal(
                requestedIds = listOf(1L, 0L),
                resolvedIds = listOf(1L, 0L),
                memoriesHidden = false,
            ),
        ).isNull()
    }
}
