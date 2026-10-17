package dev.crosspoint.pal.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import dev.crosspoint.pal.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class NavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bottomNavigationReachesAllFourRootScreens() {
        composeRule.waitForIdle()

        // Home is the start destination.
        composeRule.onNodeWithText("Last button event").assertIsDisplayed()

        composeRule.onNodeWithText("Send").performClick()
        composeRule.onNodeWithText("E-ink preview").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Notifications").performClick()
        composeRule.onNodeWithText("App sources").assertIsDisplayed()

        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Forget device").performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Last button event").assertIsDisplayed()
    }

    @Test
    fun sendDraftStateSurvivesTabSwitches() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Send").performClick()
        composeRule.onNodeWithTag("send_title_input").performTextInput("Kept")
        composeRule.onNodeWithTag("send_body_input").performTextInput("Draft body")

        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Last button event").assertIsDisplayed()

        composeRule.onNodeWithText("Send").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("send_title_input").performTextInput("!")
        composeRule.onNodeWithText("5 / 48").assertIsDisplayed()
    }

    @Test
    fun sendToPalActionNavigatesToTheSendScreen() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Send to Pal").performClick()
        composeRule.onNodeWithText("E-ink preview").performScrollTo().assertIsDisplayed()
    }
}
