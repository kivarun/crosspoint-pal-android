package fans.crosspoint.pal.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fans.crosspoint.pal.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI behaviour of the Home screen in the app's start state: no companion
 * device is connected, so the connect workflow is offered and content
 * actions stay hidden. Real BLE behaviour needs hardware and is covered by
 * device UAT, not here.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startStateOffersTheConnectWorkflowWithoutContentActions() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No device connected").assertIsDisplayed()
        composeRule.onNodeWithText("Connect device").assertIsDisplayed()
        composeRule.onNodeWithText("Send to Pal").assertDoesNotExist()
        composeRule.onNodeWithText("Last button event").assertDoesNotExist()
        composeRule.onNodeWithText("Scanning…").assertDoesNotExist()
    }

    @Test
    fun connectPressedWithoutPermissionsDoesNotStartAScan() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Connect device").performClick()
        composeRule.waitForIdle()

        // Permissions are requested first; no scan UI appears without them.
        composeRule.onNodeWithText("Scanning…").assertDoesNotExist()
    }
}
