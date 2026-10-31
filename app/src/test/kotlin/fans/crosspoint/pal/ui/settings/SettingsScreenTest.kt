package fans.crosspoint.pal.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import fans.crosspoint.pal.MainActivity
import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import androidx.test.core.app.ApplicationProvider
import fans.crosspoint.pal.CrossPointPalApplication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Settings screen behaviour in the app's start state: the theme preference
 * works, and pairing/connection summaries report the real (empty) state.
 * Forget-device flows against a real bond need hardware UAT.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class SettingsScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val app get() = ApplicationProvider.getApplicationContext<CrossPointPalApplication>()

    private fun openSettingsTab() {
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.waitForIdle()
    }

    /** The preference write happens on a background dispatcher; poll for it. */
    private fun awaitThemeMode(expected: ThemeMode) {
        val deadline = System.currentTimeMillis() + 5_000
        var observed: ThemeMode? = null
        while (System.currentTimeMillis() < deadline) {
            observed = runBlocking { app.container.themeModeStore.themeMode.first() }
            if (observed == expected) {
                return
            }
            Thread.sleep(50)
        }
        assertEquals(expected, observed)
    }

    @Test
    fun themeDialogSelectionPersistsAndClosesTheDialog() {
        openSettingsTab()

        composeRule.onNodeWithText("Theme").performClick()
        // "System" appears both as the row value and as a dialog option.
        assertTrue(composeRule.onAllNodesWithText("System").fetchSemanticsNodes().isNotEmpty())
        composeRule.onNodeWithText("Light").assertIsDisplayed()
        composeRule.onNodeWithText("Dark").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        awaitThemeMode(ThemeMode.DARK)
    }

    @Test
    fun startStateReportsAnEmptyConnectionAndPairing() {
        openSettingsTab()

        composeRule.onNodeWithText("Not connected").performScrollTo().assertIsDisplayed()
        // The value appears both as the pairing summary and on the forget row.
        composeRule.onAllNodesWithText("No bonded device").onFirst().performScrollTo().assertIsDisplayed()
    }

    @Test
    fun forgetDeviceWithoutADeviceDoesNotOpenTheDialog() {
        openSettingsTab()

        composeRule.onNodeWithText("Forget device").performScrollTo().performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Forget CrossPoint Pal?").assertDoesNotExist()
    }
}
