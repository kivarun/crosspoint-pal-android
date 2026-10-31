package fans.crosspoint.pal.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Session sequencing against the firmware contract, driven through the
 * scripted GATT seam. Every phase boundary of the connect flow, the send
 * handshake, and the failure paths is covered here.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionConnectionManagerTest {

    private class Fakes {
        val transport = FakeGattTransport()
        val environment = FakeBleEnvironment()
        val identityStore = InMemoryCompanionIdentityStore()
        val scanner = FakeScanner()
    }

    private fun TestScope.build(fakes: Fakes = Fakes()) = fakes to managerWithFakes(
        transport = fakes.transport,
        environment = fakes.environment,
        identityStore = fakes.identityStore,
        scanner = fakes.scanner,
    )

    @Test
    fun `connect walks every phase to ready and sends hello`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", "CrossPoint Pal")
        val link = fakes.transport.createdLinks.single()
        completeSetup(link)

        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
        assertEquals(CompanionCodec.hello().toList(), link.written.single().toList())
        // The choreography is the contract: MTU before discovery, subscribe
        // on the Event characteristic, hello before anything else.
        assertEquals(listOf(517), link.requestedMtuValues)
        assertEquals(
            listOf(java.util.UUID.fromString(CompanionProtocol.EVENT_UUID)),
            link.subscribed,
        )
        assertTrue(fakes.transport.bondsRequested.contains("AA:BB"))
        // Bonding is the durable pairing event.
        assertEquals("AA:BB", fakes.identityStore.identity.first()?.address)
        assertEquals("CrossPoint Pal", fakes.identityStore.identity.first()?.name)

        // The snapshot reports the real session state.
        val device = manager.device.value
        assertNotNull(device)
        assertTrue(device!!.connected)
        assertTrue(device.ready)
        assertTrue(device.paired)
        assertNull(device.batteryPercent)
    }

    @Test
    fun `connect without permissions fails before any radio work`() = runTest {
        val fakes = Fakes()
        val manager = managerWithFakes(
            transport = fakes.transport,
            environment = FakeBleEnvironment(missing = listOf("p")),
        )
        manager.connect("AA:BB", null)

        assertEquals(LinkPhase.Failed(CompanionError.PermissionsMissing), manager.linkPhase.value)
        assertTrue(fakes.transport.bondsRequested.isEmpty())
    }

    @Test
    fun `connect with bluetooth off fails before any radio work`() = runTest {
        val fakes = Fakes()
        val manager = managerWithFakes(
            transport = fakes.transport,
            environment = FakeBleEnvironment(isBluetoothEnabled = false),
        )
        manager.connect("AA:BB", null)

        assertEquals(LinkPhase.Failed(CompanionError.BluetoothOff), manager.linkPhase.value)
        assertTrue(fakes.transport.bondsRequested.isEmpty())
    }

    @Test
    fun `failed bonding reports pairing failure`() = runTest {
        val (fakes, manager) = build()
        fakes.transport.bondResult = false
        manager.connect("AA:BB", null)
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Failed(CompanionError.PairingFailed), manager.linkPhase.value)
        assertTrue(fakes.transport.createdLinks.isEmpty())
        assertNull(fakes.identityStore.identity.first())
    }

    @Test
    fun `a too small mtu refuses the session`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        val link = fakes.transport.createdLinks.single()
        link.replyMtu(247)
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Failed(CompanionError.InsufficientMtu(247)), manager.linkPhase.value)
        assertTrue(link.written.isEmpty())
        assertTrue(link.isClosed)
    }

    @Test
    fun `an incompatible hello_ack version fails closed`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        val link = fakes.transport.createdLinks.single()
        link.replyMtu(517)
        testScheduler.runCurrent()
        link.replyDiscovered()
        testScheduler.runCurrent()
        link.replySubscribed()
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification("""{"op":"hello_ack","version":2}""")
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Failed(CompanionError.IncompatibleVersion), manager.linkPhase.value)
        assertTrue(link.isClosed)
    }

    @Test
    fun `a version error during the handshake fails closed`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        val link = fakes.transport.createdLinks.single()
        link.replyMtu(517)
        testScheduler.runCurrent()
        link.replyDiscovered()
        testScheduler.runCurrent()
        link.replySubscribed()
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification(
            """{"op":"error","code":"version","message":"Unsupported protocol version"}""",
        )
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Failed(CompanionError.IncompatibleVersion), manager.linkPhase.value)
    }

    @Test
    fun `an unexpected message during the handshake fails closed`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        val link = fakes.transport.createdLinks.single()
        link.replyMtu(517)
        testScheduler.runCurrent()
        link.replyDiscovered()
        testScheduler.runCurrent()
        link.replySubscribed()
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification("""{"op":"content_ack","contentId":"c1"}""")
        testScheduler.runCurrent()

        assertTrue(manager.linkPhase.value is LinkPhase.Failed)
        assertTrue(link.isClosed)
    }

    @Test
    fun `an unexpected link loss during setup reports link lost`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        val link = fakes.transport.createdLinks.single()
        link.replyMtu(517)
        testScheduler.runCurrent()
        link.simulateLinkLoss()
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Failed(CompanionError.LinkLost), manager.linkPhase.value)
    }

    @Test
    fun `handshake timeout fails closed`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        val link = fakes.transport.createdLinks.single()
        link.replyMtu(517)
        testScheduler.runCurrent()
        link.replyDiscovered()
        testScheduler.runCurrent()
        link.replySubscribed()
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        // No hello_ack arrives; virtual time runs past the handshake timeout.

        advanceTimeBy(10_001)
        testScheduler.runCurrent()
        assertEquals(LinkPhase.Failed(CompanionError.SetupTimeout(LinkPhase.Handshaking)), manager.linkPhase.value)
        assertTrue(link.isClosed)
    }

    @Test
    fun `user disconnect during setup ends in idle`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", null)
        manager.disconnect()
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Idle, manager.linkPhase.value)
        // The remembered device survives a disconnect.
        assertNotNull(fakes.identityStore.identity.first())
    }

    @Test
    fun `reconnect after a loss opens a fresh link without duplicating the lifecycle`() = runTest {
        val (fakes, manager) = build()
        manager.connect("AA:BB", "CrossPoint Pal")
        val first = fakes.transport.createdLinks[0]
        completeSetup(first)
        assertEquals(LinkPhase.Ready, manager.linkPhase.value)

        first.simulateLinkLoss()
        testScheduler.runCurrent()
        assertEquals(LinkPhase.Failed(CompanionError.LinkLost), manager.linkPhase.value)
        assertTrue(first.isClosed)

        manager.reconnect()
        testScheduler.runCurrent()
        val second = fakes.transport.createdLinks[1]
        completeSetup(second)

        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
        assertFalse(second.isClosed)
        // The old lifecycle was never left dangling: exactly two links exist.
        assertEquals(2, fakes.transport.createdLinks.size)
    }

    @Test
    fun `a late close callback from a superseded link cannot disturb the new session`() = runTest {
        val (fakes, manager) = build()
        val first = readySession(fakes.transport, manager)
        // The binder thread has entered close() and captured the manager
        // callback before the manager could clear it.
        first.enterCloseOnBinderThread()

        manager.disconnect()
        testScheduler.runCurrent()
        manager.reconnect()
        val second = fakes.transport.createdLinks[1]
        completeSetup(second)
        assertEquals(LinkPhase.Ready, manager.linkPhase.value)

        // The stale callback arrives only after the new session is live.
        first.fireDeferredCloseCallback()
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
        assertFalse(second.isClosed)
        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()
        second.replyWritten()
        testScheduler.runCurrent()
        val sent = String(second.written.last(), Charsets.UTF_8)
        val id = Regex("\"contentId\":\"([^\"]+)\"").find(sent)!!.groupValues[1]
        second.deliverNotification("""{"op":"content_ack","contentId":"$id"}""")
        testScheduler.runCurrent()

        assertEquals(SendResult.Sent, result)
    }

    @Test
    fun `a content send is acknowledged by the exact contentId`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()

        val sent = String(link.written.last(), Charsets.UTF_8)
        val id = Regex("\"contentId\":\"([^\"]+)\"").find(sent)!!.groupValues[1]
        assertTrue(CompanionCodec.isPlainId(id))

        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification("""{"op":"content_ack","contentId":"$id"}""")
        testScheduler.runCurrent()

        assertEquals(SendResult.Sent, result)
        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
    }

    @Test
    fun `a mismatched content_ack fails the session closed`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification("""{"op":"content_ack","contentId":"different"}""")
        testScheduler.runCurrent()

        assertEquals(
            SendResult.Failed(CompanionError.ProtocolViolation("content_ack contentId mismatch")),
            result,
        )
        assertTrue(link.isClosed)
        assertTrue(manager.linkPhase.value is LinkPhase.Failed)
    }

    @Test
    fun `bad_message keeps the session alive and reports the rejection`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification(
            """{"op":"error","code":"bad_message","message":"Malformed control message"}""",
        )
        testScheduler.runCurrent()

        assertEquals(
            SendResult.Failed(CompanionError.SendRejected("bad_message", "Malformed control message")),
            result,
        )
        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
        assertFalse(link.isClosed)
    }

    @Test
    fun `an unexpected error code during send fails the session closed`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        link.deliverNotification("""{"op":"error","code":"version","message":"nope"}""")
        testScheduler.runCurrent()

        assertTrue(result is SendResult.Failed)
        assertTrue(link.isClosed)
    }

    @Test
    fun `a send times out when the reader stays silent`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        var result: SendResult? = null
        backgroundScope.launch { result = manager.sendContent("Morning", "Quiet") }
        testScheduler.runCurrent()
        link.replyWritten()
        testScheduler.runCurrent()
        // No content_ack; the send timeout fires on virtual time.

        advanceTimeBy(10_001)
        testScheduler.runCurrent()
        assertEquals(SendResult.Failed(CompanionError.SendTimeout), result)
        // The session itself stays up: the contract keeps the link alive.
        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
    }

    @Test
    fun `send without a ready session reports not ready`() = runTest {
        val (_, manager) = build()
        testScheduler.runCurrent()
        assertEquals(SendResult.NotReady, manager.sendContent("Morning", "Quiet"))
    }

    @Test
    fun `button events reach the diagnostic snapshot`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        link.deliverNotification("""{"op":"button","button":"back","pressed":true}""")
        testScheduler.runCurrent()
        assertEquals("Back button", manager.lastButtonEvent.value?.buttonLabel)

        link.deliverNotification("""{"op":"button","button":"right","pressed":false}""")
        testScheduler.runCurrent()
        assertEquals("Right button", manager.lastButtonEvent.value?.buttonLabel)
    }

    @Test
    fun `a malformed notification fails the session closed`() = runTest {
        val (fakes, manager) = build()
        val link = readySession(fakes.transport, manager)

        link.deliverNotification("{ not json")
        testScheduler.runCurrent()

        assertTrue(manager.linkPhase.value is LinkPhase.Failed)
        assertTrue(link.isClosed)
    }

    @Test
    fun `forget device clears identity and removes the bond`() = runTest {
        val (fakes, manager) = build()
        readySession(fakes.transport, manager)

        val removed = manager.forgetDevice()
        testScheduler.runCurrent()

        assertTrue(removed)
        assertNull(fakes.identityStore.identity.first())
        assertEquals(listOf("AA:BB"), fakes.transport.bondsRemoved)
        assertNull(manager.lastButtonEvent.value)
        assertEquals(LinkPhase.Idle, manager.linkPhase.value)
        assertNull(manager.device.value)
    }

    @Test
    fun `scan collects deduplicated devices and stops after its window`() = runTest {
        val (fakes, manager) = build()
        testScheduler.runCurrent()
        val events = mutableListOf<CompanionError>()
        backgroundScope.launch { manager.errorEvents.collect { events.add(it) } }
        testScheduler.runCurrent()

        manager.startScan()
        testScheduler.runCurrent()
        fakes.scanner.emit(ScannedDevice("AA:BB", "CrossPoint Pal"))
        fakes.scanner.emit(ScannedDevice("AA:BB", null)) // duplicate, ignored
        fakes.scanner.emit(ScannedDevice("CC:DD", null))
        testScheduler.runCurrent()

        assertEquals(
            listOf(
                ScannedDevice("AA:BB", "CrossPoint Pal"),
                ScannedDevice("CC:DD", null),
            ),
            manager.scannedDevices.value,
        )
        assertTrue(manager.isScanning.value)

        advanceTimeBy(12_001)
        testScheduler.runCurrent()
        assertFalse(manager.isScanning.value)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `scan without permissions reports the missing permission`() = runTest {
        val manager = managerWithFakes(environment = FakeBleEnvironment(missing = listOf("p")))
        testScheduler.runCurrent()
        val events = mutableListOf<CompanionError>()
        backgroundScope.launch { manager.errorEvents.collect { events.add(it) } }
        testScheduler.runCurrent()

        manager.startScan()
        testScheduler.runCurrent()

        assertEquals(listOf<CompanionError>(CompanionError.PermissionsMissing), events)
        assertFalse(manager.isScanning.value)
    }
}
