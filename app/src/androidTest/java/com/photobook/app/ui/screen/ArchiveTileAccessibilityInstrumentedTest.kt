package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.feature.archive.ArchiveCandidate
import com.photobook.app.ui.theme.PhotoBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveTileAccessibilityInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun archiveSelection_is48dpAndDistinctFromPreviewAt200PercentFont() {
        var selectionCalls = 0
        var previewCalls = 0
        val candidate = ArchiveCandidate(
            photo = samplePhoto(),
            confidence = 0.92,
            reasons = listOf("Payment network/reference cue"),
        )
        composeRule.setContent {
            PhotoBookTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    var selected by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.width(320.dp).height(640.dp)) {
                        ArchiveCandidateTile(
                            candidate = candidate,
                            selected = selected,
                            onPreview = { previewCalls++ },
                            onToggleSelection = {
                                selectionCalls++
                                selected = !selected
                            },
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithContentDescription("Select selection.jpg")
            .assertIsDisplayed()
            .assertWidthIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()

        composeRule.onNodeWithContentDescription("Deselect selection.jpg")
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
            .performClick()

        composeRule.runOnIdle {
            assertEquals(2, selectionCalls)
            assertEquals("Selecting a photo must not open its preview", 0, previewCalls)
        }
        composeRule.onNodeWithContentDescription("Select selection.jpg")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
    }

    private fun samplePhoto(): PhotoRecord = PhotoRecord(
        id = 9001L,
        uriString = "content://photobook.test/photo/9001",
        filePath = "/test/selection.jpg",
        fileName = "selection.jpg",
        dateAdded = 1_786_000_000_000L,
        year = 2026,
        month = 10,
        dayOfMonth = 7,
        dayOfWeek = 3,
        hourOfDay = 12,
        latitude = null,
        longitude = null,
        city = null,
        state = null,
        country = null,
        fileSize = 1024L,
        width = 100,
        height = 100,
        mimeType = "image/jpeg",
        folderName = "Test",
        folderPath = "/test",
        cameraModel = null,
        isFrontCamera = false,
        isHdr = false,
    )
}
