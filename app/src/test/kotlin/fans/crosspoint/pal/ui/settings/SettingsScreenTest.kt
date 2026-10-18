package fans.crosspoint.pal.ui.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import fans.crosspoint.pal.CrossPointPalApplication
import fans.crosspoint.pal.MainActivity
import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun forgetDeviceRequiresConfirmationAndThenClearsTheState() {
        openSettingsTab()

        composeRule.onNodeWithText("Forget device").performScrollTo().performClick()
        composeRule.onNodeWithText("Forget X4 Classic?").assertIsDisplayed()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Forget X4 Classic?").assertDoesNotExist()
        assertEquals("X4 Classic", app.container.demoCompanionState.device.value?.name)

        composeRule.onNodeWithText("Forget device").performScrollTo().performClick()
        composeRule.onNodeWithText("Forget").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Forget X4 Classic?").assertDoesNotExist()
        assertNull(app.container.demoCompanionState.device.value)
    }

    @Test
    fun connectionAndPairingSummariesReflectTheDevice() {
        openSettingsTab()

        composeRule.onNodeWithText("Connected to X4 Classic").assertIsDisplayed()
        composeRule.onNodeWithText("Bonded device: X4 Classic").assertIsDisplayed()
    }
}
