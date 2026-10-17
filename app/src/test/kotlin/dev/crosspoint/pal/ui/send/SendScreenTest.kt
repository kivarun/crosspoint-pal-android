package dev.crosspoint.pal.ui.send

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import dev.crosspoint.pal.CrossPointPalApplication
import dev.crosspoint.pal.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SendScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val demo get() = ApplicationProvider.getApplicationContext<CrossPointPalApplication>()
        .container.demoCompanionState

    private fun openSendTab() {
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun sendIsDisabledUntilBothFieldsHaveContent() {
        openSendTab()

        composeRule.onNodeWithTag("send_title_input").performTextInput("Morning")
        composeRule.onNodeWithTag("send_action_send").assertIsNotEnabled()

        composeRule.onNodeWithTag("send_body_input").performTextInput("Quiet body")
        composeRule.onNodeWithTag("send_action_send").assertIsEnabled()
    }

    @Test
    fun clearEmptiesBothFieldsAndTheCounters() {
        openSendTab()
        composeRule.onNodeWithTag("send_title_input").performTextInput("Morning")
        composeRule.onNodeWithTag("send_body_input").performTextInput("Quiet body")

        composeRule.onNodeWithText("Clear").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("0 / 48").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0 / 400").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("send_action_send").assertIsNotEnabled()
    }

    @Test
    fun sendReportsSentAndClearsTheDraft() {
        openSendTab()
        composeRule.onNodeWithTag("send_title_input").performTextInput("Morning")
        composeRule.onNodeWithTag("send_body_input").performTextInput("Quiet body")

        composeRule.onNodeWithTag("send_action_send").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Sent to Pal").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Sent to Pal").assertIsDisplayed()
        composeRule.onNodeWithText("0 / 48").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0 / 400").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun sendStaysDisabledWithoutADevice() {
        demo.forgetDevice()
        composeRule.waitForIdle()
        openSendTab()

        composeRule.onNodeWithTag("send_title_input").performTextInput("Morning")
        composeRule.onNodeWithTag("send_body_input").performTextInput("Quiet body")

        // The demo device is gone, so the send action stays disabled.
        composeRule.onNodeWithTag("send_action_send").assertIsNotEnabled()
        assertTrue(demo.device.value == null)
    }

    @Test
    fun previewReflectsCurrentDraft() {
        openSendTab()
        composeRule.onNodeWithTag("send_title_input").performTextInput("Morning")

        // The title appears both in the input and in the preview.
        assertTrue(composeRule.onAllNodesWithText("Morning").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("E-ink preview").performScrollTo().assertIsDisplayed()
    }
}
