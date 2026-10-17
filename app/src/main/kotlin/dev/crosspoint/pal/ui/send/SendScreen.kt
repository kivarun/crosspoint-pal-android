package dev.crosspoint.pal.ui.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import dev.crosspoint.pal.R
import dev.crosspoint.pal.ui.components.PalCard
import dev.crosspoint.pal.ui.components.ScreenHeader
import dev.crosspoint.pal.ui.components.SectionHeader
import dev.crosspoint.pal.ui.theme.CrossPointPalTheme
import dev.crosspoint.pal.ui.theme.Spacing

/**
 * Send to Pal screen: bounded content draft, approximate e-ink preview, and
 * demo send action.
 */
@Composable
fun SendScreen(
    state: SendUiState,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onClear: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.l, vertical = Spacing.xl),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        ScreenHeader(title = stringResource(R.string.send_title))

        OutlinedTextField(
            value = state.draft.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.send_field_title_label)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("send_title_input"),
        )
        Text(
            text = stringResource(
                R.string.send_char_count,
                state.draft.titleCount,
                SendDraft.TITLE_MAX_LENGTH,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )

        OutlinedTextField(
            value = state.draft.body,
            onValueChange = onBodyChange,
            label = { Text(stringResource(R.string.send_field_body_label)) },
            minLines = 5,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("send_body_input"),
        )
        Text(
            text = stringResource(
                R.string.send_char_count,
                state.draft.bodyCount,
                SendDraft.BODY_MAX_LENGTH,
            ),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.End,
        )

        SectionHeader(text = stringResource(R.string.send_preview_title))
        EinkPreview(
            title = state.draft.title,
            body = state.draft.body,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.m, Alignment.End),
        ) {
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.send_action_clear))
            }
            Button(
                onClick = onSend,
                enabled = state.isSendEnabled,
                modifier = Modifier.testTag("send_action_send"),
            ) {
                Text(stringResource(R.string.send_action_send))
            }
        }
    }
}

/**
 * Approximate presentation of how the draft would appear on the e-ink
 * display. It is a plain Compose component, not a firmware renderer or a
 * bitmap emulation.
 */
@Composable
private fun EinkPreview(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    PalCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 160.dp)
                .padding(Spacing.l),
        ) {
            if (title.isBlank() && body.isBlank()) {
                Text(
                    text = stringResource(R.string.send_preview_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = title.ifBlank { stringResource(R.string.send_preview_untitled) },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 10,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.s),
                )
            }
        }
    }
}

/** Collects one-shot send events and reports them to the host scaffold. */
@Composable
fun SendEventsEffect(
    events: kotlinx.coroutines.flow.Flow<SendUiEvent>,
    onContentSent: (String) -> Unit,
    onNoDevice: (String) -> Unit,
) {
    val sentMessage = stringResource(R.string.send_sent_message)
    val noDeviceMessage = stringResource(R.string.send_no_device_message)
    LaunchedEffect(events) {
        events.collect { event ->
            when (event) {
                SendUiEvent.ContentSent -> onContentSent(sentMessage)
                SendUiEvent.NoDeviceConnected -> onNoDevice(noDeviceMessage)
            }
        }
    }
}

@PreviewLightDark
@Composable
private fun SendScreenPreview() {
    CrossPointPalTheme {
        SendScreen(
            state = SendUiState(
                draft = SendDraft(title = "Morning pages", body = "Quiet is the default state of a good reader."),
                deviceConnected = true,
            ),
            onTitleChange = {},
            onBodyChange = {},
            onClear = {},
            onSend = {},
        )
    }
}
