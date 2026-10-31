package fans.crosspoint.pal.data

import fans.crosspoint.pal.domain.NotificationMirroringSettings
import fans.crosspoint.pal.domain.NotificationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory demo state for the Notifications screen. Every value here is
 * presentation data; no notification listener, transport, or persistence is
 * involved. The real companion device state lives in the BLE connection
 * manager, not here.
 */
class DemoNotificationState {

    private val notificationSettingsInternal = MutableStateFlow(DEMO_NOTIFICATION_SETTINGS)
    val notificationSettings: StateFlow<NotificationMirroringSettings> =
        notificationSettingsInternal.asStateFlow()

    /** Demo notification sync: refreshes the "last sync" label and counters. */
    fun syncNotifications() {
        notificationSettingsInternal.update { settings ->
            settings.copy(lastSyncLabel = "Just now", sentCount = settings.sentCount + DEMO_SYNC_BATCH)
        }
    }

    fun setMasterMirroring(enabled: Boolean) {
        notificationSettingsInternal.update { it.withMaster(enabled) }
    }

    fun toggleSource(source: NotificationSource) {
        notificationSettingsInternal.update { it.withSourceToggled(source) }
    }

    companion object {
        private const val DEMO_SYNC_BATCH = 1

        private val DEMO_NOTIFICATION_SETTINGS = NotificationMirroringSettings(
            masterEnabled = true,
            sourceEnabled = mapOf(
                NotificationSource.MESSAGES to true,
                NotificationSource.CALENDAR to true,
                NotificationSource.TASKS to false,
            ),
            lastSyncLabel = "2 min ago",
            sentCount = 3,
        )
    }
}
