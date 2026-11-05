package fans.crosspoint.pal.ui.home

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
 * action stays visible for every unconnected remembered device and is
 * disabled exactly while an incompatible operation (scan, connect attempt,
 * running wait) is active or the bond is gone; the running wait shows its
 * cancellation, and the terminal outcomes render their own messages.
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
        isScanning: Boolean = false,
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
                        isScanning = isScanning,
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

    /** The action node: the button must be visible in every unconnected state. */
    private fun awaitAction() = composeRule.onAllNodesWithText("Await next wake")

    @Test
    fun `idle with a paired device offers an enabled diagnostic wait`() {
        setState(AwaitWakeState.Idle)

        awaitAction().assertCountEquals(1)
        awaitAction()[0].assertIsDisplayed()
        awaitAction()[0].assertIsEnabled()
        awaitAction()[0].performClick()
        composeRule.waitForIdle()

        assertEquals(1, awaitPressed)
    }

    @Test
    fun `the action stays visible but disabled while a connect attempt runs`() {
        setState(AwaitWakeState.Idle, phase = LinkPhase.Connecting)

        awaitAction().assertCountEquals(1)
        awaitAction()[0].assertIsDisplayed()
        awaitAction()[0].assertIsNotEnabled()
    }

    @Test
    fun `the action stays visible but disabled while a scan runs`() {
        setState(AwaitWakeState.Idle, isScanning = true)

        awaitAction().assertCountEquals(1)
        awaitAction()[0].assertIsDisplayed()
        awaitAction()[0].assertIsNotEnabled()
    }

    @Test
    fun `the action stays visible but disabled while the wait runs`() {
        setState(AwaitWakeState.Waiting)

        awaitAction().assertCountEquals(1)
        awaitAction()[0].assertIsDisplayed()
        awaitAction()[0].assertIsNotEnabled()

        composeRule.onNodeWithText("Awaiting wake…").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.waitForIdle()

        assertEquals(1, cancelPressed)
    }

    @Test
    fun `the action is enabled again after a timeout`() {
        setState(AwaitWakeState.TimedOut)

        awaitAction().assertCountEquals(1)
        awaitAction()[0].assertIsEnabled()
        composeRule.onNodeWithText("No wake detected within 60 seconds.").assertIsDisplayed()
    }

    @Test
    fun `the action is enabled again after a failure`() {
        setState(AwaitWakeState.Failed(CompanionError.NotPaired))

        awaitAction().assertCountEquals(1)
        awaitAction()[0].assertIsEnabled()
        composeRule.onNodeWithText("No paired Pal is available to wait for.").assertIsDisplayed()
    }

    @Test
    fun `the action stays visible but disabled for an unpaired device`() {
        setState(
            AwaitWakeState.Idle,
            device = pairedDevice(connected = false).copy(paired = false),
        )

        awaitAction().assertCountEquals(1)
        awaitAction()[0].assertIsDisplayed()
        awaitAction()[0].assertIsNotEnabled()
    }

    @Test
    fun `a connected device offers no diagnostic wait`() {
        setState(AwaitWakeState.Idle, device = pairedDevice(connected = true), phase = LinkPhase.Ready)

        composeRule.onNodeWithText("Await next wake").assertDoesNotExist()
    }

    @Test
    fun `a successful wait reports the ready session`() {
        setState(AwaitWakeState.Connected)

        composeRule.onNodeWithText("The reader woke up and the session is ready.").assertIsDisplayed()
    }
}
