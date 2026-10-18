package fans.crosspoint.pal.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fans.crosspoint.pal.data.DemoCompanionState
import fans.crosspoint.pal.data.ThemeModeStore
import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Immutable UI state of the Settings screen. */
data class SettingsUiState(
    val themeMode: ThemeMode,
    val deviceName: String?,
    val showForgetDialog: Boolean,
) {
    val hasDevice: Boolean
        get() = deviceName != null
}

class SettingsViewModel(
    private val themeModeStore: ThemeModeStore,
    private val demoCompanionState: DemoCompanionState,
) : ViewModel() {

    private val forgetDialogVisible = MutableStateFlow(false)

    private val initial = SettingsUiState(
        themeMode = ThemeMode.SYSTEM,
        deviceName = demoCompanionState.device.value?.name,
        showForgetDialog = false,
    )

    val uiState: StateFlow<SettingsUiState> =
        combine(
            themeModeStore.themeMode,
            demoCompanionState.device,
            forgetDialogVisible,
        ) { themeMode, device, showForgetDialog ->
            SettingsUiState(
                themeMode = themeMode,
                deviceName = device?.name,
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

    fun onForgetRequested() {
        forgetDialogVisible.update { true }
    }

    fun onForgetConfirmed() {
        demoCompanionState.forgetDevice()
        forgetDialogVisible.update { false }
    }

    fun onForgetDismissed() {
        forgetDialogVisible.update { false }
    }
}
