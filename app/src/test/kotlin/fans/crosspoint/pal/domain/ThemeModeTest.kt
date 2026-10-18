package fans.crosspoint.pal.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun `known storage names map to their modes`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageName("system"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromStorageName("light"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromStorageName("dark"))
    }

    @Test
    fun `missing or unknown storage names fail closed to SYSTEM`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageName(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageName(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageName("nonsense"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromStorageName("Light"))
    }

    @Test
    fun `storage names round trip`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromStorageName(mode.storageName))
        }
    }
}
