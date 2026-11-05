package fans.crosspoint.pal.ble

import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher

/** Test doubles for the GATT seam. All behaviour is scripted by the tests. */
class FakeGattTransport : GattTransport {

    var bondResult = true
    var bonded = false
    var removeBondResult = true

    val bondsRequested = mutableListOf<String>()
    val connectsRequested = mutableListOf<String>()
    val bondsRemoved = mutableListOf<String>()

    private val linkQueue = ArrayDeque<FakeGattLink>()
    val createdLinks = mutableListOf<FakeGattLink>()

    private val bondEventsInternal = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val bondEvents: Flow<String> = bondEventsInternal

    /** The link the next [connect] call returns; created on demand. */
    fun nextLink(): FakeGattLink = FakeGattLink().also {
        createdLinks.add(it)
        linkQueue.addLast(it)
    }

    override suspend fun ensureBonded(address: String): Boolean {
        bondsRequested.add(address)
        bonded = true
        return bondResult
    }

    override suspend fun connect(address: String): GattLink =
        linkQueue.removeFirstOrNull()
            ?: FakeGattLink().also { createdLinks.add(it) }

    override fun isBonded(address: String): Boolean = bonded

    override suspend fun removeBond(address: String): Boolean {
        bondsRemoved.add(address)
        if (removeBondResult) bonded = false
        return removeBondResult
    }

    fun emitBonded(address: String) {
        bonded = true
        bondEventsInternal.tryEmit(address)
    }
}

class FakeBleEnvironment(
    override val isBluetoothEnabled: Boolean = true,
    private val missing: List<String> = emptyList(),
) : BleEnvironment {
    override val requiredPermissions: List<String> = listOf("android.permission.BLUETOOTH_CONNECT")
    override fun missingPermissions(): List<String> = missing
    override fun isBonded(address: String): Boolean = false
}

class FakeScanner : CompanionScanner {
    private val flow = MutableSharedFlow<ScannedDevice>(extraBufferCapacity = 16)

    /** How many times a scan window was opened: detects retry/scan loops. */
    var scanStarts = 0
        private set

    /** How many scan windows are currently collecting. */
    var activeScans = 0
        private set

    override fun scanResults(): Flow<ScannedDevice> = flow
        .onStart {
            scanStarts++
            activeScans++
        }
        .onCompletion { activeScans-- }

    fun emit(device: ScannedDevice) {
        flow.tryEmit(device)
    }
}

/**
 * Scripted GATT link: every operation suspends until the test supplies the
 * outcome, mirroring the asynchronous Android callbacks.
 */
class FakeGattLink : GattLink {

    override var onNotification: ((ByteArray) -> Unit)? = null

    @Volatile
    override var onClosed: (() -> Unit)? = null

    val written = mutableListOf<ByteArray>()
    val requestedMtuValues = mutableListOf<Int>()
    val subscribed = mutableListOf<UUID>()
    var isClosed = false
        private set

    private val mtuReplies = Channel<Int>(Channel.UNLIMITED)
    private val discoveryReplies = Channel<DiscoveredCharacteristics>(Channel.UNLIMITED)
    private val subscriptionReplies = Channel<Unit>(Channel.UNLIMITED)
    private val writeReplies = Channel<Unit>(Channel.UNLIMITED)

    private var deferredCloseCallback: (() -> Unit)? = null
    private var deferredNotification: Pair<((ByteArray) -> Unit)?, ByteArray>? = null

    override suspend fun requestMtu(mtu: Int): Int {
        requestedMtuValues.add(mtu)
        return mtuReplies.receive()
    }

    override suspend fun discoverServices(): DiscoveredCharacteristics =
        discoveryReplies.receive()

    override suspend fun subscribeEvent(characteristic: UUID) {
        subscribed.add(characteristic)
        subscriptionReplies.receive()
    }

    override suspend fun writeControl(characteristic: UUID, payload: ByteArray) {
        written.add(payload)
        writeReplies.receive()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        closeOperationChannels()
        val callback = onClosed
        onClosed = null
        callback?.invoke()
    }

    /**
     * Simulates the binder thread having entered close() and captured the
     * manager callback before the manager could clear it; the captured
     * callback is delivered later by [fireDeferredCloseCallback]. Mirrors the
     * real link's close ordering without invoking the callback yet.
     */
    fun enterCloseOnBinderThread() {
        if (isClosed) return
        isClosed = true
        closeOperationChannels()
        deferredCloseCallback = onClosed
        onClosed = null
    }

    fun fireDeferredCloseCallback() {
        deferredCloseCallback?.invoke()
        deferredCloseCallback = null
    }

    /**
     * Simulates the binder thread having dispatched a notification callback
     * before the manager could clear it; the captured callback runs later via
     * [fireDeferredNotification].
     */
    fun enterNotificationOnBinderThread(json: String) {
        deferredNotification = onNotification to json.toByteArray(Charsets.UTF_8)
    }

    fun fireDeferredNotification() {
        val (callback, bytes) = deferredNotification ?: return
        deferredNotification = null
        callback?.invoke(bytes)
    }

    private fun closeOperationChannels() {
        mtuReplies.close(LinkLostException())
        discoveryReplies.close(LinkLostException())
        subscriptionReplies.close(LinkLostException())
        writeReplies.close(LinkLostException())
    }

    fun replyMtu(value: Int) {
        mtuReplies.trySend(value)
    }

    fun replyDiscovered() {
        discoveryReplies.trySend(
            DiscoveredCharacteristics(
                controlUuid = UUID.fromString(CompanionProtocol.CONTROL_UUID),
                eventUuid = UUID.fromString(CompanionProtocol.EVENT_UUID),
            ),
        )
    }

    fun replySubscribed() {
        subscriptionReplies.trySend(Unit)
    }

    fun replyWritten() {
        writeReplies.trySend(Unit)
    }

    fun deliverNotification(json: String) {
        onNotification?.invoke(json.toByteArray(Charsets.UTF_8))
    }

    /** Simulates an unexpected link loss (not a manager-initiated close). */
    fun simulateLinkLoss() {
        close()
    }
}

/**
 * Builds a manager wired to scripted doubles. The scope shares the test
 * scheduler so virtual-time timeouts fire, and the shared job keeps the
 * manager's flows alive for the whole test.
 */
fun TestScope.managerWithFakes(
    transport: FakeGattTransport = FakeGattTransport(),
    environment: BleEnvironment = FakeBleEnvironment(),
    identityStore: CompanionIdentityStore = InMemoryCompanionIdentityStore(),
    scanner: FakeScanner = FakeScanner(),
): CompanionConnectionManager = CompanionConnectionManager(
    scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
    transport = transport,
    environment = environment,
    identityStore = identityStore,
    scanner = scanner,
)

/** Drives the current connect attempt through every setup step to Ready. */
fun TestScope.completeSetup(link: FakeGattLink) {
    link.replyMtu(CompanionProtocol.REQUESTED_MTU)
    testScheduler.runCurrent()
    link.replyDiscovered()
    testScheduler.runCurrent()
    link.replySubscribed()
    testScheduler.runCurrent()
    link.replyWritten()
    testScheduler.runCurrent()
    link.deliverNotification("""{"op":"hello_ack","version":1}""")
    testScheduler.runCurrent()
}

/** Connects and completes setup, returning the scripted link. */
fun TestScope.readySession(
    transport: FakeGattTransport,
    manager: CompanionConnectionManager,
    address: String = "AA:BB",
    name: String? = "CrossPoint Pal",
): FakeGattLink {
    manager.connect(address, name)
    val link = transport.createdLinks.single()
    completeSetup(link)
    return link
}
