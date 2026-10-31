package fans.crosspoint.pal.ui.send

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fans.crosspoint.pal.ble.CompanionConnectionManager
import fans.crosspoint.pal.ble.CompanionError
import fans.crosspoint.pal.ble.LinkPhase
import fans.crosspoint.pal.ble.SendResult
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
    val isSessionReady: Boolean,
    val isSending: Boolean,
) {
    val isSendEnabled: Boolean
        get() = draft.isSendable && isSessionReady && !isSending
}

/** One-shot feedback events emitted by the Send screen. */
sealed interface SendUiEvent {
    data object ContentSent : SendUiEvent
    data object NoSessionReady : SendUiEvent
    data class SendFailed(val error: CompanionError) : SendUiEvent
}

class SendViewModel(private val connectionManager: CompanionConnectionManager) : ViewModel() {

    private val draft = MutableStateFlow(SendDraft())
    private val sending = MutableStateFlow(false)

    private val _events = Channel<SendUiEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val initial = SendUiState(
        draft = draft.value,
        isSessionReady = connectionManager.linkPhase.value == LinkPhase.Ready,
        isSending = false,
    )

    val uiState: StateFlow<SendUiState> =
        combine(
            draft,
            sending,
            connectionManager.linkPhase,
        ) { currentDraft, isSending, phase ->
            SendUiState(
                draft = currentDraft,
                isSessionReady = phase == LinkPhase.Ready,
                isSending = isSending,
            )
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

    /** Sends the draft to the device and reports the acknowledged outcome. */
    fun send() {
        val current = draft.value
        if (!current.isSendable || sending.value) {
            return
        }
        sending.value = true
        viewModelScope.launch {
            val result = try {
                connectionManager.sendContent(title = current.title, body = current.body)
            } finally {
                sending.value = false
            }
            when (result) {
                SendResult.Sent -> {
                    _events.send(SendUiEvent.ContentSent)
                    clear()
                }
                SendResult.NotReady -> _events.send(SendUiEvent.NoSessionReady)
                is SendResult.Failed -> _events.send(SendUiEvent.SendFailed(result.error))
            }
        }
    }
}
