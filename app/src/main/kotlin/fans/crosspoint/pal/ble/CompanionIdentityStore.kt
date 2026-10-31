package fans.crosspoint.pal.ble

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The durable companion identity: the bonded reader's address and name. */
data class CompanionIdentity(
    val address: String,
    val name: String?,
)

/**
 * Persistence owner for the paired companion identity.
 *
 * The system bond itself lives in the platform's bond store; this record only
 * lets the app know which bonded device is the Pal, because the identity of a
 * companion is the bonded device address, never an over-the-air address.
 */
interface CompanionIdentityStore {
    val identity: Flow<CompanionIdentity?>
    suspend fun setIdentity(identity: CompanionIdentity)
    suspend fun clear()
}

private val Context.identityDataStore: DataStore<Preferences> by preferencesDataStore(name = "companion_identity")

class DataStoreCompanionIdentityStore(private val context: Context) : CompanionIdentityStore {

    override val identity: Flow<CompanionIdentity?> = context.identityDataStore.data.map { preferences ->
        val address = preferences[KEY_ADDRESS] ?: return@map null
        CompanionIdentity(address = address, name = preferences[KEY_NAME])
    }

    override suspend fun setIdentity(identity: CompanionIdentity) {
        context.identityDataStore.edit { preferences ->
            preferences[KEY_ADDRESS] = identity.address
            identity.name?.let { preferences[KEY_NAME] = it }
        }
    }

    override suspend fun clear() {
        context.identityDataStore.edit { preferences ->
            preferences.remove(KEY_ADDRESS)
            preferences.remove(KEY_NAME)
        }
    }

    private companion object {
        val KEY_ADDRESS = stringPreferencesKey("companion.device.address")
        val KEY_NAME = stringPreferencesKey("companion.device.name")
    }
}
