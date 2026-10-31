package fans.crosspoint.pal.ble

import android.Manifest
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothProfile
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.BluetoothGattCharacteristicBuilder

/**
 * Regression tests for GATT handle ownership in the real transport.
 *
 * Losing the handle returned by connectGatt produced a false
 * [LinkLostException] right after a successful connect and leaked every
 * registered GATT client (observed on a real device: the reader stayed
 * "Connected to phone" while the app reported a lost connection).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BluetoothGattTransportTest {

    private val address = "00:11:22:33:44:55"

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.BLUETOOTH_CONNECT)
    }

    private fun device(): BluetoothDevice =
        BluetoothAdapter.getDefaultAdapter().getRemoteDevice(address)

    /** Drives connectGatt to its onConnectionStateChange(STATE_CONNECTED) callback. */
    private fun connectUntilConnected(transport: BluetoothGattTransport): GattLink =
        runBlocking(Dispatchers.Unconfined) {
            val connected = CompletableDeferred<GattLink>()
            launch {
                try {
                    connected.complete(transport.connect(address))
                } catch (failure: Throwable) {
                    connected.completeExceptionally(failure)
                }
            }
            yield() // let the connect attempt reach its wait for the callback
            shadowOf(device()).simulateGattConnectionChange(
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_CONNECTED,
            )
            connected.await()
        }

    private fun lastGatt(): BluetoothGatt = shadowOf(device()).getBluetoothGatts().last()

    private fun companionService(): BluetoothGattService {
        val service = BluetoothGattService(
            UUID.fromString(CompanionProtocol.SERVICE_UUID),
            BluetoothGattService.SERVICE_TYPE_PRIMARY,
        )
        val control = BluetoothGattCharacteristicBuilder.newBuilder()
            .setUuid(UUID.fromString(CompanionProtocol.CONTROL_UUID))
            .setProperties(BluetoothGattCharacteristic.PROPERTY_WRITE)
            .setPermissions(BluetoothGattCharacteristic.PERMISSION_WRITE)
            .build()
        val event = BluetoothGattCharacteristicBuilder.newBuilder()
            .setUuid(UUID.fromString(CompanionProtocol.EVENT_UUID))
            .setProperties(BluetoothGattCharacteristic.PROPERTY_NOTIFY)
            .setPermissions(BluetoothGattCharacteristic.PERMISSION_READ)
            .build()
        event.addDescriptor(
            BluetoothGattDescriptor(
                CCCD,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )
        service.addCharacteristic(control)
        service.addCharacteristic(event)
        return service
    }

    @Test
    fun `after connectGatt the next GATT request does not get a false LinkLost`() {
        val transport = BluetoothGattTransport(ApplicationProvider.getApplicationContext())
        val link = connectUntilConnected(transport)

        // requestMtu completes through the real onMtuChanged callback path.
        runBlocking {
            assertEquals(CompanionProtocol.REQUESTED_MTU, link.requestMtu(CompanionProtocol.REQUESTED_MTU))
        }
    }

    @Test
    fun `a full setup walks discovery and the cccd write over the same gatt instance`() {
        val transport = BluetoothGattTransport(ApplicationProvider.getApplicationContext())
        val link = connectUntilConnected(transport)
        val gatt = lastGatt()
        shadowOf(gatt).addDiscoverableService(companionService())

        runBlocking {
            assertEquals(CompanionProtocol.REQUESTED_MTU, link.requestMtu(CompanionProtocol.REQUESTED_MTU))
            val discovered = link.discoverServices()
            assertEquals(UUID.fromString(CompanionProtocol.CONTROL_UUID), discovered.controlUuid)
            assertEquals(UUID.fromString(CompanionProtocol.EVENT_UUID), discovered.eventUuid)
            gatt.getService(UUID.fromString(CompanionProtocol.SERVICE_UUID))
                ?.getCharacteristic(UUID.fromString(CompanionProtocol.EVENT_UUID))
                ?.let { shadowOf(gatt).allowCharacteristicNotification(it) }
            link.subscribeEvent(UUID.fromString(CompanionProtocol.EVENT_UUID))
        }

        // The CCCD write reached the stack: value 0x01-00 enables notifications.
        assertArrayEquals(byteArrayOf(0x01, 0x00), shadowOf(gatt).getLatestWrittenBytes())
    }

    @Test
    fun `a disconnect after setup tears the client down and fails later operations`() {
        val transport = BluetoothGattTransport(ApplicationProvider.getApplicationContext())
        val link = connectUntilConnected(transport)
        val gatt = lastGatt()
        val closed = CompletableDeferred<Unit>()
        link.onClosed = { closed.complete(Unit) }

        shadowOf(device()).simulateGattConnectionChange(
            19, // HCI error 0x13: remote user terminated connection
            BluetoothProfile.STATE_DISCONNECTED,
        )

        assertTrue(shadowOf(gatt).isClosed())
        assertTrue(closed.isCompleted)
        try {
            runBlocking { link.requestMtu(CompanionProtocol.REQUESTED_MTU) }
            fail("operations on a closed link must fail")
        } catch (expected: LinkLostException) {
            // expected
        }
    }

    @Test
    fun `a repeat attempt closes the old client and opens a fresh one`() {
        val transport = BluetoothGattTransport(ApplicationProvider.getApplicationContext())

        val first = connectUntilConnected(transport)
        val firstGatt = lastGatt()
        first.close()
        assertTrue(shadowOf(firstGatt).isClosed())

        val second = connectUntilConnected(transport)
        val gatts = shadowOf(device()).getBluetoothGatts()
        assertEquals(2, gatts.size)
        assertTrue(shadowOf(gatts[0]).isClosed())
        assertNotEquals(gatts[0], gatts[1])
        runBlocking {
            assertEquals(CompanionProtocol.REQUESTED_MTU, second.requestMtu(CompanionProtocol.REQUESTED_MTU))
        }
    }

    @Test
    fun `an early disconnect inside connectGatt fails closed and still closes the client`() {
        val transport = BluetoothGattTransport(ApplicationProvider.getApplicationContext())
        val device = device()
        shadowOf(device).setGattConnectionInterceptor { gatt ->
            // Delivered on the registration path, before connectGatt returns:
            // the model AOSP uses when no Handler is passed (binder-thread
            // callback dispatch).
            shadowOf(gatt).getGattCallback()?.onConnectionStateChange(
                gatt,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_DISCONNECTED,
            )
        }
        try {
            runBlocking {
                try {
                    transport.connect(address)
                    fail("an early disconnect must fail the connect")
                } catch (expected: LinkLostException) {
                    // expected
                }
            }
            // The just-created client must not stay registered on the stack.
            val gatts = shadowOf(device).getBluetoothGatts()
            assertEquals(1, gatts.size)
            assertTrue(shadowOf(gatts[0]).isClosed())
        } finally {
            shadowOf(device).setGattConnectionInterceptor(null)
        }
    }

    @Test
    fun `an early connect callback inside connectGatt does not break the session`() {
        val transport = BluetoothGattTransport(ApplicationProvider.getApplicationContext())
        val device = device()
        shadowOf(device).setGattConnectionInterceptor { gatt ->
            shadowOf(gatt).getGattCallback()?.onConnectionStateChange(
                gatt,
                BluetoothGatt.GATT_SUCCESS,
                BluetoothProfile.STATE_CONNECTED,
            )
        }
        try {
            runBlocking {
                val link = transport.connect(address)
                assertEquals(CompanionProtocol.REQUESTED_MTU, link.requestMtu(CompanionProtocol.REQUESTED_MTU))
            }
        } finally {
            shadowOf(device).setGattConnectionInterceptor(null)
        }
    }

    @Test
    fun `cancelling the connect wait closes the just created gatt client`() {
        val transport = BluetoothGattTransport(ApplicationProvider.getApplicationContext())
        runBlocking {
            val connectJob = launch(Dispatchers.Unconfined) {
                transport.connect(address) // suspends awaiting the stack callback
            }
            yield() // let the attempt reach connectGatt and its wait
            connectJob.cancel()
            connectJob.join()
            assertTrue(connectJob.isCancelled)
        }
        // The manager never received the link, so the handle must be released
        // by the transport itself, not by a later manager cleanup.
        val gatts = shadowOf(device()).getBluetoothGatts()
        assertEquals(1, gatts.size)
        assertTrue(shadowOf(gatts[0]).isClosed())
    }

    private companion object {
        val CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
