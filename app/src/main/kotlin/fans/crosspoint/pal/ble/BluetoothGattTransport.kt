package fans.crosspoint.pal.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Parcelable
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Production [GattTransport] over [android.bluetooth].
 *
 * GATT operations are strict one-shot requests: each suspends until its own
 * callback arrives and fails with [LinkLostException] on disconnect, so the
 * session layer never sees a stale or half-finished operation. The bond
 * workflow uses the platform pairing dialog and never falls back to an
 * unencrypted link.
 */
class BluetoothGattTransport(private val context: Context) : GattTransport {

    private val adapter: android.bluetooth.BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    private val bondEventFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)

    override val bondEvents: Flow<String> = bondEventFlow.asSharedFlow()

    private val bondReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
            val device = intent.parcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val state = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
            if (state == BluetoothDevice.BOND_BONDED) {
                bondEventFlow.tryEmit(device.address)
            }
        }
    }

    init {
        context.registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
    }

    @SuppressLint("MissingPermission") // callers guard BLUETOOTH_CONNECT via BleEnvironment
    override fun isBonded(address: String): Boolean =
        runCatching { adapter?.getRemoteDevice(address)?.bondState == BluetoothDevice.BOND_BONDED }
            .getOrDefault(false)

    @SuppressLint("MissingPermission") // callers guard BLUETOOTH_CONNECT via BleEnvironment
    override suspend fun ensureBonded(address: String): Boolean {
        val device = remoteDevice(address) ?: return false
        return when (device.bondState) {
            BluetoothDevice.BOND_BONDED -> true
            BluetoothDevice.BOND_BONDING -> awaitBond(device)
            else -> device.createBond() && awaitBond(device)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun removeBond(address: String): Boolean {
        val device = remoteDevice(address) ?: return false
        if (device.bondState != BluetoothDevice.BOND_BONDED) return true
        // removeBond() is hidden API: the only way to drop a bond without
        // sending the user to system Settings. Vendors may not honour it;
        // the caller must treat false as "ask the user to forget the device
        // in the system Bluetooth settings".
        return runCatching {
            device.javaClass.getMethod("removeBond").invoke(device) as? Boolean ?: false
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(address: String): GattLink {
        val device = remoteDevice(address) ?: throw LinkLostException()
        val link = GattLinkImpl(device)
        link.open(context)
        return link
    }

    private fun remoteDevice(address: String): BluetoothDevice? = runCatching {
        adapter?.getRemoteDevice(address)
    }.getOrNull()

    private suspend fun awaitBond(device: BluetoothDevice): Boolean {
        val settled = CompletableDeferred<Boolean>()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != BluetoothDevice.ACTION_BOND_STATE_CHANGED) return
                val changed = intent.parcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (changed?.address != device.address) return
                when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                    BluetoothDevice.BOND_BONDED -> settled.complete(true)
                    BluetoothDevice.BOND_NONE -> settled.complete(false)
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))
        try {
            // The passkey dialog may wait on the user for a long time; this
            // is an explicit user workflow, not an unbounded retry loop.
            return withTimeoutOrNull(BOND_TIMEOUT_MS) { settled.await() } ?: false
        } finally {
            context.unregisterReceiver(receiver)
        }
    }

    private companion object {
        const val BOND_TIMEOUT_MS = 60_000L
    }
}

/** A GATT operation finished with a non-success ATT status. */
class GattOperationException(val gattStatus: Int) : Exception("GATT status $gattStatus")

/** The discovered GATT layout does not match the companion contract. */
class IncompatibleGattException : Exception()

/**
 * One GATT connection and its serialized operations. Callbacks arrive on
 * binder threads; every operation completes through a deferred that the close
 * path fails, so a disconnect can never leave an operation hanging.
 */
@SuppressLint("MissingPermission")
private class GattLinkImpl(
    private val device: BluetoothDevice,
) : BluetoothGattCallback(), GattLink {

    @Volatile
    private var gatt: BluetoothGatt? = null
    private val pending = AtomicReference<CompletableDeferred<GattEvent>?>(null)
    private val closed = AtomicReference(false)

    override var onNotification: ((ByteArray) -> Unit)? = null

    @Volatile
    override var onClosed: (() -> Unit)? = null

    suspend fun open(context: Context) {
        val deferred = CompletableDeferred<GattEvent>()
        check(pending.compareAndSet(null, deferred)) { "link already has an operation in flight" }
        // With the four-argument connectGatt overload no Handler is passed, so
        // BluetoothGatt keeps mHandler == null and delivers callbacks
        // synchronously on its binder thread (runOrQueueCallback). An early
        // onConnectionStateChange can therefore run before the assignment
        // below. Session state stays correct because `pending` is armed before
        // the call; teardown of the just-created handle in that window is the
        // post-assignment `closed` check.
        gatt = device.connectGatt(context, false, this, BluetoothDevice.TRANSPORT_LE)
        if (gatt == null) {
            deferred.complete(GattEvent.Status(BluetoothGatt.GATT_FAILURE))
        } else if (closed.get()) {
            // A callback closed the link before the handle was assigned.
            closeGatt()
        }
        when (val event = deferred.await()) {
            is GattEvent.Connected -> Unit
            is GattEvent.Status -> {
                close()
                throw GattOperationException(event.status)
            }
            is GattEvent.Disconnected -> {
                close()
                throw LinkLostException()
            }
            is GattEvent.Mtu -> {
                close()
                throw IllegalStateException("Unexpected GATT event while connecting")
            }
        }
    }

    override suspend fun requestMtu(mtu: Int): Int {
        val event = awaitOperation { gatt -> gatt.requestMtu(mtu) }
        return when (event) {
            is GattEvent.Mtu ->
                if (event.status == BluetoothGatt.GATT_SUCCESS) {
                    event.mtu
                } else {
                    throw GattOperationException(event.status)
                }
            is GattEvent.Status -> throw GattOperationException(event.status)
            is GattEvent.Disconnected -> throw LinkLostException()
            is GattEvent.Connected -> unexpected(event)
        }
    }

    override suspend fun discoverServices(): DiscoveredCharacteristics {
        val event = awaitOperation { gatt -> gatt.discoverServices() }
        if (event is GattEvent.Status && event.status != BluetoothGatt.GATT_SUCCESS) {
            throw GattOperationException(event.status)
        }
        if (event !is GattEvent.Status) unexpected(event)

        val service = gatt?.getService(UUID.fromString(CompanionProtocol.SERVICE_UUID))
            ?: throw GattOperationException(BluetoothGatt.GATT_FAILURE)
        service.getCharacteristic(UUID.fromString(CompanionProtocol.CONTROL_UUID))
            ?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 }
            ?: throw IncompatibleGattException()
        service.getCharacteristic(UUID.fromString(CompanionProtocol.EVENT_UUID))
            ?.takeIf { it.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 }
            ?: throw IncompatibleGattException()
        return DiscoveredCharacteristics(
            controlUuid = UUID.fromString(CompanionProtocol.CONTROL_UUID),
            eventUuid = UUID.fromString(CompanionProtocol.EVENT_UUID),
        )
    }

    override suspend fun subscribeEvent(characteristic: UUID) {
        val event = awaitOperation { gatt ->
            val characteristicRef = gatt.getService(UUID.fromString(CompanionProtocol.SERVICE_UUID))
                ?.getCharacteristic(characteristic) ?: return@awaitOperation false
            if (!gatt.setCharacteristicNotification(characteristicRef, true)) return@awaitOperation false
            val descriptor = characteristicRef.getDescriptor(CLIENT_CONFIGURATION) ?: return@awaitOperation false
            writeDescriptorSafe(gatt, descriptor, ENABLE_NOTIFICATION_VALUE)
        }
        when (event) {
            is GattEvent.Status -> if (event.status != BluetoothGatt.GATT_SUCCESS) {
                throw GattOperationException(event.status)
            }
            is GattEvent.Disconnected -> throw LinkLostException()
            else -> unexpected(event)
        }
    }

    override suspend fun writeControl(characteristic: UUID, payload: ByteArray) {
        val event = awaitOperation { gatt ->
            val characteristicRef = gatt.getService(UUID.fromString(CompanionProtocol.SERVICE_UUID))
                ?.getCharacteristic(characteristic) ?: return@awaitOperation false
            writeCharacteristicSafe(gatt, characteristicRef, payload)
        }
        when (event) {
            is GattEvent.Status -> if (event.status != BluetoothGatt.GATT_SUCCESS) {
                throw GattOperationException(event.status)
            }
            is GattEvent.Disconnected -> throw LinkLostException()
            else -> unexpected(event)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pending.getAndSet(null)?.complete(GattEvent.Disconnected)
        val callback = onClosed
        onClosed = null
        callback?.invoke()
        closeGatt()
    }

    /** Tears the GATT handle down exactly once; shared by close and the early-callback path. */
    private fun closeGatt() {
        val current = gatt
        gatt = null
        current?.let { handle ->
            runCatching { handle.disconnect() }
            runCatching { handle.close() }
        }
    }

    // --- Callbacks (binder thread) ---

    override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
        if (newState == BluetoothProfile.STATE_CONNECTED) {
            complete(GattEvent.Connected)
        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            close()
        }
    }

    override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
        complete(GattEvent.Mtu(mtu, status))
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        complete(GattEvent.Status(status))
    }

    override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
        complete(GattEvent.Status(status))
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
        complete(GattEvent.Status(status))
    }

    @Deprecated("Deprecated in Java")
    override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        @Suppress("DEPRECATION")
        onNotification?.invoke(characteristic.value ?: return)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ) {
        onNotification?.invoke(value)
    }

    // --- Internals ---

    private suspend fun awaitOperation(issue: (BluetoothGatt) -> Boolean): GattEvent {
        val deferred = CompletableDeferred<GattEvent>()
        val gatt = this.gatt ?: throw LinkLostException()
        if (!pending.compareAndSet(null, deferred)) throw LinkLostException()
        try {
            if (!issue(gatt)) {
                // The stack refused to queue the operation.
                pending.compareAndSet(deferred, null)
                throw GattOperationException(BluetoothGatt.GATT_FAILURE)
            }
        } catch (issueError: Exception) {
            pending.compareAndSet(deferred, null)
            throw issueError
        }
        try {
            return deferred.await()
        } catch (cancelled: CancellationException) {
            pending.compareAndSet(deferred, null)
            throw cancelled
        }
    }

    private fun complete(event: GattEvent) {
        pending.getAndSet(null)?.complete(event)
    }

    private fun unexpected(event: GattEvent): Nothing =
        throw IllegalStateException("Unexpected GATT event: $event")

    private fun writeDescriptorSafe(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun writeCharacteristicSafe(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(characteristic)
        }
    }

    private sealed interface GattEvent {
        data object Connected : GattEvent
        data class Mtu(val mtu: Int, val status: Int) : GattEvent
        data class Status(val status: Int) : GattEvent
        data object Disconnected : GattEvent
    }

    companion object {
        private val CLIENT_CONFIGURATION = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val ENABLE_NOTIFICATION_VALUE = byteArrayOf(0x01, 0x00)
    }
}

private inline fun <reified T : Parcelable> Intent.parcelableExtra(name: String): T? = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> getParcelableExtra(name, T::class.java)
    else -> @Suppress("DEPRECATION") getParcelableExtra(name) as? T
}
