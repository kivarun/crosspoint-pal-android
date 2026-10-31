package fans.crosspoint.pal.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Platform-facing checks the connect workflow needs before any radio work:
 * Bluetooth power, BLE permissions for the running Android version, and bond
 * state. The UI drives the runtime permission request from the same list.
 */
interface BleEnvironment {

    /** True when the local Bluetooth adapter is present and powered on. */
    val isBluetoothEnabled: Boolean

    /** Permissions the current Android version requires for the BLE workflow. */
    val requiredPermissions: List<String>

    /** Permissions from [requiredPermissions] that the app does not hold. */
    fun missingPermissions(): List<String>

    /** True when the system still holds a bond for the address. */
    fun isBonded(address: String): Boolean
}

class AndroidBleEnvironment(private val context: Context) : BleEnvironment {

    override val isBluetoothEnabled: Boolean
        get() = adapter?.isEnabled == true

    override val requiredPermissions: List<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            // Scanning on Android 11 and below requires location access.
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    override fun missingPermissions(): List<String> =
        requiredPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

    @android.annotation.SuppressLint("MissingPermission") // guarded by missingPermissions()
    override fun isBonded(address: String): Boolean =
        runCatching {
            adapter?.getRemoteDevice(address)?.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED
        }.getOrDefault(false)

    private val adapter: android.bluetooth.BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter
}
