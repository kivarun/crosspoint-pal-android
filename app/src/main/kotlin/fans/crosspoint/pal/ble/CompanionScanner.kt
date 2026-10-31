package fans.crosspoint.pal.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Bounded scan for the companion's advertised service UUID.
 *
 * The caller owns the scan window: it must call [startScan] at most once at a
 * time and always stop or time the scan out; Android throttles unbounded
 * scans aggressively.
 */
interface CompanionScanner {
    /** Emits every scan result matching the companion service UUID. */
    fun scanResults(): Flow<ScannedDevice>
}

class AndroidCompanionScanner(private val context: Context) : CompanionScanner {

    @SuppressLint("MissingPermission") // callers guard permissions via BleEnvironment
    override fun scanResults(): Flow<ScannedDevice> = callbackFlow {
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val scanner = adapter?.bluetoothLeScanner
        if (adapter == null || scanner == null || !adapter.isEnabled) {
            close(IllegalStateException("Bluetooth is not available"))
            return@callbackFlow
        }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(
                    ScannedDevice(
                        address = result.device.address,
                        name = result.scanRecord?.deviceName ?: result.device.name,
                    ),
                )
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Scan failed: $errorCode"))
            }
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid.fromString(CompanionProtocol.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .build()
        scanner.startScan(listOf(filter), settings, callback)
        awaitClose {
            runCatching { scanner.stopScan(callback) }
        }
    }
}
