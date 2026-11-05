package fans.crosspoint.pal.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fans.crosspoint.pal.R
import fans.crosspoint.pal.ble.AwaitWakeState
import fans.crosspoint.pal.ble.CompanionError
import fans.crosspoint.pal.ble.LinkPhase
import fans.crosspoint.pal.ble.ScannedDevice
import fans.crosspoint.pal.domain.ButtonEventSnapshot
import fans.crosspoint.pal.domain.CompanionDeviceSnapshot
import fans.crosspoint.pal.ui.components.PalCard
import fans.crosspoint.pal.ui.components.ScreenHeader
import fans.crosspoint.pal.ui.components.SectionHeader
import fans.crosspoint.pal.ui.components.StatusDot
import fans.crosspoint.pal.ui.components.rememberCompanionErrorMessages
import fans.crosspoint.pal.ui.theme.CrossPointPalTheme
import fans.crosspoint.pal.ui.theme.palStatusColors
import fans.crosspoint.pal.ui.theme.Spacing

/**
 * Home / device screen: companion status, primary actions, the bounded
 * scan-and-connect flow, and the most recent device button event.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    requiredPermissions: List<String>,
    onSendToPal: () -> Unit,
    onConnectPressed: () -> Unit,
    onDeviceSelected: (ScannedDevice) -> Unit,
    onCancelScan: () -> Unit,
    onReconnectPressed: () -> Unit,
    onAwaitWakePressed: () -> Unit,
    onCancelAwaitWake: () -> Unit,
    onDisconnectPressed: () -> Unit,
    onPermissionsResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        onPermissionsResult(grants.values.all { it })
    }
    LaunchedEffect(state.needsPermissions) {
        if (state.needsPermissions && requiredPermissions.isNotEmpty()) {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ScreenHeader(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.home_subtitle),
        )

        DeviceCard(
            state = state,
            onConnectPressed = onConnectPressed,
            onReconnectPressed = onReconnectPressed,
            onAwaitWakePressed = onAwaitWakePressed,
            onDisconnectPressed = onDisconnectPressed,
        )

        if (state.awaitWake != AwaitWakeState.Idle) {
            AwaitWakeCard(
                state = state.awaitWake,
                onCancel = onCancelAwaitWake,
            )
        }

        if (state.isScanning) {
            ScanCard(
                devices = state.scannedDevices,
                onSelect = onDeviceSelected,
                onCancel = onCancelScan,
            )
        }

        if (state.hasDevice && state.phase == LinkPhase.Ready) {
            Button(
                onClick = onSendToPal,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(stringResource(R.string.home_action_send_to_pal))
            }
        }

        state.lastEvent?.let { event ->
            LastEventCard(event)
        }
    }
}

@Composable
private fun DeviceCard(
    state: HomeUiState,
    onConnectPressed: () -> Unit,
    onReconnectPressed: () -> Unit,
    onAwaitWakePressed: () -> Unit,
    onDisconnectPressed: () -> Unit,
) {
    val device = state.device
    val phase = state.phase
    val failure = (phase as? LinkPhase.Failed)?.error
    val errorMessage = rememberCompanionErrorMessages()
    PalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            if (device == null) {
                Text(
                    text = stringResource(R.string.home_no_device_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.home_no_device_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
                Button(
                    onClick = onConnectPressed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.l),
                ) {
                    Text(stringResource(R.string.home_action_connect))
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    ConnectionBadge(connected = device.connected)
                }
                Text(
                    text = stringResource(R.string.home_last_seen, device.lastSeenLabel),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.m),
                )
                if (device.connected) {
                    StatusIndicators(device = device)
                    OutlinedButton(
                        onClick = onDisconnectPressed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.l),
                    ) {
                        Text(stringResource(R.string.home_action_disconnect))
                    }
                } else if (!state.isConnecting) {
                    // A remembered device must always offer its way back into
                    // a session: after a process restart the phase is Idle,
                    // and without this button the only route to a working
                    // state was Forget Device plus a fresh scan and pairing.
                    Button(
                        onClick = onReconnectPressed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.l),
                    ) {
                        Text(stringResource(R.string.home_action_reconnect))
                    }
                    // The bounded diagnostic wait only makes sense for a
                    // paired, currently unconnected reader; the session, the
                    // manual scan, and a running wait exclude it.
                    if (device.paired && !state.isScanning &&
                        state.awaitWake == AwaitWakeState.Idle
                    ) {
                        OutlinedButton(
                            onClick = onAwaitWakePressed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = Spacing.s),
                        ) {
                            Text(stringResource(R.string.home_action_await_wake))
                        }
                    }
                }
            }

            if (state.isConnecting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = phaseLabel(phase),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            failure?.let { error ->
                Text(
                    text = errorMessage(error),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.palStatusColors.destructive,
                    modifier = Modifier.padding(top = Spacing.m),
                )
            }
        }
    }
}

@Composable
private fun AwaitWakeCard(
    state: AwaitWakeState,
    onCancel: () -> Unit,
) {
    val errorMessage = rememberCompanionErrorMessages()
    PalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            when (state) {
                AwaitWakeState.Idle -> Unit
                AwaitWakeState.Waiting -> {
                    Text(
                        text = stringResource(R.string.home_await_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.home_await_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = Spacing.s),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.m),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.m),
                    ) {
                        LinearProgressIndicator(modifier = Modifier.weight(1f))
                        TextButton(onClick = onCancel) {
                            Text(stringResource(R.string.home_action_cancel))
                        }
                    }
                }
                AwaitWakeState.TimedOut ->
                    Text(
                        text = stringResource(R.string.home_await_timed_out),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                AwaitWakeState.Connected ->
                    Text(
                        text = stringResource(R.string.home_await_connected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                is AwaitWakeState.Failed ->
                    Text(
                        text = errorMessage(state.error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.palStatusColors.destructive,
                    )
            }
        }
    }
}

@Composable
private fun ScanCard(
    devices: List<ScannedDevice>,
    onSelect: (ScannedDevice) -> Unit,
    onCancel: () -> Unit,
) {
    PalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            Text(
                text = stringResource(R.string.home_scanning_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (devices.isEmpty()) {
                Text(
                    text = stringResource(R.string.home_scanning_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            } else {
                Column(modifier = Modifier.padding(top = Spacing.s)) {
                    devices.forEach { device ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(device) }
                                .padding(vertical = Spacing.s),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = device.name
                                    ?: stringResource(R.string.home_scan_unnamed_device),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = device.address,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            TextButton(
                onClick = onCancel,
                modifier = Modifier.padding(top = Spacing.s),
            ) {
                Text(stringResource(R.string.home_action_cancel))
            }
        }
    }
}

@Composable
private fun phaseLabel(phase: LinkPhase): String = when (phase) {
    LinkPhase.Bonding -> stringResource(R.string.home_phase_bonding)
    LinkPhase.Connecting -> stringResource(R.string.home_phase_connecting)
    LinkPhase.NegotiatingMtu -> stringResource(R.string.home_phase_negotiating)
    LinkPhase.Discovering -> stringResource(R.string.home_phase_discovering)
    LinkPhase.Subscribing -> stringResource(R.string.home_phase_subscribing)
    LinkPhase.Handshaking -> stringResource(R.string.home_phase_handshaking)
    else -> stringResource(R.string.home_phase_connecting)
}

@Composable
private fun ConnectionBadge(connected: Boolean) {
    val statusColors = MaterialTheme.palStatusColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        StatusDot(
            color = if (connected) statusColors.connected else statusColors.warning,
        )
        Text(
            text = stringResource(
                if (connected) R.string.home_device_connected else R.string.home_device_disconnected,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusIndicators(device: CompanionDeviceSnapshot) {
    val statusColors = MaterialTheme.palStatusColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.l),
        horizontalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Indicator(label = stringResource(R.string.home_indicator_paired), active = device.paired)
        Indicator(
            label = stringResource(R.string.home_indicator_notifications_on),
            active = device.notificationsMirroring,
        )
        Indicator(label = stringResource(R.string.home_indicator_ready), active = device.ready)
    }
}

@Composable
private fun Indicator(label: String, active: Boolean) {
    val statusColors = MaterialTheme.palStatusColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        StatusDot(color = if (active) statusColors.connected else statusColors.warning)
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LastEventCard(event: ButtonEventSnapshot) {
    SectionHeader(text = stringResource(R.string.home_last_event_label))
    PalCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(Spacing.l)) {
            Text(
                text = event.buttonLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = event.timeLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.xs),
            )
        }
    }
}

/** Collects one-shot Home events and reports them to the host scaffold. */
@Composable
fun HomeEventsEffect(
    events: kotlinx.coroutines.flow.Flow<HomeUiEvent>,
    onError: (String) -> Unit,
) {
    val message = rememberCompanionErrorMessages()
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                is HomeUiEvent.Error -> onError(message(event.error))
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreviewConnected() {
    CrossPointPalTheme {
        HomeScreen(
            state = HomeUiState(
                device = CompanionDeviceSnapshot(
                    name = "CrossPoint Pal",
                    connected = true,
                    paired = true,
                    notificationsMirroring = false,
                    ready = true,
                    batteryPercent = null,
                    lastSeenLabel = "just now",
                ),
                lastEvent = ButtonEventSnapshot(buttonLabel = "Right button", timeLabel = "14:03:59"),
                phase = LinkPhase.Ready,
                isScanning = false,
                scannedDevices = emptyList(),
                needsPermissions = false,
            ),
            requiredPermissions = emptyList(),
            onSendToPal = {},
            onConnectPressed = {},
            onDeviceSelected = {},
            onCancelScan = {},
            onReconnectPressed = {},
            onAwaitWakePressed = {},
            onCancelAwaitWake = {},
            onDisconnectPressed = {},
            onPermissionsResult = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreviewNoDevice() {
    CrossPointPalTheme {
        HomeScreen(
            state = HomeUiState(
                device = null,
                lastEvent = null,
                phase = LinkPhase.Idle,
                isScanning = false,
                scannedDevices = emptyList(),
                needsPermissions = false,
            ),
            requiredPermissions = emptyList(),
            onSendToPal = {},
            onConnectPressed = {},
            onDeviceSelected = {},
            onCancelScan = {},
            onReconnectPressed = {},
            onAwaitWakePressed = {},
            onCancelAwaitWake = {},
            onDisconnectPressed = {},
            onPermissionsResult = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreviewScanning() {
    CrossPointPalTheme {
        HomeScreen(
            state = HomeUiState(
                device = null,
                lastEvent = null,
                phase = LinkPhase.Idle,
                isScanning = true,
                scannedDevices = listOf(ScannedDevice("AA:BB:CC:DD:EE:FF", "CrossPoint Pal")),
                needsPermissions = false,
            ),
            requiredPermissions = emptyList(),
            onSendToPal = {},
            onConnectPressed = {},
            onDeviceSelected = {},
            onCancelScan = {},
            onReconnectPressed = {},
            onAwaitWakePressed = {},
            onCancelAwaitWake = {},
            onDisconnectPressed = {},
            onPermissionsResult = {},
        )
    }
}
