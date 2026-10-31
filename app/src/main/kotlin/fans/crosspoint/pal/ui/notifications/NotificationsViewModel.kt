package fans.crosspoint.pal.ui.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fans.crosspoint.pal.data.DemoNotificationState
import fans.crosspoint.pal.domain.NotificationMirroringSettings
import fans.crosspoint.pal.domain.NotificationSource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Immutable UI state of the Notifications screen. */
data class NotificationsUiState(
    val settings: NotificationMirroringSettings,
)

class NotificationsViewModel(private val demoNotificationState: DemoNotificationState) : ViewModel() {

    val uiState: StateFlow<NotificationsUiState> = demoNotificationState.notificationSettings
        .map { NotificationsUiState(settings = it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = NotificationsUiState(demoNotificationState.notificationSettings.value),
        )

    fun onMasterToggle(enabled: Boolean) {
        demoNotificationState.setMasterMirroring(enabled)
    }

    fun onSourceToggle(source: NotificationSource) {
        demoNotificationState.toggleSource(source)
    }

    /** Demo sync request from the Sync now action. */
    fun syncNow() {
        demoNotificationState.syncNotifications()
    }
}
