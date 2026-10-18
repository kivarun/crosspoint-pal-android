package fans.crosspoint.pal.ui.send

import fans.crosspoint.pal.data.DemoCompanionState
import fans.crosspoint.pal.data.SendOutcome
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SendViewModelTest {

    private val mainDispatcher = UnconfinedTestDispatcher()
    private lateinit var demo: DemoCompanionState

    @Before
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        demo = DemoCompanionState()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `title and body changes update the state`() = runTest {
        val viewModel = SendViewModel(demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")

        val state = viewModel.uiState.value
        assertEquals("Note", state.draft.title)
        assertEquals("Content", state.draft.body)
        assertTrue(state.isSendEnabled)
    }

    @Test
    fun `send without content is a no-op`() = runTest {
        val viewModel = SendViewModel(demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        val events = mutableListOf<SendUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }

        viewModel.send()

        assertTrue(events.isEmpty())
        assertEquals("", viewModel.uiState.value.draft.title)
    }

    @Test
    fun `successful send emits event and clears the draft`() = runTest {
        val viewModel = SendViewModel(demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")

        val events = mutableListOf<SendUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }

        viewModel.send()

        assertEquals(listOf(SendUiEvent.ContentSent), events)
        assertEquals("", viewModel.uiState.value.draft.title)
        assertEquals("", viewModel.uiState.value.draft.body)
    }

    @Test
    fun `send without a connected device emits no-device event`() = runTest {
        demo.forgetDevice()
        val viewModel = SendViewModel(demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")

        val events = mutableListOf<SendUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }

        viewModel.send()

        assertEquals(listOf(SendUiEvent.NoDeviceConnected), events)
        // The draft is preserved so the user does not lose content.
        assertEquals("Note", viewModel.uiState.value.draft.title)
    }

    @Test
    fun `send is disabled when the device is not connected`() = runTest {
        demo.forgetDevice()
        val viewModel = SendViewModel(demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")

        assertFalse(viewModel.uiState.value.isSendEnabled)
    }

    @Test
    fun `clear empties the draft`() = runTest {
        val viewModel = SendViewModel(demo)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")

        viewModel.clear()

        assertFalse(viewModel.uiState.value.isSendEnabled)
        assertEquals("", viewModel.uiState.value.draft.title)
    }

    @Test
    fun `demo state reports sent for a connected device`() {
        assertEquals(SendOutcome.Sent, demo.sendContent(title = "t", body = "b"))
    }
}
