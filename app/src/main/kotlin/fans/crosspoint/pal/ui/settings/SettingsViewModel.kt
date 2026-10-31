package fans.crosspoint.pal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fans.crosspoint.pal.ble.CompanionConnectionManager
import fans.crosspoint.pal.ble.LinkPhase
import fans.crosspoint.pal.data.ThemeModeStore
import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state of the Settings screen. */
data class SettingsUiState(
    val themeMode: ThemeMode,
    val deviceName: String?,
    val isConnected: Boolean,
    val isPaired: Boolean,
    val showForgetDialog: Boolean,
) {
    val hasDevice: Boolean
        get() = deviceName != null
}

/** One-shot feedback events emitted by the Settings screen. */
sealed interface SettingsUiEvent {
    data object ForgetCompleted : SettingsUiEvent

    /** The system bond could not be removed automatically. */
    data object ForgetNeedsSystemSettings : SettingsUiEvent
}

class SettingsViewModel(
    private val themeModeStore: ThemeModeStore,
    private val connectionManager: CompanionConnectionManager,
) : ViewModel() {

    private val forgetDialogVisible = MutableStateFlow(false)

    private val _events = Channel<SettingsUiEvent>(capacity = Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val initial = SettingsUiState(
        themeMode = ThemeMode.SYSTEM,
        deviceName = connectionManager.device.value?.name,
        isConnected = connectionManager.linkPhase.value == LinkPhase.Ready,
        isPaired = connectionManager.device.value?.paired == true,
        showForgetDialog = false,
    )

    val uiState: StateFlow<SettingsUiState> =
        combine(
            themeModeStore.themeMode,
            connectionManager.device,
            connectionManager.linkPhase,
            forgetDialogVisible,
        ) { themeMode, device, phase, showForgetDialog ->
            SettingsUiState(
                themeMode = themeMode,
                deviceName = device?.name,
                isConnected = phase == LinkPhase.Ready,
                isPaired = device?.paired == true,
                showForgetDialog = showForgetDialog,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initial,
        )

    /** Persists the selected theme mode. */
    fun onThemeModeSelected(mode: ThemeMode) {
        viewModelScope.launch { themeModeStore.setThemeMode(mode) }
    }

    /** Ends the active session without touching the pairing. */
    fun onDisconnectPressed() {
        connectionManager.disconnect()
    }

    fun onForgetRequested() {
        forgetDialogVisible.update { true }
    }

    fun onForgetConfirmed() {
        forgetDialogVisible.update { false }
        viewModelScope.launch {
            val bondRemoved = connectionManager.forgetDevice()
            _events.send(
                if (bondRemoved) SettingsUiEvent.ForgetCompleted else SettingsUiEvent.ForgetNeedsSystemSettings,
            )
        }
    }

    fun onForgetDismissed() {
        forgetDialogVisible.update { false }
    }
}
