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
    fun externalCsv_requiresPositiveUniqueBoundedIds() {
        assertThat(MemoryStoryLaunchPolicy.parseExternalIds("7,2,9"))
            .containsExactly(7L, 2L, 9L).inOrder()
        assertThat(MemoryStoryLaunchPolicy.parseExternalIds("7,7")).isNull()
        assertThat(MemoryStoryLaunchPolicy.parseExternalIds("7,0")).isNull()
        assertThat(MemoryStoryLaunchPolicy.parseExternalIds("7,nope")).isNull()

        val oversized = (1..MemoryStoryLaunchPolicy.MAX_WIDGET_STORY_IDS + 1)
            .joinToString(",")
        assertThat(MemoryStoryLaunchPolicy.parseExternalIds(oversized)).isNull()
    }

    @Test
    fun externalTitle_isTrimmedAndBounded() {
        val value = "  " + "A".repeat(MemoryStoryLaunchPolicy.MAX_WIDGET_TITLE_LENGTH + 20) + "  "

        val sanitized = MemoryStoryLaunchPolicy.sanitizeExternalTitle(value)

        assertThat(sanitized.length).isEqualTo(MemoryStoryLaunchPolicy.MAX_WIDGET_TITLE_LENGTH)
        assertThat(sanitized.startsWith(" ")).isFalse()
        assertThat(sanitized.endsWith(" ")).isFalse()
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
