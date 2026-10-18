package fans.crosspoint.pal.domain

/**
 * User-facing application theme preference.
 *
 * [SYSTEM] follows the OS dark-mode setting and is the product default.
 */
enum class ThemeMode(val storageName: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        /**
         * Maps a persisted storage name to a [ThemeMode].
         * Unknown or missing values fail closed to the product default ([SYSTEM]).
         */
        fun fromStorageName(name: String?): ThemeMode = when (name) {
            LIGHT.storageName -> LIGHT
            DARK.storageName -> DARK
            else -> SYSTEM
        }
    }
}
