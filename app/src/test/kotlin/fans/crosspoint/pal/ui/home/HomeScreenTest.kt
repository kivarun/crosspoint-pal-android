package fans.crosspoint.pal.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fans.crosspoint.pal.CrossPointPalApplication
import fans.crosspoint.pal.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val demo get() = ApplicationProvider.getApplicationContext<CrossPointPalApplication>()
        .container.demoCompanionState

    @Test
    fun connectedHomeShowsDeviceStatusAndActions() {
        composeRule.waitForIdle()

        composeRule.onNodeWithText("X4 Classic").assertIsDisplayed()
        composeRule.onNodeWithText("Connected").assertIsDisplayed()
        composeRule.onNodeWithText("Battery 73%").assertIsDisplayed()
        composeRule.onNodeWithText("Last seen: now").assertIsDisplayed()
        composeRule.onNodeWithText("Paired").assertIsDisplayed()
        composeRule.onNodeWithText("Notifications on").assertIsDisplayed()
        composeRule.onNodeWithText("Ready").assertIsDisplayed()
        composeRule.onNodeWithText("Send to Pal").assertIsDisplayed()
        composeRule.onNodeWithText("Sync notifications").assertIsDisplayed()
        composeRule.onNodeWithText("Right button").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun forgettingTheDeviceShowsTheEmptyStateInsteadOfActions() {
        composeRule.waitForIdle()

        demo.forgetDevice()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No device connected").assertIsDisplayed()
        composeRule.onNodeWithText("Connect device").assertIsDisplayed()
        composeRule.onNodeWithText("Send to Pal").assertDoesNotExist()
        composeRule.onNodeWithText("Sync notifications").assertDoesNotExist()
        composeRule.onNodeWithText("Right button").assertDoesNotExist()
    }
}
