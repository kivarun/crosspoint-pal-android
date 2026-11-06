package fans.crosspoint.pal.ble

/**
 * Bounded, exportable diagnostic trace for the companion BLE session
 * (P1-B9 overnight soak observability).
 *
 * Ownership: exactly one trace instance exists, owned by the app container
 * and driven only by [CompanionConnectionManager] (the single GATT owner),
 * the app lifecycle, and the Settings export path. No second component may
 * record GATT-level events.
 *
 * Privacy: records carry structural facts only (phase names, event kinds,
 * Android GATT status codes, monotonic timestamps). Content titles and
 * bodies, passkeys, bonding material, and raw BLE addresses never enter the
 * trace; `detail` strings come from a fixed vocabulary owned by this module.
 *
 * Storage: records are appended to a bounded in-memory window and persisted
 * as JSONL through a [DiagnosticTraceStore] on every mutation, so the trace
 * survives process death. When the window is full the oldest record is
 * evicted and the eviction is counted in `droppedEvents`.
 */
class SessionDiagnosticTrace(
    private val store: DiagnosticTraceStore,
    private val monotonicMs: () -> Long,
    private val maxRecords: Int = MAX_RECORDS,
) {

    /** One observed diagnostic event. */
    data class DiagnosticEvent(
        val seq: Long,
        val attempt: String?,
        val kind: String,
        val phase: String?,
        val gattStatus: Int?,
        val detail: String?,
        val uptimeMs: Long,
    )

    /** Soak counters, derived only from recorded events. */
    data class Summary(
        val connectionAttempts: Int,
        val readyTransitions: Int,
        val cleanDisconnects: Int,
        val unexpectedDisconnects: Int,
        val contentAckSuccesses: Int,
        val contentAckFailures: Int,
        val connectLatencySamples: Int,
        val connectLatencyAvgMs: Long,
        val connectLatencyMaxMs: Long,
        val handshakeLatencySamples: Int,
        val handshakeLatencyAvgMs: Long,
        val handshakeLatencyMaxMs: Long,
        val lastFailureStage: String?,
        val lastFailureKind: String?,
        val lastGattStatus: Int?,
        val droppedEvents: Int,
    )

    private val lock = Any()
    private val records = ArrayDeque<DiagnosticEvent>()
    private var startedWallUtc: String = ""
    private var nextSeq: Long = 1
    private var dropped = 0
    private var nextAttemptNumber = 1
    private var connectionAttempts = 0
    private var readyTransitions = 0
    private var cleanDisconnects = 0
    private var unexpectedDisconnects = 0
    private var contentAckSuccesses = 0
    private var contentAckFailures = 0
    private var connectLatencySum = 0L
    private var connectLatencyCount = 0
    private var connectLatencyMax = 0L
    private var handshakeLatencySum = 0L
    private var handshakeLatencyCount = 0
    private var handshakeLatencyMax = 0L
    private var lastFailureStage: String? = null
    private var lastFailureKind: String? = null
    private var lastGattStatus: Int? = null

    /** Monotonic start time of the attempt that is currently running. */
    private val attemptStartUptime = HashMap<String, Long>()
    private var helloSentUptime: Long? = null

    init {
        load()
    }

    // --- Recording API ---

    /**
     * Records one event. `detail` must come from the fixed vocabulary of
     * [diagnosticLabel] or another structural string; never user content.
     */
    fun record(
        kind: String,
        attempt: String? = null,
        phase: String? = null,
        gattStatus: Int? = null,
        detail: String? = null,
    ) {
        synchronized(lock) {
            val event = DiagnosticEvent(
                seq = nextSeq++,
                attempt = attempt,
                kind = kind,
                phase = phase,
                gattStatus = gattStatus,
                detail = detail,
                uptimeMs = monotonicMs(),
            )
            applyToCounters(event)
            append(event)
            persist()
        }
    }

    /** Records an application foreground/background transition. */
    fun recordAppLifecycle(foreground: Boolean) {
        record(if (foreground) EVENT_APP_FOREGROUND else EVENT_APP_BACKGROUND)
    }

    /**
     * Allocates the identity of the next connection attempt. Attempt ids are
     * derived from the persisted attempt count, so they stay unique across
     * process restarts.
     */
    fun newAttemptId(): String = synchronized(lock) {
        "a${nextAttemptNumber++}"
    }

    // --- Introspection ---

    fun events(): List<DiagnosticEvent> = synchronized(lock) { records.toList() }

    fun summary(): Summary = synchronized(lock) { summarize() }

    // --- Export ---

    /**
     * Renders the full trace as JSONL: one session metadata line, the event
     * lines in deterministic seq order, and one final summary line.
     */
    fun exportJsonl(exportedWallUtc: String): String {
        synchronized(lock) {
            val sb = StringBuilder()
            sb.appendLine(
                """{"type":"session","app":"crosspoint-pal","protocol":"companion-v1",""" +
                    """"startedWallUtc":${jsonString(startedWallUtc)},""" +
                    """"exportedWallUtc":${jsonString(exportedWallUtc)},""" +
                    """"eventCount":${records.size},"droppedEvents":$dropped}""",
            )
            for (event in records) {
                sb.appendLine(encodeEvent(event))
            }
            val s = summarize()
            sb.append(
                """{"type":"summary","connectionAttempts":${s.connectionAttempts},""" +
                    """"readyTransitions":${s.readyTransitions},""" +
                    """"cleanDisconnects":${s.cleanDisconnects},""" +
                    """"unexpectedDisconnects":${s.unexpectedDisconnects},""" +
                    """"contentAckSuccesses":${s.contentAckSuccesses},""" +
                    """"contentAckFailures":${s.contentAckFailures},""" +
                    """"connectLatencySamples":${s.connectLatencySamples},""" +
                    """"connectLatencyAvgMs":${s.connectLatencyAvgMs},""" +
                    """"connectLatencyMaxMs":${s.connectLatencyMaxMs},""" +
                    """"handshakeLatencySamples":${s.handshakeLatencySamples},""" +
                    """"handshakeLatencyAvgMs":${s.handshakeLatencyAvgMs},""" +
                    """"handshakeLatencyMaxMs":${s.handshakeLatencyMaxMs},""" +
                    """"lastFailureStage":${jsonNullable(s.lastFailureStage)},""" +
                    """"lastFailureKind":${jsonNullable(s.lastFailureKind)},""" +
                    """"lastGattStatus":${s.lastGattStatus ?: "null"},""" +
                    """"droppedEvents":${s.droppedEvents},""" +
                    """"nextSeq":$nextSeq}""",
            )
            return sb.toString()
        }
    }

    // --- Counter derivation ---

    private fun applyToCounters(event: DiagnosticEvent) {
        when (event.kind) {
            EVENT_CONNECT_REQUEST, EVENT_RECONNECT_REQUEST, EVENT_AWAIT_WAKE_CONNECT -> {
                connectionAttempts++
                attemptStartUptime[event.attempt!!] = event.uptimeMs
            }
            EVENT_DISCONNECT_REQUEST ->
                // A clean disconnect is a user request while the session was Ready.
                if (event.phase == PHASE_READY) cleanDisconnects++
            EVENT_READY -> readyTransitions++
            EVENT_GATT_CONNECTED -> event.attempt?.let { attempt ->
                attemptStartUptime.remove(attempt)?.let { start ->
                    val latency = event.uptimeMs - start
                    connectLatencySum += latency
                    connectLatencyCount++
                    if (latency > connectLatencyMax) connectLatencyMax = latency
                }
            }
            EVENT_HELLO_SENT -> helloSentUptime = event.uptimeMs
            EVENT_HELLO_ACK -> helloSentUptime?.let { start ->
                val latency = event.uptimeMs - start
                handshakeLatencySum += latency
                handshakeLatencyCount++
                if (latency > handshakeLatencyMax) handshakeLatencyMax = latency
                helloSentUptime = null
            }
            EVENT_CONTENT_ACK_OK -> contentAckSuccesses++
            EVENT_CONTENT_SEND_TIMEOUT, EVENT_CONTENT_SEND_FAILED, EVENT_CONTENT_ACK_MISMATCH -> {
                contentAckFailures++
                if (event.phase != null) lastFailureStage = event.phase
                lastFailureKind = event.detail ?: event.kind
            }
            EVENT_LINK_CLOSED_UNEXPECTED -> {
                unexpectedDisconnects++
                if (event.phase != null) lastFailureStage = event.phase
                lastFailureKind = event.detail ?: event.kind
            }
            EVENT_FAILURE, EVENT_GATT_STATUS_FAILED -> {
                if (event.phase != null) lastFailureStage = event.phase
                lastFailureKind = event.detail ?: event.kind
                lastGattStatus = event.gattStatus
            }
        }
    }

    private fun summarize() = Summary(
        connectionAttempts = connectionAttempts,
        readyTransitions = readyTransitions,
        cleanDisconnects = cleanDisconnects,
        unexpectedDisconnects = unexpectedDisconnects,
        contentAckSuccesses = contentAckSuccesses,
        contentAckFailures = contentAckFailures,
        connectLatencySamples = connectLatencyCount,
        connectLatencyAvgMs = if (connectLatencyCount == 0) 0 else connectLatencySum / connectLatencyCount,
        connectLatencyMaxMs = connectLatencyMax,
        handshakeLatencySamples = handshakeLatencyCount,
        handshakeLatencyAvgMs = if (handshakeLatencyCount == 0) 0 else handshakeLatencySum / handshakeLatencyCount,
        handshakeLatencyMaxMs = handshakeLatencyMax,
        lastFailureStage = lastFailureStage,
        lastFailureKind = lastFailureKind,
        lastGattStatus = lastGattStatus,
        droppedEvents = dropped,
    )

    // --- Bounded storage ---

    private fun append(event: DiagnosticEvent) {
        if (records.size >= maxRecords) {
            records.removeFirst()
            dropped++
        }
        records.addLast(event)
    }

    private fun persist() {
        store.writeAll(persistedLines())
    }

    /** Event lines in seq order, then the summary line that recovers counters. */
    private fun persistedLines(): List<String> {
        val lines = ArrayList<String>(records.size + 1)
        for (event in records) lines.add(encodeEvent(event))
        val s = summarize()
        lines.add(
            """{"type":"summary","startedWallUtc":${jsonString(startedWallUtc)},""" +
                """"dropped":$dropped,"nextSeq":$nextSeq,"nextAttempt":$nextAttemptNumber,""" +
                """"connectionAttempts":${s.connectionAttempts},""" +
                """"readyTransitions":${s.readyTransitions},""" +
                """"cleanDisconnects":${s.cleanDisconnects},""" +
                """"unexpectedDisconnects":${s.unexpectedDisconnects},""" +
                """"contentAckSuccesses":${s.contentAckSuccesses},""" +
                """"contentAckFailures":${s.contentAckFailures},""" +
                """"connectLatencySum":$connectLatencySum,""" +
                """"connectLatencyCount":$connectLatencyCount,""" +
                """"connectLatencyMax":$connectLatencyMax,""" +
                """"handshakeLatencySum":$handshakeLatencySum,""" +
                """"handshakeLatencyCount":$handshakeLatencyCount,""" +
                """"handshakeLatencyMax":$handshakeLatencyMax,""" +
                """"lastFailureStage":${jsonNullable(lastFailureStage)},""" +
                """"lastFailureKind":${jsonNullable(lastFailureKind)},""" +
                """"lastGattStatus":${lastGattStatus ?: "null"}}""",
        )
        return lines
    }

    /** Restores history and counters from the persisted trace, if any. */
    private fun load() {
        val lines = store.readLines()
        if (lines.isEmpty()) {
            startedWallUtc = utcNow()
            persist()
            return
        }
        for (line in lines) {
            if (!line.startsWith(EVENT_LINE_PREFIX)) continue
            decodeEvent(line)?.let { records.addLast(it) }
        }
        val summaryLine = lines.lastOrNull { it.contains("\"type\":\"summary\"") } ?: return
        restoreCounters(summaryLine)
    }

    private fun restoreCounters(line: String) {
        startedWallUtc = jsonStringValue(line, "startedWallUtc") ?: startedWallUtc
        dropped = intField(line, "dropped")
        nextSeq = longField(line, "nextSeq")
        nextAttemptNumber = intField(line, "nextAttempt")
        connectionAttempts = intField(line, "connectionAttempts")
        readyTransitions = intField(line, "readyTransitions")
        cleanDisconnects = intField(line, "cleanDisconnects")
        unexpectedDisconnects = intField(line, "unexpectedDisconnects")
        contentAckSuccesses = intField(line, "contentAckSuccesses")
        contentAckFailures = intField(line, "contentAckFailures")
        connectLatencySum = longField(line, "connectLatencySum")
        connectLatencyCount = intField(line, "connectLatencyCount")
        connectLatencyMax = longField(line, "connectLatencyMax")
        handshakeLatencySum = longField(line, "handshakeLatencySum")
        handshakeLatencyCount = intField(line, "handshakeLatencyCount")
        handshakeLatencyMax = longField(line, "handshakeLatencyMax")
        lastFailureStage = jsonStringValue(line, "lastFailureStage")
        lastFailureKind = jsonStringValue(line, "lastFailureKind")
        lastGattStatus = intFieldOrNull(line, "lastGattStatus")
    }

    // --- Event vocabulary ---

    companion object {
        /** Records kept before the oldest is evicted. */
        const val MAX_RECORDS = 512

        const val EVENT_CONNECT_REQUEST = "connect_request"
        const val EVENT_RECONNECT_REQUEST = "reconnect_request"
        const val EVENT_AWAIT_WAKE_CONNECT = "await_wake_connect"
        const val EVENT_CONNECT_REFUSED = "connect_refused"
        const val EVENT_DISCONNECT_REQUEST = "disconnect_request"
        const val EVENT_FORGET_REQUEST = "forget_request"
        const val EVENT_BOND_STARTED = "bond_started"
        const val EVENT_BOND_OK = "bond_ok"
        const val EVENT_BOND_STATE = "bond_state"
        const val EVENT_GATT_CONNECT = "gatt_connect"
        const val EVENT_GATT_CONNECTED = "gatt_connected"
        const val EVENT_GATT_STATUS_FAILED = "gatt_status_failed"
        const val EVENT_MTU_NEGOTIATED = "mtu_negotiated"
        const val EVENT_SERVICES_DISCOVERED = "services_discovered"
        const val EVENT_CCCD_DONE = "cccd_done"
        const val EVENT_HELLO_SENT = "hello_sent"
        const val EVENT_HELLO_ACK = "hello_ack"
        const val EVENT_READY = "ready"
        const val EVENT_CONTENT_SEND_STARTED = "content_send_started"
        const val EVENT_CONTENT_ACK_OK = "content_ack_ok"
        const val EVENT_CONTENT_ACK_MISMATCH = "content_ack_mismatch"
        const val EVENT_CONTENT_SEND_TIMEOUT = "content_send_timeout"
        const val EVENT_CONTENT_SEND_FAILED = "content_send_failed"
        const val EVENT_LINK_CLOSED_UNEXPECTED = "link_closed_unexpected"
        const val EVENT_FAILURE = "setup_failed"
        const val EVENT_APP_FOREGROUND = "app_foreground"
        const val EVENT_APP_BACKGROUND = "app_background"

        const val PHASE_READY = "Ready"

        private const val EVENT_LINE_PREFIX = "{\"type\":\"event\""

        /**
         * Fixed diagnostic label for a [CompanionError]. Only structural
         * information survives; reader-supplied message text is dropped.
         */
        fun diagnosticLabel(error: CompanionError): String = when (error) {
            is CompanionError.BluetoothOff -> "bluetooth_off"
            is CompanionError.PermissionsMissing -> "permissions_missing"
            is CompanionError.ScanFailed -> "scan_failed"
            is CompanionError.PairingFailed -> "pairing_failed"
            is CompanionError.ConnectionFailed -> "connection_failed"
            is CompanionError.InsufficientMtu -> "insufficient_mtu"
            is CompanionError.IncompatibleGatt -> "incompatible_gatt"
            is CompanionError.IncompatibleVersion -> "incompatible_version"
            is CompanionError.NotPaired -> "not_paired"
            is CompanionError.SetupTimeout -> "setup_timeout"
            is CompanionError.SendRejected -> "send_rejected"
            is CompanionError.SendTimeout -> "send_timeout"
            is CompanionError.ProtocolViolation -> "protocol_violation"
            is CompanionError.LinkLost -> "link_lost"
        }

        fun phaseLabel(phase: LinkPhase): String = when (phase) {
            is LinkPhase.Idle -> "Idle"
            is LinkPhase.Bonding -> "Bonding"
            is LinkPhase.Connecting -> "Connecting"
            is LinkPhase.NegotiatingMtu -> "NegotiatingMtu"
            is LinkPhase.Discovering -> "Discovering"
            is LinkPhase.Subscribing -> "Subscribing"
            is LinkPhase.Handshaking -> "Handshaking"
            is LinkPhase.Ready -> PHASE_READY
            is LinkPhase.Failed -> "Failed"
        }

        private fun encodeEvent(event: DiagnosticEvent): String = buildString {
            append("""{"type":"event","seq":${event.seq},"ts":${event.uptimeMs}""")
            event.attempt?.let { append(",").append("\"attempt\":").append(jsonString(it)) }
            append(",").append("\"event\":").append(jsonString(event.kind))
            event.phase?.let { append(",").append("\"phase\":").append(jsonString(it)) }
            event.gattStatus?.let { append(",").append("\"gattStatus\":").append(it) }
            event.detail?.let { append(",").append("\"detail\":").append(jsonString(it)) }
            append("}")
        }

        private fun decodeEvent(line: String): DiagnosticEvent? {
            val seq = longFieldOrNull(line, "seq") ?: return null
            return DiagnosticEvent(
                seq = seq,
                attempt = jsonStringValue(line, "attempt"),
                kind = jsonStringValue(line, "event") ?: return null,
                phase = jsonStringValue(line, "phase"),
                gattStatus = intFieldOrNull(line, "gattStatus"),
                detail = jsonStringValue(line, "detail"),
                uptimeMs = longFieldOrNull(line, "ts") ?: 0L,
            )
        }

        private fun jsonNullable(value: String?): String =
            if (value == null) "null" else jsonString(value)

        private fun jsonString(value: String): String {
            val sb = StringBuilder(value.length + 2)
            sb.append('"')
            for (ch in value) {
                when (ch) {
                    '\\' -> sb.append("\\\\")
                    '"' -> sb.append("\\\"")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> if (ch < ' ') sb.append("\\u%04x".format(ch.code)) else sb.append(ch)
                }
            }
            sb.append('"')
            return sb.toString()
        }

        private fun intField(line: String, key: String): Int = intFieldOrNull(line, key) ?: 0
        private fun longField(line: String, key: String): Long = longFieldOrNull(line, key) ?: 0L

        private fun intFieldOrNull(line: String, key: String): Int? =
            longFieldOrNull(line, key)?.let { if (it in Int.MIN_VALUE..Int.MAX_VALUE) it.toInt() else null }

        private fun longFieldOrNull(line: String, key: String): Long? {
            val marker = "\"$key\":"
            val start = line.indexOf(marker)
            if (start < 0) return null
            var end = start + marker.length
            while (end < line.length && (line[end].isDigit() || line[end] == '-')) end++
            if (end == start + marker.length) return null
            return line.substring(start + marker.length, end).toLongOrNull()
        }

        private fun jsonStringValue(line: String, key: String): String? {
            val marker = "\"$key\":"
            val start = line.indexOf(marker)
            if (start < 0) return null
            val openQuote = start + marker.length
            if (openQuote >= line.length || line[openQuote] != '"') return null
            val sb = StringBuilder()
            var i = openQuote + 1
            while (i < line.length) {
                val ch = line[i]
                if (ch == '\\' && i + 1 < line.length) {
                    when (val next = line[i + 1]) {
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            val hex = line.substring(i + 2, (i + 6).coerceAtMost(line.length))
                            sb.append(hex.toIntOrNull(16)?.toChar() ?: '?')
                            i += 4
                        }
                        else -> sb.append(next)
                    }
                    i += 2
                } else if (ch == '"') {
                    return sb.toString()
                } else {
                    sb.append(ch)
                    i++
                }
            }
            return null
        }

        internal fun utcNow(): String = java.time.Instant.now().toString().substring(0, 19) + "Z"
    }
}

/** Storage seam for the trace: one JSONL file in app-private storage. */
interface DiagnosticTraceStore {
    fun readLines(): List<String>
    fun writeAll(lines: List<String>)
}

/** A trace sink that keeps nothing; used when diagnostics are disabled. */
object NoopDiagnosticTraceStore : DiagnosticTraceStore {
    override fun readLines(): List<String> = emptyList()
    override fun writeAll(lines: List<String>) = Unit
}

/**
 * Atomic JSONL file store. Every write replaces the file through a temporary
 * file and a rename, so a process kill never leaves a torn trace.
 */
class FileDiagnosticTraceStore(private val file: java.io.File) : DiagnosticTraceStore {

    override fun readLines(): List<String> =
        if (file.exists()) runCatching { file.readLines() }.getOrDefault(emptyList()) else emptyList()

    override fun writeAll(lines: List<String>) {
        val parent = file.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()
        val tmp = java.io.File(file.parentFile, file.name + ".tmp")
        tmp.writeText(lines.joinToString(separator = "\n", postfix = "\n"))
        if (!tmp.renameTo(file)) {
            // Rename across the same directory cannot fail in practice; keep
            // the last good trace rather than crashing a diagnostic path.
            tmp.delete()
        }
    }
}
