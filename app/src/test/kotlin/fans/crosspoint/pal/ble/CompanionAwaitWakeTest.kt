package fans.crosspoint.pal.ble

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bounded await-wake diagnostic: a single scan window that auto-connects
 * only to the stored, bonded companion identity, stops the scan before any
 * GATT work, completes on Ready/timeout/failure, and never loops.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CompanionAwaitWakeTest {

    private class Fakes {
        val transport = FakeGattTransport()
        val environment = FakeBleEnvironment()
        val identityStore = InMemoryCompanionIdentityStore()
        val scanner = FakeScanner()
    }

    /** Builds a manager whose remembered companion is paired and bonded. */
    private suspend fun TestScope.buildPaired(fakes: Fakes = Fakes()): CompanionConnectionManager {
        val manager = managerWithFakes(
            transport = fakes.transport,
            environment = fakes.environment,
            identityStore = fakes.identityStore,
            scanner = fakes.scanner,
        )
        fakes.identityStore.setIdentity(CompanionIdentity("AA:BB", "CrossPoint Pal"))
        fakes.transport.bonded = true
        testScheduler.runCurrent()
        return manager
    }

    @Test
    fun `the paired reader's advertisement walks the session to ready`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        assertEquals(AwaitWakeState.Waiting, manager.awaitWake.value)

        // The reader wakes and advertises with its identity address.
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        testScheduler.runCurrent()

        // Exactly one session attempt to the bonded identity, and the scan
        // window is no longer open while the session runs.
        assertEquals(listOf("AA:BB"), fakes.transport.bondsRequested)
        assertEquals(1, fakes.scanner.scanStarts)
        assertEquals(0, fakes.scanner.activeScans)

        val link = fakes.transport.createdLinks.single()
        completeSetup(link)
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Ready, manager.linkPhase.value)
        assertEquals(AwaitWakeState.Connected, manager.awaitWake.value)
    }

    @Test
    fun `the scan is stopped before the session attempt starts`() = runTest {
        val fakes = Fakes()
        val timeline = mutableListOf<String>()
        val recordingTransport = object : GattTransport by fakes.transport {
            override suspend fun connect(address: String): GattLink {
                timeline.add("connect")
                return fakes.transport.connect(address)
            }
        }
        val manager = CompanionConnectionManager(
            scope = CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)),
            transport = recordingTransport,
            environment = fakes.environment,
            identityStore = fakes.identityStore,
            scanner = object : CompanionScanner {
                override fun scanResults(): Flow<ScannedDevice> =
                    fakes.scanner.scanResults().onCompletion { timeline.add("scanStopped") }
            },
        )
        fakes.identityStore.setIdentity(CompanionIdentity("AA:BB", "CrossPoint Pal"))
        fakes.transport.bonded = true
        testScheduler.runCurrent()

        manager.awaitNextWake()
        testScheduler.runCurrent()
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        testScheduler.runCurrent()

        assertEquals(listOf("scanStopped", "connect"), timeline)
    }

    @Test
    fun `a foreign advertisement with the same service uuid never connects`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        fakes.scanner.emit(ScannedDevice("CC:DD", "CrossPoint Pal"))
        fakes.scanner.emit(ScannedDevice("EE:FF", null))
        testScheduler.runCurrent()

        // The wait continues; no radio work beyond the scan happened.
        assertEquals(AwaitWakeState.Waiting, manager.awaitWake.value)
        assertTrue(fakes.transport.bondsRequested.isEmpty())
        assertTrue(fakes.transport.createdLinks.isEmpty())

        advanceTimeBy(60_001)
        testScheduler.runCurrent()
        assertEquals(AwaitWakeState.TimedOut, manager.awaitWake.value)
        assertEquals(1, fakes.scanner.scanStarts)
    }

    @Test
    fun `a repeated match opens exactly one session`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        fakes.scanner.emit(ScannedDevice("AA:BB", "CrossPoint Pal"))
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        testScheduler.runCurrent()

        assertEquals(1, fakes.transport.bondsRequested.size)
        assertEquals(1, fakes.transport.createdLinks.size)
        assertEquals(1, fakes.scanner.scanStarts)
    }

    @Test
    fun `the wait times out when the reader never appears`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        assertTrue(manager.awaitWake.value == AwaitWakeState.Waiting)

        advanceTimeBy(60_001)
        testScheduler.runCurrent()

        assertEquals(AwaitWakeState.TimedOut, manager.awaitWake.value)
        assertFalse(manager.isScanning.value)
        assertEquals(0, fakes.scanner.activeScans)
        assertTrue(fakes.transport.bondsRequested.isEmpty())
    }

    @Test
    fun `cancel stops the wait and later advertisements do not connect`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        manager.cancelAwaitWake()
        testScheduler.runCurrent()

        assertEquals(AwaitWakeState.Idle, manager.awaitWake.value)
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        testScheduler.runCurrent()
        advanceTimeBy(60_001)
        testScheduler.runCurrent()

        assertTrue(fakes.transport.bondsRequested.isEmpty())
        assertEquals(AwaitWakeState.Idle, manager.awaitWake.value)
    }

    @Test
    fun `cancel during the session attempt aborts it`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        testScheduler.runCurrent()
        val link = fakes.transport.createdLinks.single()

        manager.cancelAwaitWake()
        testScheduler.runCurrent()

        assertEquals(LinkPhase.Idle, manager.linkPhase.value)
        assertEquals(AwaitWakeState.Idle, manager.awaitWake.value)
        assertTrue(link.isClosed)
    }

    @Test
    fun `a session failure completes the wait with the error`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        testScheduler.runCurrent()
        val link = fakes.transport.createdLinks.single()

        link.replyMtu(247)
        testScheduler.runCurrent()

        assertEquals(
            LinkPhase.Failed(CompanionError.InsufficientMtu(247)),
            manager.linkPhase.value,
        )
        assertEquals(
            AwaitWakeState.Failed(CompanionError.InsufficientMtu(247)),
            manager.awaitWake.value,
        )
        assertTrue(link.isClosed)
    }

    @Test
    fun `await without a paired device reports not paired and never scans`() = runTest {
        val fakes = Fakes()
        val manager = managerWithFakes(
            transport = fakes.transport,
            environment = fakes.environment,
            identityStore = fakes.identityStore,
            scanner = fakes.scanner,
        )
        testScheduler.runCurrent()

        manager.awaitNextWake()
        testScheduler.runCurrent()

        assertEquals(AwaitWakeState.Failed(CompanionError.NotPaired), manager.awaitWake.value)
        assertEquals(0, fakes.scanner.scanStarts)
    }

    @Test
    fun `await with a remembered identity whose bond is gone reports not paired`() = runTest {
        val fakes = Fakes()
        // Identity remembered, but the system bond was dropped elsewhere.
        val manager = managerWithFakes(
            transport = fakes.transport,
            environment = fakes.environment,
            identityStore = fakes.identityStore,
            scanner = fakes.scanner,
        )
        fakes.identityStore.setIdentity(CompanionIdentity("AA:BB", "CrossPoint Pal"))
        testScheduler.runCurrent()

        manager.awaitNextWake()
        testScheduler.runCurrent()

        assertEquals(AwaitWakeState.Failed(CompanionError.NotPaired), manager.awaitWake.value)
        assertEquals(0, fakes.scanner.scanStarts)
        assertTrue(fakes.transport.bondsRequested.isEmpty())
    }

    @Test
    fun `a second await press while waiting is ignored`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        manager.awaitNextWake()
        testScheduler.runCurrent()

        assertEquals(1, fakes.scanner.scanStarts)
        assertEquals(AwaitWakeState.Waiting, manager.awaitWake.value)
    }

    @Test
    fun `the manual scan and reconnect are refused while the wait owns the radio`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        manager.startScan()
        manager.reconnect()
        testScheduler.runCurrent()

        assertFalse(manager.isScanning.value)
        assertEquals(1, fakes.scanner.scanStarts)
        assertTrue(fakes.transport.bondsRequested.isEmpty())
        assertEquals(AwaitWakeState.Waiting, manager.awaitWake.value)
    }

    @Test
    fun `forgetting the device ends the wait`() = runTest {
        val fakes = Fakes()
        val manager = buildPaired(fakes)

        manager.awaitNextWake()
        testScheduler.runCurrent()
        manager.forgetDevice()
        testScheduler.runCurrent()

        assertEquals(AwaitWakeState.Idle, manager.awaitWake.value)
        fakes.scanner.emit(ScannedDevice("AA:BB", null))
        testScheduler.runCurrent()
        assertTrue(fakes.transport.bondsRequested.isEmpty())
        assertNull(fakes.identityStore.identity.first())
    }
}
