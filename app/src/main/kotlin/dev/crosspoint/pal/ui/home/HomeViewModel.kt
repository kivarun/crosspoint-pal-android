package dev.crosspoint.pal.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.crosspoint.pal.data.DemoCompanionState
import dev.crosspoint.pal.domain.ButtonEventSnapshot
import dev.crosspoint.pal.domain.CompanionDeviceSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Immutable UI state of the Home screen. */
data class HomeUiState(
    val device: CompanionDeviceSnapshot?,
    val lastEvent: ButtonEventSnapshot?,
) {
    val hasDevice: Boolean
        get() = device != null
}

class HomeViewModel(private val demoCompanionState: DemoCompanionState) : ViewModel() {

    private val initial = HomeUiState(
        device = demoCompanionState.device.value,
        lastEvent = demoCompanionState.lastButtonEvent.value,
    )

    val uiState: StateFlow<HomeUiState> =
        combine(
            demoCompanionState.device,
            demoCompanionState.lastButtonEvent,
        ) { device, lastEvent ->
            HomeUiState(device = device, lastEvent = lastEvent)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = initial,
        )

    /** Demo notification sync request from the Home primary action. */
    fun syncNotifications() {
        demoCompanionState.syncNotifications()
    }
}
