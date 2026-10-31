package fans.crosspoint.pal.ui.send

import fans.crosspoint.pal.ble.CompanionError
import fans.crosspoint.pal.ble.FakeGattTransport
import fans.crosspoint.pal.ble.managerWithFakes
import fans.crosspoint.pal.ble.readySession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class SendViewModelTest {

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
    fun `send is disabled until the session is ready`() = runTest {
        val manager = managerWithFakes()
        testScheduler.runCurrent()
        val viewModel = SendViewModel(manager)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")

        assertFalse(viewModel.uiState.value.isSendEnabled)
    }

    @Test
    fun `acknowledged send emits sent and clears the draft`() = runTest {
        val transport = FakeGattTransport()
        val manager = managerWithFakes(transport = transport)
        testScheduler.runCurrent()
        readySession(transport, manager)
        val viewModel = SendViewModel(manager)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        val events = mutableListOf<SendUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }

        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")
        assertTrue(viewModel.uiState.value.isSendEnabled)
        viewModel.send()
        testScheduler.runCurrent()

        val link = transport.createdLinks.single()
        val payload = String(link.written.last(), Charsets.UTF_8)
        assertTrue(payload.contains("\"title\":\"Note\""))
        val id = Regex("\"contentId\":\"([^\"]+)\"").find(payload)!!.groupValues[1]
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification("""{"op":"content_ack","contentId":"$id"}""")
        testScheduler.runCurrent()

        assertEquals(listOf<SendUiEvent>(SendUiEvent.ContentSent), events)
        assertEquals("", viewModel.uiState.value.draft.title)
        assertEquals("", viewModel.uiState.value.draft.body)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun `send without a ready session reports no session`() = runTest {
        val manager = managerWithFakes()
        testScheduler.runCurrent()
        val viewModel = SendViewModel(manager)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        val events = mutableListOf<SendUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }

        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")
        viewModel.send()
        testScheduler.runCurrent()

        assertEquals(listOf<SendUiEvent>(SendUiEvent.NoSessionReady), events)
        // The draft is preserved so the user does not lose content.
        assertEquals("Note", viewModel.uiState.value.draft.title)
    }

    @Test
    fun `a rejected send reports the failure and keeps the draft`() = runTest {
        val transport = FakeGattTransport()
        val manager = managerWithFakes(transport = transport)
        testScheduler.runCurrent()
        readySession(transport, manager)
        val viewModel = SendViewModel(manager)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }
        val events = mutableListOf<SendUiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect(events::add) }

        viewModel.onTitleChange("Note")
        viewModel.onBodyChange("Content")
        viewModel.send()
        testScheduler.runCurrent()
        val link = transport.createdLinks.single()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification(
            """{"op":"error","code":"bad_message","message":"Malformed control message"}""",
        )
        testScheduler.runCurrent()

        assertEquals(
            listOf<SendUiEvent>(
                SendUiEvent.SendFailed(CompanionError.SendRejected("bad_message", "Malformed control message")),
            ),
            events,
        )
        assertEquals("Note", viewModel.uiState.value.draft.title)
        assertFalse(viewModel.uiState.value.isSending)
    }

    @Test
    fun `clear empties the draft`() = runTest {
        val manager = managerWithFakes()
        testScheduler.runCurrent()
        val viewModel = SendViewModel(manager)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect { } }

        viewModel.onTitleChange("Note")
        viewModel.clear()

        assertEquals("", viewModel.uiState.value.draft.title)
    }
}
