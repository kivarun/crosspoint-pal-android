package fans.crosspoint.pal.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationMirroringSettingsTest {

    private val settings = NotificationMirroringSettings(
        masterEnabled = true,
        sourceEnabled = mapOf(
            NotificationSource.MESSAGES to true,
            NotificationSource.CALENDAR to true,
            NotificationSource.TASKS to false,
        ),
        lastSyncLabel = "2 min ago",
        sentCount = 3,
    )

    @Test
    fun `source toggle flips only that source`() {
        val toggled = settings.withSourceToggled(NotificationSource.TASKS)
        assertTrue(toggled.sourceEnabled.getValue(NotificationSource.TASKS))
        assertTrue(toggled.sourceEnabled.getValue(NotificationSource.MESSAGES))
        assertTrue(toggled.sourceEnabled.getValue(NotificationSource.CALENDAR))
        assertTrue(settings.sourceEnabled.getValue(NotificationSource.TASKS).not())
    }

    @Test
    fun `source toggle is independent while master is off`() {
        val masterOff = settings.withMaster(false)
        val toggled = masterOff.withSourceToggled(NotificationSource.MESSAGES)
        assertFalse(toggled.masterEnabled)
        assertFalse(toggled.sourceEnabled.getValue(NotificationSource.MESSAGES))
        // Individual switches keep their own values; they simply have no
        // effect until mirroring is enabled again.
        assertTrue(masterOff.sourceEnabled.getValue(NotificationSource.MESSAGES))
    }

    @Test
    fun `master toggle only changes the master switch`() {
        val disabled = settings.withMaster(false)
        assertFalse(disabled.masterEnabled)
        assertEquals(settings.sourceEnabled, disabled.sourceEnabled)
        assertEquals(settings.sentCount, disabled.sentCount)

        val reEnabled = disabled.withMaster(true)
        assertTrue(reEnabled.masterEnabled)
    }
}
