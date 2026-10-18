package fans.crosspoint.pal.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fans.crosspoint.pal.data.DemoCompanionState
import fans.crosspoint.pal.data.SendOutcome
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state of the Send screen. */
data class SendUiState(
    val draft: SendDraft,
    val deviceConnected: Boolean,
) {
    val isSendEnabled: Boolean
        get() = draft.isSendable && deviceConnected
}

/** One-shot feedback events emitted by the Send screen. */
sealed interface SendUiEvent {
    data object ContentSent : SendUiEvent
    data object NoDeviceConnected : SendUiEvent
}

class SendViewModel(private val demoCompanionState: DemoCompanionState) : ViewModel() {

    private val draft = MutableStateFlow(SendDraft())

    private val _events = Channel<SendUiEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val initial = SendUiState(
        draft = draft.value,
        deviceConnected = demoCompanionState.device.value?.connected == true,
    )

    val uiState: StateFlow<SendUiState> =
        combine(
            draft,
            demoCompanionState.device,
        ) { currentDraft, device ->
            SendUiState(draft = currentDraft, deviceConnected = device?.connected == true)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initial,
        )

    fun onTitleChange(value: String) {
        draft.update { it.withTitle(value) }
    }

    fun onBodyChange(value: String) {
        draft.update { it.withBody(value) }
    }

    fun clear() {
        draft.update { it.cleared() }
    }

    /** Sends the draft through the Phase 0 demo path and emits feedback. */
    fun send() {
        val current = draft.value
        if (!current.isSendable) {
            return
        }
        when (demoCompanionState.sendContent(title = current.title, body = current.body)) {
            SendOutcome.Sent -> {
                viewModelScope.launch { _events.send(SendUiEvent.ContentSent) }
                clear()
            }
            SendOutcome.NoDevice ->
                viewModelScope.launch { _events.send(SendUiEvent.NoDeviceConnected) }
        }
    }
}
