package com.photobook.app.ui.component

import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ui.theme.PhotoBookTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoThumbnailAccessibilityInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun selectableThumbnail_exposesSelectedStateAndCheckboxRole() {
        val photo = samplePhoto()
        composeRule.setContent {
            PhotoBookTheme(dynamicColor = false) {
                PhotoThumbnail(
                    photo = photo,
                    isSelected = true,
                    showSelectionState = true,
                    onClick = {},
                    onLongClick = {},
                    requestSizePx = 128,
                )
            }
        }

        composeRule.onNodeWithContentDescription(photo.fileName, useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    }

    @Test
    fun selectableThumbnail_exposesUnselectedTransition() {
        val photo = samplePhoto()
        composeRule.setContent {
            PhotoBookTheme(dynamicColor = false) {
                PhotoThumbnail(
                    photo = photo,
                    isSelected = false,
                    showSelectionState = true,
                    onClick = {},
                    onLongClick = {},
                    requestSizePx = 128,
                )
            }
        }

        composeRule.onNodeWithContentDescription(photo.fileName, useUnmergedTree = false)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
    }

    @Test
    fun gridSurvivesEmptyAndRepopulatedPagingGenerations() {
        val photo = samplePhoto()
        val generations = MutableStateFlow(PagingData.from(listOf(photo)))
        composeRule.setContent {
            PhotoBookTheme(dynamicColor = false) {
                PhotoGrid(
                    photos = generations.collectAsLazyPagingItems(),
                    columns = 3,
                    timelineMarks = emptyList(),
                    selectedPhotoIds = emptySet(),
                    isSelectionMode = false,
                    onPhotoClick = {},
                    onPhotoLongClick = {},
                    gridState = rememberLazyGridState(),
                )
            }
        }
        composeRule.waitForIdle()
        repeat(4) {
            composeRule.runOnIdle {
                generations.value = PagingData.empty()
            }
            composeRule.waitForIdle()
            composeRule.runOnIdle {
                generations.value = PagingData.from(listOf(photo))
            }
            composeRule.waitForIdle()
        }
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
