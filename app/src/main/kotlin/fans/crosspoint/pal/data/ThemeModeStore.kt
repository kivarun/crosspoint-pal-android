package fans.crosspoint.pal.data

import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Owner of the persisted application theme preference.
 *
 * Phase 0 keeps this concrete and tiny; the store is the single owner of the
 * theme setting, whether it is backed by DataStore or by memory in tests.
 */
interface ThemeModeStore {
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)
}
