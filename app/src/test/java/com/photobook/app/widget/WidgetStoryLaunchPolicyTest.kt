package com.photobook.app.widget

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WidgetStoryLaunchPolicyTest {

    @Test
    fun validIds_preserveExactOrder() {
        assertThat(WidgetStoryLaunchPolicy.parseStoryIds("9, 3, 7"))
            .containsExactly(9L, 3L, 7L)
            .inOrder()
    }

    @Test
    fun malformedDuplicateOrNonPositiveIds_failClosed() {
        assertThat(WidgetStoryLaunchPolicy.parseStoryIds("1,bad,2")).isEmpty()
        assertThat(WidgetStoryLaunchPolicy.parseStoryIds("1,1")).isEmpty()
        assertThat(WidgetStoryLaunchPolicy.parseStoryIds("1,0,2")).isEmpty()
        assertThat(WidgetStoryLaunchPolicy.parseStoryIds("1,,2")).isEmpty()
    }

    @Test
    fun oversizedStoryIdList_failsClosedInsteadOfTruncating() {
        val csv = (1L..(WidgetStoryLaunchPolicy.MAX_STORY_IDS + 1L)).joinToString(",")

        assertThat(WidgetStoryLaunchPolicy.parseStoryIds(csv)).isEmpty()
    }

    @Test
    fun title_isBoundedAndTrimmed() {
        val title = "  " + "M".repeat(WidgetStoryLaunchPolicy.MAX_TITLE_LENGTH + 20)

        assertThat(WidgetStoryLaunchPolicy.sanitizeTitle(title)).hasLength(
            WidgetStoryLaunchPolicy.MAX_TITLE_LENGTH,
        )
    }
}
