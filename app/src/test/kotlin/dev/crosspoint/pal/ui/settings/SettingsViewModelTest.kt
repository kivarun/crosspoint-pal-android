package dev.crosspoint.pal.ui.settings

import dev.crosspoint.pal.data.DemoCompanionState
import dev.crosspoint.pal.data.InMemoryThemeModeStore
import dev.crosspoint.pal.domain.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var themeStore: InMemoryThemeModeStore
    private lateinit var demo: DemoCompanionState

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        themeStore = InMemoryThemeModeStore()
        demo = DemoCompanionState()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `theme selection is persisted in the store`() = runTest {
        val viewModel = SettingsViewModel(themeStore, demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.onThemeModeSelected(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, themeStore.themeMode.first())

        viewModel.onThemeModeSelected(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, themeStore.themeMode.first())
        assertEquals(ThemeMode.LIGHT, viewModel.uiState.value.themeMode)
    }

    @Test
    fun `forget request shows the dialog and dismiss hides it`() = runTest {
        val viewModel = SettingsViewModel(themeStore, demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.onForgetRequested()
        assertTrue(viewModel.uiState.value.showForgetDialog)

        viewModel.onForgetDismissed()
        assertFalse(viewModel.uiState.value.showForgetDialog)
        // A dismissed dialog forgets nothing.
        assertEquals("X4 Classic", viewModel.uiState.value.deviceName)
    }

    @Test
    fun `forget confirmation clears the device state`() = runTest {
        val viewModel = SettingsViewModel(themeStore, demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.onForgetRequested()
        viewModel.onForgetConfirmed()

        assertNull(demo.device.value)
        assertFalse(viewModel.uiState.value.showForgetDialog)
        assertFalse(viewModel.uiState.value.hasDevice)
    }
}
