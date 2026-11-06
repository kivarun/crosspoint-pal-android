package fans.crosspoint.pal.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Diagnostic instrumentation of the connection manager, driven through the
 * scripted GATT seam: every soak scenario records observable events with the
 * attempt identity intact.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionSessionDiagnosticsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class Fakes {
        var clockNow = 0L
        val transport = FakeGattTransport()
        val environment = FakeBleEnvironment()
        val identityStore = InMemoryCompanionIdentityStore()
        val scanner = FakeScanner()
        val trace = SessionDiagnosticTrace(
            NoopDiagnosticTraceStore,
            monotonicMs = { ++clockNow },
        )
    }

    private fun TestScope.build(fakes: Fakes = Fakes()) = fakes to managerWithFakes(
        transport = fakes.transport,
        environment = fakes.environment,
        identityStore = fakes.identityStore,
        scanner = fakes.scanner,
        diagnostics = fakes.trace,
    )

    private fun kinds(fakes: Fakes, attempt: String? = null): List<String> =
        fakes.trace.events()
            .filter { attempt == null || it.attempt == attempt }
            .map { it.kind }

    @Test
    fun `successful session records the full path to ready and a clean disconnect`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        val id = Regex("\"contentId\":\"([^\"]+)\"").find(String(link.written.last(), Charsets.UTF_8))!!
            .groupValues[1]
        link.deliverNotification("""{"op":"content_ack","contentId":"$id"}""")
        testScheduler.runCurrent()

        manager.disconnect()
        testScheduler.runCurrent()

        val expected = listOf(
            SessionDiagnosticTrace.EVENT_CONNECT_REQUEST,
            SessionDiagnosticTrace.EVENT_BOND_STARTED,
            SessionDiagnosticTrace.EVENT_BOND_OK,
            SessionDiagnosticTrace.EVENT_GATT_CONNECT,
            SessionDiagnosticTrace.EVENT_GATT_CONNECTED,
            SessionDiagnosticTrace.EVENT_MTU_NEGOTIATED,
            SessionDiagnosticTrace.EVENT_SERVICES_DISCOVERED,
            SessionDiagnosticTrace.EVENT_CCCD_DONE,
            SessionDiagnosticTrace.EVENT_HELLO_SENT,
            SessionDiagnosticTrace.EVENT_HELLO_ACK,
            SessionDiagnosticTrace.EVENT_READY,
            SessionDiagnosticTrace.EVENT_CONTENT_SEND_STARTED,
            SessionDiagnosticTrace.EVENT_CONTENT_ACK_OK,
            SessionDiagnosticTrace.EVENT_DISCONNECT_REQUEST,
        )
        assertEquals(expected, kinds(fakes, "a1"))

        val s = fakes.trace.summary()
        assertEquals(1, s.connectionAttempts)
        assertEquals(1, s.readyTransitions)
        assertEquals(1, s.cleanDisconnects)
        assertEquals(0, s.unexpectedDisconnects)
        assertEquals(1, s.contentAckSuccesses)
        assertEquals(0, s.contentAckFailures)
        assertEquals(1, s.connectLatencySamples)
        assertEquals(1, s.handshakeLatencySamples)
        assertEquals(null, s.lastFailureStage)
    }

    @Test
    fun `repeated reconnect cycles keep one continuous trace`() = runTest {
        val (fakes, manager) = build()
        repeat(3) { cycle ->
            if (cycle == 0) manager.connect("AA:BB", "CrossPoint Pal") else manager.reconnect()
            testScheduler.runCurrent()
            val link = fakes.transport.createdLinks[cycle]
            completeSetup(link)
            assertEquals(LinkPhase.Ready, manager.linkPhase.value)
            if (cycle < 2) {
                link.simulateLinkLoss()
                testScheduler.runCurrent()
            }
        }

        val s = fakes.trace.summary()
        assertEquals(3, s.connectionAttempts)
        assertEquals(3, s.readyTransitions)
        assertEquals(2, s.unexpectedDisconnects)
        // Trace continuity: one strictly increasing seq across all attempts.
        val seqs = fakes.trace.events().map { it.seq }
        assertEquals(seqs.sorted(), seqs)
        assertEquals(listOf("a1", "a2", "a3"), fakes.trace.events().mapNotNull { it.attempt }.distinct())
    }

    @Test
    fun `a gatt status failure during connection is recorded with its status`() = runTest {
        val (fakes, manager) = build()
        fakes.transport.nextConnectError = GattOperationException(133)
        manager.connect("AA:BB", null)
        testScheduler.runCurrent()

        val failure = fakes.trace.events().single { it.kind == SessionDiagnosticTrace.EVENT_GATT_STATUS_FAILED }
        assertEquals(133, failure.gattStatus)
        assertEquals("Connecting", failure.phase)
        assertEquals("a1", failure.attempt)

        val s = fakes.trace.summary()
        assertEquals(1, s.connectionAttempts)
        assertEquals(0, s.readyTransitions)
        assertEquals(133, s.lastGattStatus)
        assertEquals("Connecting", s.lastFailureStage)
    }

    @Test
    fun `a failed hello handshake records the failure stage`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        val link = fakes.transport.createdLinks.single()
        link.replyMtu(CompanionProtocol.REQUESTED_MTU)
        testScheduler.runCurrent()
        link.replyDiscovered()
        testScheduler.runCurrent()
        link.replySubscribed()
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification("""{"op":"error","code":"version","message":"Unsupported protocol version"}""")
        testScheduler.runCurrent()

        assertEquals(
            listOf(
                SessionDiagnosticTrace.EVENT_CONNECT_REQUEST,
                SessionDiagnosticTrace.EVENT_BOND_STARTED,
                SessionDiagnosticTrace.EVENT_BOND_OK,
                SessionDiagnosticTrace.EVENT_GATT_CONNECT,
                SessionDiagnosticTrace.EVENT_GATT_CONNECTED,
                SessionDiagnosticTrace.EVENT_MTU_NEGOTIATED,
                SessionDiagnosticTrace.EVENT_SERVICES_DISCOVERED,
                SessionDiagnosticTrace.EVENT_CCCD_DONE,
                SessionDiagnosticTrace.EVENT_HELLO_SENT,
                SessionDiagnosticTrace.EVENT_FAILURE,
            ),
            kinds(fakes, "a1"),
        )
        val s = fakes.trace.summary()
        assertEquals("Handshaking", s.lastFailureStage)
        assertEquals("incompatible_version", s.lastFailureKind)
        assertEquals(0, s.readyTransitions)
    }

    @Test
    fun `an unexpected disconnection while sending is recorded on both counters`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.simulateLinkLoss()
        testScheduler.runCurrent()

        assertEquals(SendResult.Failed(CompanionError.LinkLost), result)
        val s = fakes.trace.summary()
        assertEquals(1, s.unexpectedDisconnects)
        assertEquals(1, s.contentAckFailures)
        assertEquals(0, s.contentAckSuccesses)
        assertEquals("Ready", s.lastFailureStage)
        assertEquals("link_lost", s.lastFailureKind)
        // No clean disconnect was ever requested.
        assertEquals(0, s.cleanDisconnects)
    }

    @Test
    fun `a late callback from a superseded connection is not attributed to the active attempt`() = runTest {
        val (fakes, manager) = build()
        val first = readySession(fakes.transport, manager)
        first.enterCloseOnBinderThread()

        manager.disconnect()
        testScheduler.runCurrent()
        manager.reconnect()
        testScheduler.runCurrent()
        val second = fakes.transport.createdLinks[1]
        completeSetup(second)
        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
        val eventsUpTo = fakes.trace.events().size

        // The stale close callback arrives while the new session is live.
        first.fireDeferredCloseCallback()
        testScheduler.runCurrent()

        assertEquals(eventsUpTo, fakes.trace.events().size)
        assertTrue(fakes.trace.events().none {
            it.attempt == "a1" && it.kind == SessionDiagnosticTrace.EVENT_LINK_CLOSED_UNEXPECTED
        })
        val s = fakes.trace.summary()
        assertEquals(0, s.unexpectedDisconnects) // the stale close recorded nothing
        assertEquals(2, s.connectionAttempts)
        assertEquals(2, s.readyTransitions)
        assertEquals(1, s.cleanDisconnects)
    }

    @Test
    fun `history survives a process restart with counters intact`() {
        val file = File(tmp.root, "ble_session_trace.jsonl")
        val store = FileDiagnosticTraceStore(file)
        val first = SessionDiagnosticTrace(store, monotonicMs = { 0L })
        val a = first.newAttemptId()
        first.record(SessionDiagnosticTrace.EVENT_CONNECT_REQUEST, attempt = a)
        first.record(SessionDiagnosticTrace.EVENT_READY, attempt = a)
        first.record(SessionDiagnosticTrace.EVENT_CONTENT_ACK_OK, attempt = a)
        first.record(SessionDiagnosticTrace.EVENT_DISCONNECT_REQUEST, attempt = a, phase = "Ready")

        val restarted = SessionDiagnosticTrace(store, monotonicMs = { 0L })
        val s = restarted.summary()
        assertEquals(1, s.connectionAttempts)
        assertEquals(1, s.readyTransitions)
        assertEquals(1, s.cleanDisconnects)
        assertEquals(1, s.contentAckSuccesses)
        // The trace remains appendable after the restart.
        restarted.record(SessionDiagnosticTrace.EVENT_APP_BACKGROUND)
        assertEquals(5, restarted.events().size)
    }

    @Test
    fun `the manager writes its trace into the bounded store`() = runTest {
        val (fakes, manager) = build()
        readySession(fakes.transport, manager)
        manager.disconnect()
        testScheduler.runCurrent()

        assertTrue(fakes.trace.events().isNotEmpty())
        assertTrue(fakes.trace.summary().connectionAttempts == 1)
        // Events stay within the bound regardless of session count.
        assertFalse(fakes.trace.events().size > SessionDiagnosticTrace.MAX_RECORDS)
    }
}
