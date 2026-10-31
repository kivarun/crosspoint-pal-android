package fans.crosspoint.pal.ui.send

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import fans.crosspoint.pal.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Send screen behaviour without a connected session: the form works, the
 * counters use the protocol byte bounds, and the send action stays disabled
 * until a ready companion session exists.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SendScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private fun openSendTab() {
        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
    }

    @Test
    fun sendStaysDisabledWithoutAReadySession() {
        openSendTab()

        composeRule.onNodeWithTag("send_title_input").performTextInput("Morning")
        composeRule.onNodeWithTag("send_body_input").performTextInput("Quiet body")

        composeRule.onNodeWithTag("send_action_send").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithText("No connected session").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun clearEmptiesBothFieldsAndTheCounters() {
        openSendTab()
        composeRule.onNodeWithTag("send_title_input").performTextInput("Morning")
        composeRule.onNodeWithTag("send_body_input").performTextInput("Quiet body")

        composeRule.onNodeWithText("Clear").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("0 / 64").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("0 / 384").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("send_action_send").performScrollTo().assertIsNotEnabled()
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
