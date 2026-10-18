package fans.crosspoint.pal.ui.home

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewLightDark
import fans.crosspoint.pal.R
import fans.crosspoint.pal.domain.ButtonEventSnapshot
import fans.crosspoint.pal.domain.CompanionDeviceSnapshot
import fans.crosspoint.pal.ui.components.PalCard
import fans.crosspoint.pal.ui.components.ScreenHeader
import fans.crosspoint.pal.ui.components.SectionHeader
import fans.crosspoint.pal.ui.components.StatusDot
import fans.crosspoint.pal.ui.theme.CrossPointPalTheme
import fans.crosspoint.pal.ui.theme.palStatusColors
import fans.crosspoint.pal.ui.theme.Spacing

/**
 * Home / device screen: companion status, primary actions, and the most
 * recent device button event.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onSendToPal: () -> Unit,
    onSyncNotifications: () -> Unit,
    onConnectDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            onConnectDevice = onConnectDevice,
        )

        if (state.hasDevice) {
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
            OutlinedButton(
                onClick = onSyncNotifications,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.home_action_sync_notifications))
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
    onConnectDevice: () -> Unit,
) {
    val device = state.device
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
                    onClick = onConnectDevice,
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.m),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xl),
                ) {
                    Text(
                        text = stringResource(R.string.home_battery_label, device.batteryPercent),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(R.string.home_last_seen, device.lastSeenLabel),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (device.connected) {
                    StatusIndicators(device = device)
                }
            }
        }
    }
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

@PreviewLightDark
@Composable
private fun HomeScreenPreviewConnected() {
    CrossPointPalTheme {
        HomeScreen(
            state = HomeUiState(
                device = CompanionDeviceSnapshot(
                    name = "X4 Classic",
                    connected = true,
                    paired = true,
                    notificationsMirroring = true,
                    ready = true,
                    batteryPercent = 73,
                    lastSeenLabel = "now",
                ),
                lastEvent = ButtonEventSnapshot(buttonLabel = "Right button", timeLabel = "Just now"),
            ),
            onSendToPal = {},
            onSyncNotifications = {},
            onConnectDevice = {},
        )
    }
}

@PreviewLightDark
@Composable
private fun HomeScreenPreviewNoDevice() {
    CrossPointPalTheme {
        HomeScreen(
            state = HomeUiState(device = null, lastEvent = null),
            onSendToPal = {},
            onSyncNotifications = {},
            onConnectDevice = {},
        )
    }
}
