package fans.crosspoint.pal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themePreferences by preferencesDataStore(name = "theme")

/**
 * DataStore-backed [ThemeModeStore].
 *
 * The preference value is stored as its canonical lowercase storage name;
 * unknown values fail closed to [ThemeMode.SYSTEM] on read.
 */
class DataStoreThemeModeStore(appContext: Context) : ThemeModeStore {

    private val dataStore: DataStore<Preferences> = appContext.themePreferences

    private val key = stringPreferencesKey("theme_mode")

    override val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        ThemeMode.fromStorageName(preferences[key])
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[key] = mode.storageName
        }
    }
}
