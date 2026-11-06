package fans.crosspoint.pal.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import fans.crosspoint.pal.R
import fans.crosspoint.pal.domain.ThemeMode
import fans.crosspoint.pal.ui.components.PalCard
import fans.crosspoint.pal.ui.components.ScreenHeader
import fans.crosspoint.pal.ui.components.SectionHeader
import fans.crosspoint.pal.ui.theme.CrossPointPalTheme
import fans.crosspoint.pal.ui.theme.palStatusColors
import fans.crosspoint.pal.ui.theme.Spacing

/**
 * Settings screen: theme preference, connection and pairing summaries,
 * privacy pointer, diagnostic export, about, and the destructive forget
 * device action.
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDisconnectPressed: () -> Unit,
    onForgetRequested: () -> Unit,
    onForgetConfirmed: () -> Unit,
    onForgetDismissed: () -> Unit,
    onOpenNotifications: () -> Unit,
    onExportDiagnosticsRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var themeDialogVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.s),
    ) {
        ScreenHeader(title = stringResource(R.string.settings_title))

        SectionHeader(text = stringResource(R.string.settings_theme_section))
        PalCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = stringResource(R.string.settings_theme_label),
                value = themeModeLabel(state.themeMode),
                onClick = { themeDialogVisible = true },
            )
        }

        SectionHeader(text = stringResource(R.string.settings_connection_section))
        PalCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = stringResource(R.string.settings_connection_label),
                value = if (state.isConnected) {
                    stringResource(R.string.settings_connection_connected, state.deviceName.orEmpty())
                } else {
                    stringResource(R.string.settings_connection_none)
                },
                onClick = null,
            )
            if (state.isConnected) {
                SettingsRow(
                    title = stringResource(R.string.settings_action_disconnect),
                    value = "",
                    onClick = onDisconnectPressed,
                )
            }
        }

        SectionHeader(text = stringResource(R.string.settings_pairing_section))
        PalCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = stringResource(R.string.settings_pairing_label),
                value = if (state.isPaired) {
                    stringResource(R.string.settings_pairing_bonded, state.deviceName.orEmpty())
                } else {
                    stringResource(R.string.settings_pairing_none)
                },
                onClick = null,
            )
        }

        SectionHeader(text = stringResource(R.string.settings_privacy_section))
        PalCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = stringResource(R.string.settings_privacy_label),
                value = stringResource(R.string.settings_privacy_hint),
                onClick = onOpenNotifications,
            )
        }

        SectionHeader(text = stringResource(R.string.settings_diagnostics_section))
        PalCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = stringResource(R.string.settings_diagnostics_label),
                value = stringResource(R.string.settings_diagnostics_hint),
                onClick = onExportDiagnosticsRequested,
            )
        }

        SectionHeader(text = stringResource(R.string.settings_about_section))
        PalCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = stringResource(R.string.settings_about_label),
                value = versionLabel(),
                onClick = null,
            )
        }

        SectionHeader(text = stringResource(R.string.settings_danger_section))
        PalCard(modifier = Modifier.fillMaxWidth()) {
            SettingsRow(
                title = stringResource(R.string.settings_forget_device),
                value = if (state.hasDevice) {
                    state.deviceName.orEmpty()
                } else {
                    stringResource(R.string.settings_pairing_none)
                },
                onClick = if (state.hasDevice) onForgetRequested else null,
                titleColor = MaterialTheme.palStatusColors.destructive,
            )
        }
    }

    if (themeDialogVisible) {
        ThemeModeDialog(
            selected = state.themeMode,
            onSelect = { mode ->
                onThemeModeSelected(mode)
                themeDialogVisible = false
            },
            onDismiss = { themeDialogVisible = false },
        )
    }

    if (state.showForgetDialog && state.deviceName != null) {
        ForgetDeviceDialog(
            deviceName = state.deviceName,
            onConfirm = onForgetConfirmed,
            onDismiss = onForgetDismissed,
        )
    }
}

/** Collects one-shot Settings events and reports them to the host scaffold. */
@Composable
fun SettingsEventsEffect(
    events: kotlinx.coroutines.flow.Flow<SettingsUiEvent>,
    onForgetCompleted: (String) -> Unit,
    onForgetNeedsSystemSettings: (String) -> Unit,
    onExportReady: (android.net.Uri) -> Unit,
    onExportFailed: (String) -> Unit,
) {
    val completedMessage = stringResource(R.string.settings_forget_completed)
    val bondFailedMessage = stringResource(R.string.settings_forget_bond_failed)
    val exportFailedMessage = stringResource(R.string.settings_diagnostics_export_failed)
    val shareTitle = stringResource(R.string.settings_diagnostics_share_title)
    val context = LocalContext.current
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                SettingsUiEvent.ForgetCompleted -> onForgetCompleted(completedMessage)
                SettingsUiEvent.ForgetNeedsSystemSettings -> onForgetNeedsSystemSettings(bondFailedMessage)
                is SettingsUiEvent.ExportReady -> {
                    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(android.content.Intent.EXTRA_STREAM, event.uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = android.content.Intent.createChooser(share, shareTitle)
                    runCatching { context.startActivity(chooser) }
                    onExportReady(event.uri)
                }
                SettingsUiEvent.ExportFailed -> onExportFailed(exportFailedMessage)
            }
        }
    }
}

@Composable
private fun ForgetDeviceDialog(
    deviceName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_forget_dialog_title, deviceName)) },
        text = { Text(stringResource(R.string.settings_forget_dialog_text)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.settings_forget_confirm),
                    color = MaterialTheme.palStatusColors.destructive,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun ThemeModeDialog(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_theme_dialog_title)) },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = mode == selected,
                                role = Role.RadioButton,
                                onClick = { onSelect(mode) },
                            )
                            .padding(vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == selected,
                            onClick = null,
                        )
                        Text(
                            text = themeModeLabel(mode),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = Spacing.m),
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.settings_cancel))
            }
        },
    )
}

@Composable
private fun SettingsRow(
    title: String,
    value: String,
    onClick: (() -> Unit)?,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(horizontal = Spacing.l, vertical = Spacing.l),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** App version from the installed package; the single source of truth. */
@Composable
private fun versionLabel(): String {
    val context = LocalContext.current
    val version = remember(context) {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    return stringResource(R.string.settings_version, version)
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.settings_theme_value_system)
    ThemeMode.LIGHT -> stringResource(R.string.settings_theme_value_light)
    ThemeMode.DARK -> stringResource(R.string.settings_theme_value_dark)
}

@PreviewLightDark
@Composable
private fun SettingsScreenPreview() {
    CrossPointPalTheme {
        SettingsScreen(
            state = SettingsUiState(
                themeMode = ThemeMode.SYSTEM,
                deviceName = "CrossPoint Pal",
                isConnected = true,
                isPaired = true,
                showForgetDialog = false,
            ),
            onThemeModeSelected = {},
            onDisconnectPressed = {},
            onForgetRequested = {},
            onForgetConfirmed = {},
            onForgetDismissed = {},
            onOpenNotifications = {},
            onExportDiagnosticsRequested = {},
        )
    }
}
