package fans.crosspoint.pal.ble

import fans.crosspoint.pal.domain.ButtonEventSnapshot
import fans.crosspoint.pal.domain.CompanionDeviceSnapshot
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

/**
 * The single owner of the companion GATT connection and session.
 *
 * All screens express intent here (scan, connect, send, disconnect, forget);
 * no screen, notification, or background component creates its own GATT
 * lifecycle. The session is user-initiated and foreground-scoped: there is no
 * background service, automatic reconnect, or scan loop.
 *
 * Protocol sequencing follows the firmware contract: MTU before service
 * discovery, `hello` before any other message, content sends acknowledged by
 * `content_ack` for the exact contentId, and every failure is explicit —
 * there is no unencrypted or unbounded fallback.
 */
class CompanionConnectionManager(
    private val scope: CoroutineScope,
    private val transport: GattTransport,
    private val environment: BleEnvironment,
    private val identityStore: CompanionIdentityStore,
    private val scanner: CompanionScanner,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {

    private val identityInternal = MutableStateFlow<CompanionIdentity?>(null)
    private val linkPhaseInternal = MutableStateFlow<LinkPhase>(LinkPhase.Idle)
    val linkPhase: StateFlow<LinkPhase> = linkPhaseInternal.asStateFlow()

    private val scannedInternal = MutableStateFlow<List<ScannedDevice>>(emptyList())
    val scannedDevices: StateFlow<List<ScannedDevice>> = scannedInternal.asStateFlow()

    private val scanningInternal = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = scanningInternal.asStateFlow()

    private val lastButtonEventInternal = MutableStateFlow<ButtonEventSnapshot?>(null)
    val lastButtonEvent: StateFlow<ButtonEventSnapshot?> = lastButtonEventInternal.asStateFlow()

    private val errorEventsInternal = MutableSharedFlow<CompanionError>(extraBufferCapacity = 8)
    val errorEvents: SharedFlow<CompanionError> = errorEventsInternal.asSharedFlow()

    private val pairedInternal = MutableStateFlow(false)
    private val lastConnectedAtInternal = MutableStateFlow<Long?>(null)

    /** Presentation-ready snapshot for the Home and Settings screens. */
    val device: StateFlow<CompanionDeviceSnapshot?> =
        combine(
            identityInternal,
            linkPhase,
            pairedInternal,
            lastConnectedAtInternal,
        ) { identity, phase, paired, lastConnectedAt ->
            identity?.let {
                CompanionDeviceSnapshot(
                    name = it.name ?: DEFAULT_NAME,
                    connected = phase == LinkPhase.Ready,
                    paired = paired && transport.isBonded(it.address),
                    notificationsMirroring = false,
                    ready = phase == LinkPhase.Ready,
                    batteryPercent = null,
                    lastSeenLabel = lastSeenLabel(lastConnectedAt),
                )
            }
        }.stateIn(scope, SharingStarted.Eagerly, null)

    @Volatile
    private var userDisconnect = false

    @Volatile
    private var responseWaiter: CompletableDeferred<DeviceMessage>? = null

    // The GATT link reference is shared with binder-thread callbacks (via
    // onClosed) and with main-scope coroutines; every access is atomic.
    private val link = AtomicReference<GattLink?>(null)
    private var connectJob: Job? = null
    private var scanJob: Job? = null
    private val sendMutex = Mutex()
    private val contentCounter = AtomicInteger(0)

    init {
        identityStore.identity
            .onEach { identity ->
                identityInternal.value = identity
                pairedInternal.value = identity != null && transport.isBonded(identity.address)
            }
            .launchIn(scope)
        transport.bondEvents
            .onEach { address ->
                val identity = identityInternal.value
                if (identity != null && identity.address == address) {
                    pairedInternal.value = transport.isBonded(address)
                }
            }
            .launchIn(scope)
    }

    // --- Scanning ---

    /** Permissions the BLE workflow still needs, for the runtime dialog. */
    fun missingPermissions(): List<String> = environment.missingPermissions()

    /** Permissions the BLE workflow requires on this Android version. */
    fun requiredPermissions(): List<String> = environment.requiredPermissions

    /** Starts a bounded scan; emits on [errorEvents] when guards fail. */
    fun startScan() {
        if (scanningInternal.value) return
        if (environment.missingPermissions().isNotEmpty()) {
            errorEventsInternal.tryEmit(CompanionError.PermissionsMissing)
            return
        }
        if (!environment.isBluetoothEnabled) {
            errorEventsInternal.tryEmit(CompanionError.BluetoothOff)
            return
        }
        scanJob?.cancel()
        scanJob = scope.launch {
            scanningInternal.value = true
            val seen = LinkedHashMap<String, ScannedDevice>()
            try {
                withTimeout(SCAN_WINDOW_MS) {
                    scanner.scanResults().collect { result ->
                        val known = seen[result.address]
                        if (known == null || (known.name == null && result.name != null)) {
                            seen[result.address] = result
                            scannedInternal.value = seen.values.toList()
                        }
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                // The scan window simply ended.
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (scanError: Exception) {
                errorEventsInternal.tryEmit(CompanionError.ScanFailed)
            } finally {
                scanningInternal.value = false
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        scanningInternal.value = false
    }

    // --- Connection lifecycle ---

    /**
     * Connects to the reader at [address], bonding first when needed. A
     * connection attempt must be user-initiated; the call is ignored while an
     * attempt or session is already running.
     */
    fun connect(address: String, name: String?) {
        if (connectJob?.isActive == true) return
        if (linkPhaseInternal.value == LinkPhase.Ready) return
        if (environment.missingPermissions().isNotEmpty()) {
            linkPhaseInternal.value = LinkPhase.Failed(CompanionError.PermissionsMissing)
            return
        }
        if (!environment.isBluetoothEnabled) {
            linkPhaseInternal.value = LinkPhase.Failed(CompanionError.BluetoothOff)
            return
        }
        userDisconnect = false
        connectJob = scope.launch {
            try {
                runSession(address, name)
            } catch (failure: CompanionFailure) {
                settle(failure.error)
                cleanupSession()
            } catch (lost: LinkLostException) {
                settle(CompanionError.LinkLost)
                cleanupSession()
            } catch (operation: GattOperationException) {
                settle(CompanionError.ConnectionFailed(operation.gattStatus))
                cleanupSession()
            } catch (incompatible: IncompatibleGattException) {
                settle(CompanionError.IncompatibleGatt)
                cleanupSession()
            } catch (cancelled: CancellationException) {
                // A user disconnect may land between link assignment and the
                // next suspension; the fresh handle must not leak.
                cleanupSession()
                throw cancelled
            }
            // A successful runSession keeps the link and the notification
            // wiring alive: the session is now Ready.
        }
    }

    /** Reconnects to the remembered companion device. */
    fun reconnect() {
        val identity = identityInternal.value ?: return
        connect(identity.address, identity.name)
    }

    /** User-initiated disconnect; the remembered device stays paired. */
    fun disconnect() {
        userDisconnect = true
        connectJob?.cancel()
        connectJob = null
        closeLink()
        responseWaiter = null
        linkPhaseInternal.value = LinkPhase.Idle
    }

    /**
     * Forgets the companion: disconnects, clears the app identity and removes
     * the system bond where the platform permits. Returns false when the bond
     * had to be removed through system settings instead. The reader side of
     * the pairing reset stays manual per the protocol contract.
     */
    suspend fun forgetDevice(): Boolean {
        userDisconnect = true
        connectJob?.cancel()
        connectJob = null
        closeLink()
        responseWaiter = null
        val identity = identityInternal.value
        identityStore.clear()
        identityInternal.value = null
        lastButtonEventInternal.value = null
        lastConnectedAtInternal.value = null
        pairedInternal.value = false
        linkPhaseInternal.value = LinkPhase.Idle
        return identity == null || transport.removeBond(identity.address)
    }

    // --- Content ---

    /**
     * Sends one content message and waits for `content_ack` for the exact
     * contentId. Only one send is in flight at a time.
     */
    suspend fun sendContent(title: String, body: String): SendResult {
        val currentLink = link.get()
            ?.takeIf { linkPhaseInternal.value == LinkPhase.Ready }
            ?: return SendResult.NotReady
        return sendMutex.withLock {
            val contentId = nextContentId()
            val payload = CompanionCodec.content(title = title, body = body, contentId = contentId)
            val waiter = CompletableDeferred<DeviceMessage>()
            responseWaiter = waiter
            try {
                withTimeout(SEND_TIMEOUT_MS) {
                    currentLink.writeControl(UUID.fromString(CompanionProtocol.CONTROL_UUID), payload)
                    when (val message = waiter.await()) {
                        is DeviceMessage.ContentAck ->
                            if (message.contentId == contentId) {
                                SendResult.Sent
                            } else {
                                terminateSession(
                                    CompanionError.ProtocolViolation("content_ack contentId mismatch"),
                                )
                                SendResult.Failed(
                                    CompanionError.ProtocolViolation("content_ack contentId mismatch"),
                                )
                            }
                        is DeviceMessage.Error ->
                            if (message.code == CompanionProtocol.ERROR_BAD_MESSAGE ||
                                message.code == CompanionProtocol.ERROR_HELLO_FIRST
                            ) {
                                // The session stays alive on these errors.
                                SendResult.Failed(
                                    CompanionError.SendRejected(message.code, message.message),
                                )
                            } else {
                                val error =
                                    CompanionError.ProtocolViolation("unexpected error ${message.code}")
                                terminateSession(error)
                                SendResult.Failed(error)
                            }
                        is DeviceMessageLinkClosed -> SendResult.Failed(CompanionError.LinkLost)
                        else -> {
                            val error =
                                CompanionError.ProtocolViolation("unexpected ${message::class.simpleName}")
                            terminateSession(error)
                            SendResult.Failed(error)
                        }
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                if (!currentCoroutineContext().isActive) throw timeout
                SendResult.Failed(CompanionError.SendTimeout)
            } catch (lost: LinkLostException) {
                SendResult.Failed(CompanionError.LinkLost)
            } catch (operation: GattOperationException) {
                SendResult.Failed(CompanionError.ConnectionFailed(operation.gattStatus))
            } finally {
                responseWaiter = null
            }
        }
    }

    // --- Session internals ---

    private suspend fun runSession(address: String, scannedName: String?) {
        closeLink() // never two live GATT lifecycles
        val identityName = identityInternal.value
            ?.takeIf { it.address == address }
            ?.name
        val deviceName = scannedName ?: identityName

        linkPhaseInternal.value = LinkPhase.Bonding
        if (!transport.ensureBonded(address)) throw CompanionFailure(CompanionError.PairingFailed)
        // The bond is the pairing event: the identity becomes durable here.
        identityStore.setIdentity(CompanionIdentity(address = address, name = deviceName))
        pairedInternal.value = transport.isBonded(address)

        linkPhaseInternal.value = LinkPhase.Connecting
        val sessionLink = within(LinkPhase.Connecting, STEP_TIMEOUT_MS) { transport.connect(address) }
        link.set(sessionLink)
        // The link identity travels with both callbacks: a late callback from
        // a superseded link must never disturb the live session.
        sessionLink.onNotification = { bytes -> onNotification(sessionLink, bytes) }
        sessionLink.onClosed = { onLinkClosed(sessionLink) }

        linkPhaseInternal.value = LinkPhase.NegotiatingMtu
        val mtu = within(LinkPhase.NegotiatingMtu, STEP_TIMEOUT_MS) {
            sessionLink.requestMtu(CompanionProtocol.REQUESTED_MTU)
        }
        if (mtu < CompanionProtocol.MIN_USABLE_MTU) {
            throw CompanionFailure(CompanionError.InsufficientMtu(mtu))
        }

        linkPhaseInternal.value = LinkPhase.Discovering
        val characteristics = within(LinkPhase.Discovering, STEP_TIMEOUT_MS) { sessionLink.discoverServices() }

        linkPhaseInternal.value = LinkPhase.Subscribing
        within(LinkPhase.Subscribing, STEP_TIMEOUT_MS) {
            sessionLink.subscribeEvent(characteristics.eventUuid)
        }

        linkPhaseInternal.value = LinkPhase.Handshaking
        val helloWaiter = CompletableDeferred<DeviceMessage>()
        responseWaiter = helloWaiter
        sessionLink.writeControl(UUID.fromString(CompanionProtocol.CONTROL_UUID), CompanionCodec.hello())
        when (val message = within(LinkPhase.Handshaking, HANDSHAKE_TIMEOUT_MS) { helloWaiter.await() }) {
            is DeviceMessage.HelloAck ->
                if (message.version != CompanionProtocol.VERSION) {
                    throw CompanionFailure(CompanionError.IncompatibleVersion)
                }
            is DeviceMessage.Error ->
                throw CompanionFailure(
                    if (message.code == CompanionProtocol.ERROR_VERSION) {
                        CompanionError.IncompatibleVersion
                    } else {
                        CompanionError.ProtocolViolation("error ${message.code} during handshake")
                    },
                )
            is DeviceMessageLinkClosed -> throw CompanionFailure(CompanionError.LinkLost)
            else -> throw CompanionFailure(
                CompanionError.ProtocolViolation("unexpected ${message::class.simpleName} during handshake"),
            )
        }

        lastConnectedAtInternal.value = clockMillis()
        linkPhaseInternal.value = LinkPhase.Ready
    }

    private fun onNotification(source: GattLink, bytes: ByteArray) {
        // A late notification dispatched from a superseded link is dead
        // traffic; it must not reach the live session's waiter or diagnostics.
        if (link.get() !== source) return
        val message = try {
            CompanionCodec.decodeNotification(bytes)
        } catch (malformed: MalformedNotification) {
            // Runs on a binder thread: never throw out of a GATT callback.
            terminateSession(CompanionError.ProtocolViolation("malformed notification"))
            return
        }
        if (message is DeviceMessage.Button) {
            // Button events are unsolicited reader input; they are never the
            // reply a waiter awaits, even while a send is in flight.
            onUnawaitedMessage(message)
            return
        }
        val waiter = responseWaiter
        if (waiter != null) {
            responseWaiter = null
            waiter.complete(message)
        } else {
            onUnawaitedMessage(message)
        }
    }

    private fun onUnawaitedMessage(message: DeviceMessage) {
        when (message) {
            is DeviceMessage.Button -> {
                lastButtonEventInternal.value = ButtonEventSnapshot(
                    buttonLabel = buttonLabel(message.button),
                    timeLabel = LocalTime.now(zone).format(TIME_FORMAT),
                )
            }
            else -> Unit // late or stray reader replies are not session state
        }
    }

    private fun onLinkClosed(closedLink: GattLink) {
        // Only the current link may settle the waiters and the phase; a stale
        // callback from a superseded link is a no-op.
        if (!link.compareAndSet(closedLink, null)) return
        responseWaiter?.complete(DeviceMessageLinkClosed)
        responseWaiter = null
        if (!userDisconnect && linkPhaseInternal.value != LinkPhase.Idle) {
            linkPhaseInternal.value = LinkPhase.Failed(CompanionError.LinkLost)
        }
    }

    /** Ends the session with an explicit error; used from within a send. */
    private fun terminateSession(error: CompanionError) {
        closeLink()
        linkPhaseInternal.value = LinkPhase.Failed(error)
    }

    /** Frees the GATT wiring after a failed or finished connect attempt. */
    private fun cleanupSession() {
        closeLink()
        responseWaiter = null
    }

    /** Maps the failure of a connection attempt onto the final phase. */
    private fun settle(error: CompanionError) {
        linkPhaseInternal.value =
            if (userDisconnect) LinkPhase.Idle else LinkPhase.Failed(error)
    }

    private fun closeLink() {
        link.getAndSet(null)?.let { current ->
            current.onClosed = null
            current.onNotification = null
            current.close()
        }
    }

    private suspend fun <T> within(phase: LinkPhase, timeoutMs: Long, block: suspend () -> T): T =
        try {
            withTimeout(timeoutMs) { block() }
        } catch (timeout: TimeoutCancellationException) {
            if (!currentCoroutineContext().isActive) throw timeout
            throw CompanionFailure(CompanionError.SetupTimeout(phase))
        }

    private fun nextContentId(): String {
        val counter = contentCounter.incrementAndGet()
        val time = clockMillis() % 100_000_000_000L
        val id = "c" + time.toString(36) + "-" + counter.toString(36)
        return id.take(CompanionProtocol.MAX_CONTENT_ID_BYTES)
    }

    private fun clockMillis(): Long = System.currentTimeMillis()

    private fun lastSeenLabel(lastConnectedAt: Long?): String {
        lastConnectedAt ?: return ""
        val ageMinutes = (clockMillis() - lastConnectedAt) / 60_000
        return when {
            ageMinutes < 1 -> "just now"
            ageMinutes < 60 -> "$ageMinutes min ago"
            else -> "${ageMinutes / 60} h ago"
        }
    }

    private fun buttonLabel(button: String): String = when (button) {
        CompanionProtocol.BUTTON_BACK -> "Back button"
        CompanionProtocol.BUTTON_CONFIRM -> "Confirm button"
        CompanionProtocol.BUTTON_LEFT -> "Left button"
        CompanionProtocol.BUTTON_RIGHT -> "Right button"
        CompanionProtocol.BUTTON_UP -> "Up button"
        CompanionProtocol.BUTTON_DOWN -> "Down button"
        else -> button
    }

    private class CompanionFailure(val error: CompanionError) : Exception()

    private companion object {
        const val SCAN_WINDOW_MS = 12_000L
        const val STEP_TIMEOUT_MS = 10_000L
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val SEND_TIMEOUT_MS = 10_000L
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)
        const val DEFAULT_NAME = "CrossPoint Pal"
    }
}

/** Signalling message for waits: the GATT link closed. */
internal data object DeviceMessageLinkClosed : DeviceMessage
