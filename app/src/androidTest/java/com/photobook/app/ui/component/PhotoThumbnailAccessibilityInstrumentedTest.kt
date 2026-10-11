package com.photobook.app.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.data.model.PhotoRecord
import com.photobook.app.ui.screen.SelectionActionBar
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
    fun selectionActions_remainReachableAt320dpAnd200PercentFont() {
        val selected = setOf(11L, 12L, 13L)
        var trashRequest: Set<Long>? = null
        var shareRequest: Set<Long>? = null
        var clearRequests = 0

        composeRule.setContent {
            PhotoBookTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                    Box(modifier = androidx.compose.ui.Modifier.width(320.dp)) {
                        SelectionActionBar(
                            selectedPhotoIds = selected,
                            onClearSelection = { clearRequests++ },
                            onShareSelected = { shareRequest = it },
                            onMoveSelectedToTrash = { trashRequest = it },
                            onCreatePdfSelected = {},
                            onAddSelectedToVault = {},
                            onCopyTextFromPhoto = {},
                        )
                    }
                }
            }
        }

        composeRule.onNodeWithText("3 selected").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Clear selection").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Share selected").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Move to Trash").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("More selection actions").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("Move to Trash").performClick()
        composeRule.onNodeWithContentDescription("Share selected").performClick()
        composeRule.onNodeWithContentDescription("Clear selection").performClick()
        composeRule.runOnIdle {
            org.junit.Assert.assertEquals(selected, trashRequest)
            org.junit.Assert.assertEquals(selected, shareRequest)
            org.junit.Assert.assertEquals(1, clearRequests)
        }
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
