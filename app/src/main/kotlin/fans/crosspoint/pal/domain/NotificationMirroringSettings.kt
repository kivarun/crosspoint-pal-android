package fans.crosspoint.pal.domain

/** Phone notification sources the user may mirror to the Pal. */
enum class NotificationSource {
    MESSAGES,
    CALENDAR,
    TASKS,
}

/**
 * Notification mirroring settings. Phone-owned configuration; nothing here
 * touches Android Notification Access in Phase 0.
 */
data class NotificationMirroringSettings(
    val masterEnabled: Boolean,
    val sourceEnabled: Map<NotificationSource, Boolean>,
    val lastSyncLabel: String,
    val sentCount: Int,
) {
    /**
     * Returns the settings after applying a source toggle.
     * Individual source switches keep their value while the master switch is
     * off; they simply have no effect until mirroring is enabled again.
     */
    fun withSourceToggled(source: NotificationSource): NotificationMirroringSettings {
        val current = sourceEnabled.getValue(source)
        return copy(sourceEnabled = sourceEnabled + (source to !current))
    }

    fun withMaster(enabled: Boolean): NotificationMirroringSettings = copy(masterEnabled = enabled)
}
