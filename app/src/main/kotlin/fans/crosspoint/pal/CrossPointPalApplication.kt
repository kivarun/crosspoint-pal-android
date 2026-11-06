package fans.crosspoint.pal

import android.app.Application
import android.content.Context
import android.os.SystemClock
import fans.crosspoint.pal.ble.AndroidBleEnvironment
import fans.crosspoint.pal.ble.AndroidCompanionScanner
import fans.crosspoint.pal.ble.BluetoothGattTransport
import fans.crosspoint.pal.ble.CompanionConnectionManager
import fans.crosspoint.pal.ble.CompanionIdentityStore
import fans.crosspoint.pal.ble.DataStoreCompanionIdentityStore
import fans.crosspoint.pal.ble.DiagnosticExporter
import fans.crosspoint.pal.ble.FileDiagnosticExporter
import fans.crosspoint.pal.ble.FileDiagnosticTraceStore
import fans.crosspoint.pal.ble.SessionDiagnosticTrace
import fans.crosspoint.pal.data.DataStoreThemeModeStore
import fans.crosspoint.pal.data.DemoNotificationState
import fans.crosspoint.pal.data.ThemeModeStore
import java.io.File
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

    /**
     * Bounded BLE session trace for the P1-B9 soak. One instance, one file;
     * only the connection manager, the app lifecycle, and the export path
     * touch it.
     */
    val diagnostics = SessionDiagnosticTrace(
        store = FileDiagnosticTraceStore(File(appContext.filesDir, TRACE_DIR)),
        monotonicMs = { SystemClock.elapsedRealtime() },
    )

    val connectionManager = CompanionConnectionManager(
        scope = appScope,
        transport = transport,
        environment = environment,
        identityStore = identityStore,
        scanner = scanner,
        diagnostics = diagnostics,
    )

    val diagnosticExporter: DiagnosticExporter = FileDiagnosticExporter(appContext, diagnostics)

    val demoNotificationState: DemoNotificationState = DemoNotificationState()

    private companion object {
        const val TRACE_DIR = "diagnostics/ble_session_trace.jsonl"
    }
}

class CrossPointPalApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
