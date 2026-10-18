package fans.crosspoint.pal

import android.app.Application
import android.content.Context
import fans.crosspoint.pal.data.DemoCompanionState
import fans.crosspoint.pal.data.DataStoreThemeModeStore
import fans.crosspoint.pal.data.ThemeModeStore

/**
 * Application-scoped dependencies for Phase 0.
 *
 * A dependency injection framework is intentionally not used yet; this
 * container is the single place where screen-facing state owners are created.
 */
class AppContainer(appContext: Context) {
    val themeModeStore: ThemeModeStore = DataStoreThemeModeStore(appContext)
    val demoCompanionState: DemoCompanionState = DemoCompanionState()
}

class CrossPointPalApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
