package dev.crosspoint.pal.domain

/**
 * Presentation-ready snapshot of the companion device state.
 *
 * Relative time labels ("Just now") are produced by the state owner; the UI
 * only renders them. Phase 0 values come from the demo state holder.
 */
data class CompanionDeviceSnapshot(
    val name: String,
    val connected: Boolean,
    val paired: Boolean,
    val notificationsMirroring: Boolean,
    val ready: Boolean,
    val batteryPercent: Int,
    val lastSeenLabel: String,
)

/** Snapshot of the most recent physical button event on the device. */
data class ButtonEventSnapshot(
    val buttonLabel: String,
    val timeLabel: String,
)
