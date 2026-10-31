package fans.crosspoint.pal.ui.settings

import fans.crosspoint.pal.ble.CompanionIdentity
import fans.crosspoint.pal.ble.FakeGattTransport
import fans.crosspoint.pal.ble.InMemoryCompanionIdentityStore
import fans.crosspoint.pal.ble.managerWithFakes
import fans.crosspoint.pal.data.InMemoryThemeModeStore
import fans.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme selection is persisted in the store`() = runTest {
        val themeStore = InMemoryThemeModeStore()
        val viewModel = SettingsViewModel(themeStore, managerWithFakes())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.onThemeModeSelected(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, themeStore.themeMode.first())

        viewModel.onThemeModeSelected(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, themeStore.themeMode.first())
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `forget request shows the dialog and dismiss hides it`() = runTest {
        val identityStore = InMemoryCompanionIdentityStore()
        identityStore.setIdentity(CompanionIdentity("AA:BB", "CrossPoint Pal"))
        val viewModel = SettingsViewModel(
            InMemoryThemeModeStore(),
            managerWithFakes(identityStore = identityStore),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        testScheduler.runCurrent()

        viewModel.onForgetRequested()
        assertTrue(viewModel.uiState.value.showForgetDialog)

        viewModel.onForgetDismissed()
        assertFalse(viewModel.uiState.value.showForgetDialog)
        // A dismissed dialog forgets nothing.
        assertEquals("CrossPoint Pal", viewModel.uiState.value.deviceName)
    }

    @Test
    fun `forget confirmation clears identity and reports completion`() = runTest {
        val transport = FakeGattTransport()
        val identityStore = InMemoryCompanionIdentityStore()
        identityStore.setIdentity(CompanionIdentity("AA:BB", "CrossPoint Pal"))
        val viewModel = SettingsViewModel(
            InMemoryThemeModeStore(),
            managerWithFakes(transport = transport, identityStore = identityStore),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        val events = mutableListOf<SettingsUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }
        testScheduler.runCurrent()
        assertTrue(viewModel.uiState.value.hasDevice)

        viewModel.onForgetRequested()
        viewModel.onForgetConfirmed()
        testScheduler.runCurrent()

        assertEquals("AA:BB", transport.bondsRemoved.single())
        assertFalse(viewModel.uiState.value.showForgetDialog)
        assertFalse(viewModel.uiState.value.hasDevice)
        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.ForgetCompleted), events)
    }

    @Test
    fun `forget reports when the system bond needs manual removal`() = runTest {
        val transport = FakeGattTransport().apply { removeBondResult = false }
        val identityStore = InMemoryCompanionIdentityStore()
        identityStore.setIdentity(CompanionIdentity("AA:BB", "CrossPoint Pal"))
        val viewModel = SettingsViewModel(
            InMemoryThemeModeStore(),
            managerWithFakes(transport = transport, identityStore = identityStore),
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        val events = mutableListOf<SettingsUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }
        testScheduler.runCurrent()

        viewModel.onForgetRequested()
        viewModel.onForgetConfirmed()
        testScheduler.runCurrent()

        assertEquals(listOf<SettingsUiEvent>(SettingsUiEvent.ForgetNeedsSystemSettings), events)
        // The app-side identity is cleared either way.
        assertFalse(viewModel.uiState.value.hasDevice)
    }
}
