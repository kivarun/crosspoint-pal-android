package dev.crosspoint.pal.data

import dev.crosspoint.pal.domain.ButtonEventSnapshot
import dev.crosspoint.pal.domain.CompanionDeviceSnapshot
import dev.crosspoint.pal.domain.NotificationMirroringSettings
import dev.crosspoint.pal.domain.NotificationSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory demo state for Phase 0. Every value here is presentation data;
 * no Bluetooth, notification listener, or persistence is involved.
 *
 * A future transport implementation replaces this state holder behind the
 * same screen-facing ViewModels without UI changes.
 */
class DemoCompanionState {

    private val deviceInternal = MutableStateFlow<CompanionDeviceSnapshot?>(DEMO_DEVICE)
    val device: StateFlow<CompanionDeviceSnapshot?> = deviceInternal.asStateFlow()

    private val lastButtonEventInternal = MutableStateFlow<ButtonEventSnapshot?>(
        ButtonEventSnapshot(buttonLabel = DEMO_BUTTON_LABEL, timeLabel = "Just now"),
    )
    val lastButtonEvent: StateFlow<ButtonEventSnapshot?> = lastButtonEventInternal.asStateFlow()

    private val notificationSettingsInternal = MutableStateFlow(DEMO_NOTIFICATION_SETTINGS)
    val notificationSettings: StateFlow<NotificationMirroringSettings> =
        notificationSettingsInternal.asStateFlow()

    /**
     * Demo content send. Phase 0 always succeeds immediately; the result value
     * exists so the UI already renders the real success path shape.
     */
    fun sendContent(title: String, body: String): SendOutcome {
        if (deviceInternal.value?.connected != true) {
            return SendOutcome.NoDevice
        }
        return SendOutcome.Sent
    }

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

    /** Demo forget-device action: clears the companion device entirely. */
    fun forgetDevice() {
        deviceInternal.value = null
        lastButtonEventInternal.value = null
    }

    companion object {
        private const val DEMO_BUTTON_LABEL = "Right button"
        private const val DEMO_SYNC_BATCH = 1

        private val DEMO_DEVICE = CompanionDeviceSnapshot(
            name = "X4 Classic",
            connected = true,
            paired = true,
            notificationsMirroring = true,
            ready = true,
            batteryPercent = 73,
            lastSeenLabel = "now",
        )

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

/** Result of a demo content send. */
sealed interface SendOutcome {
    data object Sent : SendOutcome
    data object NoDevice : SendOutcome
}
