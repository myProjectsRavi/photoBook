package com.photobook.app.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.photobook.app.ui.theme.PhotoBookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainTabsAccessibilityInstrumentedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun photosTab_has48dpClickableTargetAtDefaultFont() = verifyTouchTarget(fontScale = 1f)

    @Test
    fun photosTab_has48dpClickableTargetAt200PercentFont() = verifyTouchTarget(fontScale = 2f)

    private fun verifyTouchTarget(fontScale: Float) {
        var clicks = 0
        composeRule.setContent {
            PhotoBookTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalDensity provides Density(1f, fontScale)) {
                    var selected by remember { mutableStateOf(true) }
                    Row(modifier = Modifier.width(320.dp)) {
                        RefinedTabButton(
                            label = "Photos",
                            selected = selected,
                            onClick = {
                                clicks++
                                selected = !selected
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        val tab = composeRule.onNode(hasText("Photos") and hasClickAction())
        tab.assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
            .performClick()
        tab.assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, false))
        composeRule.runOnIdle { assertEquals(1, clicks) }
    }
}
