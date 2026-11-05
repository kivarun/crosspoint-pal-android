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
import fans.crosspoint.pal.domain.CompanionDeviceSnapshot
import fans.crosspoint.pal.ble.LinkPhase
import fans.crosspoint.pal.ui.theme.CrossPointPalTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Regression tests for the reconnect affordance of a remembered device.
 *
 * After a process restart the link phase is Idle; with only the previous
 * entry points (Connect device for no device, Reconnect for a Failed phase)
 * a remembered device had no way back into a session, and the only route to
 * a working state was Forget Device plus a fresh scan and pairing.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class HomeScreenReconnectTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun rememberedDevice(connected: Boolean) = CompanionDeviceSnapshot(
        name = "CrossPoint Pal",
        connected = connected,
        paired = true,
        notificationsMirroring = false,
        ready = connected,
        batteryPercent = null,
        lastSeenLabel = "just now",
    )

    private fun setState(
        device: CompanionDeviceSnapshot?,
        phase: LinkPhase,
        isScanning: Boolean = false,
        awaitWake: AwaitWakeState = AwaitWakeState.Idle,
    ) {
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
                    onReconnectPressed = { reconnectPressed++ },
                    onAwaitWakePressed = {},
                    onCancelAwaitWake = {},
                    onDisconnectPressed = {},
                    onPermissionsResult = {},
                )
            }
        }
    }

    private var reconnectPressed = 0

    @Test
    fun `a remembered device offers reconnect after a process restart`() {
        reconnectPressed = 0
        setState(rememberedDevice(connected = false), LinkPhase.Idle)

        composeRule.onNodeWithText("Reconnect").assertIsDisplayed()
        composeRule.onNodeWithText("Reconnect").performClick()

        assertEquals(1, reconnectPressed)
    }

    @Test
    fun `a failed phase shows exactly one reconnect button`() {
        reconnectPressed = 0
        setState(rememberedDevice(connected = false), LinkPhase.Failed(fans.crosspoint.pal.ble.CompanionError.LinkLost))

        composeRule.onAllNodesWithText("Reconnect").assertCountEquals(1)
        composeRule.onAllNodesWithText("Reconnect")[0].performClick()

        assertEquals(1, reconnectPressed)
    }

    @Test
    fun `a connected device offers disconnect instead of reconnect`() {
        setState(rememberedDevice(connected = true), LinkPhase.Ready)

        composeRule.onNodeWithText("Disconnect").assertIsDisplayed()
        composeRule.onAllNodesWithText("Reconnect").assertCountEquals(0)
    }

    @Test
    fun `reconnect stays visible but disabled while a connect attempt runs`() {
        setState(rememberedDevice(connected = false), LinkPhase.Connecting)

        composeRule.onAllNodesWithText("Reconnect")[0].assertIsNotEnabled()
    }

    @Test
    fun `reconnect stays visible but disabled while a scan runs`() {
        setState(rememberedDevice(connected = false), LinkPhase.Idle, isScanning = true)

        composeRule.onAllNodesWithText("Reconnect")[0].assertIsNotEnabled()
    }

    @Test
    fun `reconnect stays visible but disabled while the wait runs`() {
        setState(rememberedDevice(connected = false), LinkPhase.Idle, awaitWake = AwaitWakeState.Waiting)

        composeRule.onAllNodesWithText("Reconnect")[0].assertIsNotEnabled()
    }

    @Test
    fun `reconnect is enabled again after the wait times out`() {
        setState(rememberedDevice(connected = false), LinkPhase.Idle, awaitWake = AwaitWakeState.TimedOut)

        composeRule.onAllNodesWithText("Reconnect")[0].assertIsEnabled()
    }

    @Test
    fun `reconnect is enabled again after the wait fails`() {
        setState(
            rememberedDevice(connected = false),
            LinkPhase.Idle,
            awaitWake = AwaitWakeState.Failed(fans.crosspoint.pal.ble.CompanionError.ScanFailed),
        )

        composeRule.onAllNodesWithText("Reconnect")[0].assertIsEnabled()
    }
}
