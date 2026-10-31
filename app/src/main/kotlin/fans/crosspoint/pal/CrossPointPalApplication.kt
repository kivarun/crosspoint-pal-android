package fans.crosspoint.pal

import android.app.Application
import android.content.Context
import fans.crosspoint.pal.ble.AndroidBleEnvironment
import fans.crosspoint.pal.ble.AndroidCompanionScanner
import fans.crosspoint.pal.ble.BluetoothGattTransport
import fans.crosspoint.pal.ble.CompanionConnectionManager
import fans.crosspoint.pal.ble.CompanionIdentityStore
import fans.crosspoint.pal.ble.DataStoreCompanionIdentityStore
import fans.crosspoint.pal.data.DataStoreThemeModeStore
import fans.crosspoint.pal.data.DemoNotificationState
import fans.crosspoint.pal.data.ThemeModeStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Application-scoped dependencies.
 *
 * A dependency injection framework is intentionally not used; this container
 * is the single place where screen-facing state owners are created. The
 * connection manager is the one owner of the companion GATT lifecycle.
 */
class AppContainer(appContext: Context) {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val themeModeStore: ThemeModeStore = DataStoreThemeModeStore(appContext)

    private val identityStore: CompanionIdentityStore = DataStoreCompanionIdentityStore(appContext)
    private val transport = BluetoothGattTransport(appContext)
    private val environment = AndroidBleEnvironment(appContext)
    private val scanner = AndroidCompanionScanner(appContext)

    val connectionManager = CompanionConnectionManager(
        scope = appScope,
        transport = transport,
        environment = environment,
        identityStore = identityStore,
        scanner = scanner,
    )

    val demoNotificationState: DemoNotificationState = DemoNotificationState()
}

class CrossPointPalApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
