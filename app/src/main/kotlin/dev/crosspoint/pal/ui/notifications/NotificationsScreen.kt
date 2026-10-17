package dev.crosspoint.pal.ui.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.crosspoint.pal.R
import dev.crosspoint.pal.domain.NotificationMirroringSettings
import dev.crosspoint.pal.domain.NotificationSource
import dev.crosspoint.pal.ui.components.PalCard
import dev.crosspoint.pal.ui.components.ScreenHeader
import dev.crosspoint.pal.ui.components.SectionHeader
import dev.crosspoint.pal.ui.theme.CrossPointPalTheme
import dev.crosspoint.pal.ui.theme.Spacing

/**
 * Notifications screen: mirroring master switch, demo app sources, and the
 * demo sync status. No Android notification access is involved in Phase 0.
 */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onMasterToggle: (Boolean) -> Unit,
    onSourceToggle: (NotificationSource) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ScreenHeader(title = stringResource(R.string.notifications_title))

        PalCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = state.settings.masterEnabled,
                        role = Role.Switch,
                        onValueChange = onMasterToggle,
                    )
                    .padding(Spacing.l),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.notifications_master_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Switch(
                    checked = state.settings.masterEnabled,
                    onCheckedChange = null,
                )
            }
        }

        SectionHeader(text = stringResource(R.string.notifications_sources_header))
        Text(
            text = stringResource(R.string.notifications_sources_caption),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = Spacing.xs),
        )
        PalCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                NotificationSource.entries.forEachIndexed { index, source ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant,
                            thickness = 1.dp,
                        )
                    }
                    SourceRow(
                        label = sourceLabel(source),
                        enabled = state.settings.sourceEnabled.getValue(source),
                        onToggle = { onSourceToggle(source) },
                    )
                }
            }
        }

        PalCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(Spacing.l)) {
                Text(
                    text = stringResource(
                        R.string.notifications_last_sync,
                        state.settings.lastSyncLabel,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = pluralStringResource(
                        R.plurals.notifications_sent_count,
                        state.settings.sentCount,
                        state.settings.sentCount,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Button(
                    onClick = onSyncNow,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.l),
                ) {
                    Text(stringResource(R.string.notifications_action_sync_now))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    label: String,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = enabled,
                role = Role.Switch,
                onValueChange = { onToggle() },
            )
            .padding(horizontal = Spacing.l, vertical = Spacing.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = enabled, onCheckedChange = null)
    }
}

@Composable
private fun sourceLabel(source: NotificationSource): String = when (source) {
    NotificationSource.MESSAGES -> stringResource(R.string.source_messages)
    NotificationSource.CALENDAR -> stringResource(R.string.source_calendar)
    NotificationSource.TASKS -> stringResource(R.string.source_tasks)
}

@PreviewLightDark
@Composable
private fun NotificationsScreenPreview() {
    CrossPointPalTheme {
        NotificationsScreen(
            state = NotificationsUiState(
                settings = NotificationMirroringSettings(
                    masterEnabled = true,
                    sourceEnabled = mapOf(
                        NotificationSource.MESSAGES to true,
                        NotificationSource.CALENDAR to true,
                        NotificationSource.TASKS to false,
                    ),
                    lastSyncLabel = "2 min ago",
                    sentCount = 3,
                ),
            ),
            onMasterToggle = {},
            onSourceToggle = {},
            onSyncNow = {},
        )
    }
}
