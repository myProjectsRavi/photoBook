package com.photobook.app.ui.component

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
