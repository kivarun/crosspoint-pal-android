package fans.crosspoint.pal.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Bounded storage, persistence across process death, counter derivation, and
 * export completeness of the BLE session trace, on a controlled clock.
 */
class SessionDiagnosticTraceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private class Clock {
        var now = 1_000L
        operator fun invoke(): Long = ++now
    }

    private fun trace(store: DiagnosticTraceStore, clock: Clock = Clock(), maxRecords: Int = 512) =
        SessionDiagnosticTrace(store, monotonicMs = clock::invoke, maxRecords = maxRecords)

    @Test
    fun `records keep deterministic seq order and monotonic timestamps`() {
        val clock = Clock()
        val t = trace(NoopDiagnosticTraceStore, clock)
        repeat(5) { index ->
            t.record(SessionDiagnosticTrace.EVENT_READY, attempt = "a1", detail = "step$index")
        }
        val events = t.events()
        assertEquals((1L..5L).toList(), events.map { it.seq })
        assertTrue(events.zipWithNext().all { (a, b) -> b.uptimeMs > a.uptimeMs })
    }

    @Test
    fun `storage is bounded and dropped events are counted`() {
        val t = trace(NoopDiagnosticTraceStore, maxRecords = 5)
        repeat(8) { t.record(SessionDiagnosticTrace.EVENT_READY) }

        assertEquals(5, t.events().size)
        assertEquals(3, t.summary().droppedEvents)
        // The oldest records were evicted: the newest five survive.
        assertEquals((4L..8L).toList(), t.events().map { it.seq })
    }

    @Test
    fun `counters are derived from observed events only`() {
        val t = trace(NoopDiagnosticTraceStore)
        val a = t.newAttemptId()
        t.record(SessionDiagnosticTrace.EVENT_CONNECT_REQUEST, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_GATT_CONNECTED, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_HELLO_SENT, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_HELLO_ACK, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_READY, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_CONTENT_SEND_STARTED, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_CONTENT_ACK_OK, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_DISCONNECT_REQUEST, attempt = a, phase = "Ready")

        val s = t.summary()
        assertEquals(1, s.connectionAttempts)
        assertEquals(1, s.readyTransitions)
        assertEquals(1, s.cleanDisconnects)
        assertEquals(0, s.unexpectedDisconnects)
        assertEquals(1, s.contentAckSuccesses)
        assertEquals(0, s.contentAckFailures)
        // Latencies derive from the recorded request/completion pairs.
        assertEquals(1, s.connectLatencySamples)
        assertEquals(1, s.handshakeLatencySamples)
        assertTrue(s.connectLatencyAvgMs > 0)
        assertEquals(s.connectLatencyAvgMs, s.connectLatencyMaxMs)
    }

    @Test
    fun `history survives a process restart`() {
        val file = tmp.newFile("trace.jsonl")
        val store = FileDiagnosticTraceStore(file)
        val first = trace(store)
        val a = first.newAttemptId()
        first.record(SessionDiagnosticTrace.EVENT_CONNECT_REQUEST, attempt = a)
        first.record(SessionDiagnosticTrace.EVENT_READY, attempt = a)
        first.record(SessionDiagnosticTrace.EVENT_GATT_STATUS_FAILED, attempt = a, phase = "Connecting", gattStatus = 133)
        val lastSeqBefore = first.events().last().seq

        // A new process constructs a fresh trace over the same file.
        val second = trace(store)
        val s = second.summary()
        assertEquals(1, s.connectionAttempts)
        assertEquals(1, s.readyTransitions)
        assertEquals("Connecting", s.lastFailureStage)
        assertEquals(133, s.lastGattStatus)
        // Attempt numbering and seq continue without reuse.
        assertEquals("a2", second.newAttemptId())
        second.record(SessionDiagnosticTrace.EVENT_APP_FOREGROUND)
        assertEquals(lastSeqBefore + 1, second.events().last().seq)
    }

    @Test
    fun `export is complete, ordered, and carries metadata and summary`() {
        val t = trace(NoopDiagnosticTraceStore)
        val a = t.newAttemptId()
        t.recordAppLifecycle(foreground = true)
        t.record(SessionDiagnosticTrace.EVENT_CONNECT_REQUEST, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_READY, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_LINK_CLOSED_UNEXPECTED, attempt = a, phase = "Ready")

        val lines = t.exportJsonl(exportedWallUtc = "2026-01-01T00:00:00Z").trimEnd().lines()
        assertTrue(lines.first().startsWith("""{"type":"session""""))
        assertTrue(lines.last().startsWith("""{"type":"summary""""))
        val eventLines = lines.filter { it.startsWith("""{"type":"event"""") }
        assertEquals(4, eventLines.size)
        val seqs = eventLines.map { line -> Regex("\"seq\":(\\d+)").find(line)!!.groupValues[1].toLong() }
        assertEquals(seqs.sorted(), seqs)
        // Attempt identifiers travel on the session events.
        assertTrue(eventLines.drop(1).all { it.contains("\"attempt\":\"$a\"") })
        assertTrue(lines.last().contains("\"unexpectedDisconnects\":1"))
    }

    @Test
    fun `sensitive fields never reach the trace`() {
        val t = trace(NoopDiagnosticTraceStore)
        val a = t.newAttemptId()
        t.record(SessionDiagnosticTrace.EVENT_CONNECT_REQUEST, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_READY, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_CONTENT_SEND_STARTED, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_CONTENT_ACK_OK, attempt = a)
        t.record(SessionDiagnosticTrace.EVENT_DISCONNECT_REQUEST, attempt = a, phase = "Ready")

        val exported = t.exportJsonl("2026-01-01T00:00:00Z")
        // Raw addresses, device names, and content are structurally excluded.
        for (forbidden in listOf("AA:BB", "CC:DD", "CrossPoint Pal", "Secret title", "hello_ack")) {
            assertTrue("leaked: $forbidden", !exported.contains(forbidden))
        }
        // Details come from the fixed vocabulary only.
        assertTrue(t.events().all { it.detail == null || it.detail in DIAGNOSTIC_VOCABULARY })
    }

    private companion object {
        val DIAGNOSTIC_VOCABULARY = setOf(
            "bluetooth_off",
            "permissions_missing",
            "scan_failed",
            "pairing_failed",
            "connection_failed",
            "insufficient_mtu",
            "incompatible_gatt",
            "incompatible_version",
            "not_paired",
            "setup_timeout",
            "send_rejected",
            "send_timeout",
            "protocol_violation",
            "link_lost",
            "bonded",
        )
    }
}
