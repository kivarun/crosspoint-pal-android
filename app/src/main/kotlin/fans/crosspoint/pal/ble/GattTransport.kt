package fans.crosspoint.pal.ble

import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * The Android-side GATT surface the connection manager drives.
 *
 * This seam keeps the session logic testable without a Bluetooth stack: the
 * production implementation wraps [android.bluetooth.BluetoothGatt] and its
 * callbacks; tests script it. Implementations must fail pending operations
 * with [LinkLostException] when the link drops or is closed, and must never
 * silently retry an operation.
 */
interface GattTransport {

    /**
     * Ensures the peer is bonded, running the system pairing flow when
     * needed. Returns false when bonding failed or was cancelled.
     */
    suspend fun ensureBonded(address: String): Boolean

    /** Opens a fresh GATT connection and suspends until it is established. */
    suspend fun connect(address: String): GattLink

    /** True when the system still holds a bond for the address. */
    fun isBonded(address: String): Boolean

    /** Removes the system bond where the platform permits it. */
    suspend fun removeBond(address: String): Boolean

    /** Bond state changes for remote devices (addresses). */
    val bondEvents: Flow<String>
}

/** An established, ready-to-drive GATT connection to the companion. */
interface GattLink {

    /** Negotiates the MTU and returns the value the stack accepted. */
    suspend fun requestMtu(mtu: Int): Int

    /** Discovers services; returns the characteristic UUIDs found under the companion service. */
    suspend fun discoverServices(): DiscoveredCharacteristics

    /** Enables notifications on the Event characteristic via its CCCD. */
    suspend fun subscribeEvent(characteristic: UUID)

    /** Writes one complete control message with response. */
    suspend fun writeControl(characteristic: UUID, payload: ByteArray)

    /** Invoked for every Event notification payload. */
    var onNotification: ((ByteArray) -> Unit)?

    /** Invoked exactly once when the link drops or is closed. */
    var onClosed: (() -> Unit)?

    /** Tears the link down; idempotent. */
    fun close()
}

/** Characteristics of the companion service discovered on the reader. */
data class DiscoveredCharacteristics(
    val controlUuid: UUID,
    val eventUuid: UUID,
)

/** The GATT link dropped while an operation or the session was in flight. */
class LinkLostException : Exception()
