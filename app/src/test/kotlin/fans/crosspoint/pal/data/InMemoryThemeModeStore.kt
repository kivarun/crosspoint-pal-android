package fans.crosspoint.pal.data

import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

/** Test and preview double for [ThemeModeStore]. */
class InMemoryThemeModeStore(
    initial: ThemeMode = ThemeMode.SYSTEM,
) : ThemeModeStore {
    override val themeMode = MutableStateFlow(initial)

    override suspend fun setThemeMode(mode: ThemeMode) {
        themeMode.value = mode
    }
}
