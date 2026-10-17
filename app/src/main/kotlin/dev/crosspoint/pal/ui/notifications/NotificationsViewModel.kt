package dev.crosspoint.pal.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.crosspoint.pal.data.DemoCompanionState
import dev.crosspoint.pal.domain.NotificationMirroringSettings
import dev.crosspoint.pal.domain.NotificationSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Immutable UI state of the Notifications screen. */
data class NotificationsUiState(
    val settings: NotificationMirroringSettings,
)

class NotificationsViewModel(private val demoCompanionState: DemoCompanionState) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = demoCompanionState.notificationSettings
        .map { NotificationsUiState(settings = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationsUiState(demoCompanionState.notificationSettings.value),
        )

    fun onMasterToggle(enabled: Boolean) {
        demoCompanionState.setMasterMirroring(enabled)
    }

    fun onSourceToggle(source: NotificationSource) {
        demoCompanionState.toggleSource(source)
    }

    /** Demo sync request from the Sync now action. */
    fun syncNow() {
        demoCompanionState.syncNotifications()
    }
}
