package fans.crosspoint.pal.ui.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import fans.crosspoint.pal.ble.AwaitWakeState
import fans.crosspoint.pal.ble.CompanionError
import fans.crosspoint.pal.ble.LinkPhase
import fans.crosspoint.pal.domain.CompanionDeviceSnapshot
import fans.crosspoint.pal.ui.theme.CrossPointPalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * UI behaviour of the Home screen's bounded await-wake diagnostic: the
 * action is offered for a paired unconnected device, the running wait shows
 * its cancellation, and the terminal outcomes render their own messages.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HomeScreenAwaitWakeTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var awaitPressed = 0
    private var cancelPressed = 0

    private fun pairedDevice(connected: Boolean) = CompanionDeviceSnapshot(
        name = "CrossPoint Pal",
        connected = connected,
        paired = true,
        notificationsMirroring = false,
        ready = connected,
        batteryPercent = null,
        lastSeenLabel = "1 min ago",
    )

    private fun setState(
        awaitWake: AwaitWakeState,
        device: CompanionDeviceSnapshot? = pairedDevice(connected = false),
        phase: LinkPhase = LinkPhase.Idle,
    ) {
        awaitPressed = 0
        cancelPressed = 0
        composeRule.setContent {
            CrossPointPalTheme {
                HomeScreen(
                    state = HomeUiState(
                        device = device,
                        lastEvent = null,
                        phase = phase,
                        isScanning = false,
                        scannedDevices = emptyList(),
                        needsPermissions = false,
                        awaitWake = awaitWake,
                    ),
                    requiredPermissions = emptyList(),
                    onSendToPal = {},
                    onConnectPressed = {},
                    onDeviceSelected = {},
                    onCancelScan = {},
                    onReconnectPressed = {},
                    onAwaitWakePressed = { awaitPressed++ },
                    onCancelAwaitWake = { cancelPressed++ },
                    onDisconnectPressed = {},
                    onPermissionsResult = {},
                )
            }
        }
    }

    @Test
    fun `a paired device offers the diagnostic wait`() {
        setState(AwaitWakeState.Idle)

        composeRule.onNodeWithText("Await next wake").assertIsDisplayed()
        composeRule.onNodeWithText("Await next wake").performClick()
        composeRule.waitForIdle()

        assertEquals(1, awaitPressed)
    }

    @Test
    fun `a connected device offers no diagnostic wait`() {
        setState(AwaitWakeState.Idle, device = pairedDevice(connected = true), phase = LinkPhase.Ready)

        composeRule.onNodeWithText("Await next wake").assertDoesNotExist()
    }

    @Test
    fun `a running wait shows its hint and cancellation`() {
        setState(AwaitWakeState.Waiting)

        composeRule.onNodeWithText("Awaiting wake…").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        // The starting action is replaced by the running wait's card.
        composeRule.onNodeWithText("Await next wake").assertDoesNotExist()

        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(1, cancelPressed)
    }

    @Test
    fun `a timed out wait reports the outcome`() {
        setState(AwaitWakeState.TimedOut)

        composeRule.onNodeWithText("No wake detected within 60 seconds.").assertIsDisplayed()
    }

    @Test
    fun `a failed wait shows the typed error`() {
        setState(AwaitWakeState.Failed(CompanionError.NotPaired))

        composeRule.onNodeWithText("No paired Pal is available to wait for.").assertIsDisplayed()
    }

    @Test
    fun `a successful wait reports the ready session`() {
        setState(AwaitWakeState.Connected)

        composeRule.onNodeWithText("The reader woke up and the session is ready.").assertIsDisplayed()
    }
}
