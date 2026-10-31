package fans.crosspoint.pal.ble

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory store for host tests. */
class InMemoryCompanionIdentityStore : CompanionIdentityStore {
    private val internal = MutableStateFlow<CompanionIdentity?>(null)
    override val identity: Flow<CompanionIdentity?> = internal
    override suspend fun setIdentity(identity: CompanionIdentity) {
        internal.value = identity
    }
    override suspend fun clear() {
        internal.value = null
    }
}
