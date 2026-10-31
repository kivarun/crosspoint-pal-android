package fans.crosspoint.pal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import java.util.Locale
import fans.crosspoint.pal.R
import fans.crosspoint.pal.ble.CompanionError

private const val STRING_PLACEHOLDER = "%1\$s"

/**
 * User-facing messages for typed companion failures. The mapping is created
 * once in composable context (configuration-aware) and can then be applied
 * to events as they arrive (snackbars, inline text); the error types
 * themselves are the contract.
 */
@Composable
fun rememberCompanionErrorMessages(): (CompanionError) -> String {
    val bluetoothOff = stringResource(R.string.error_bluetooth_off)
    val permissionsMissing = stringResource(R.string.error_permissions_missing)
    val scanFailed = stringResource(R.string.error_scan_failed)
    val pairingFailed = stringResource(R.string.error_pairing_failed)
    val connectionFailedPattern = stringResource(R.string.error_connection_failed, STRING_PLACEHOLDER)
    val insufficientMtuPattern = stringResource(R.string.error_insufficient_mtu, STRING_PLACEHOLDER)
    val incompatibleGatt = stringResource(R.string.error_incompatible_gatt)
    val incompatibleVersion = stringResource(R.string.error_incompatible_version)
    val setupTimeout = stringResource(R.string.error_setup_timeout)
    val sendRejectedPattern = stringResource(R.string.error_send_rejected, STRING_PLACEHOLDER)
    val sendTimeout = stringResource(R.string.error_send_timeout)
    val protocolViolation = stringResource(R.string.error_protocol_violation)
    val linkLost = stringResource(R.string.error_link_lost)
    return remember(
        bluetoothOff, permissionsMissing, scanFailed, pairingFailed, connectionFailedPattern,
        insufficientMtuPattern, incompatibleGatt, incompatibleVersion, setupTimeout,
        sendRejectedPattern, sendTimeout, protocolViolation, linkLost,
    ) {
        { error: CompanionError ->
        when (error) {
            CompanionError.BluetoothOff -> bluetoothOff
            CompanionError.PermissionsMissing -> permissionsMissing
            CompanionError.ScanFailed -> scanFailed
            CompanionError.PairingFailed -> pairingFailed
            is CompanionError.ConnectionFailed ->
                formatPattern(connectionFailedPattern, error.gattStatus.toString())
            is CompanionError.InsufficientMtu ->
                formatPattern(insufficientMtuPattern, error.negotiated.toString())
            CompanionError.IncompatibleGatt -> incompatibleGatt
            CompanionError.IncompatibleVersion -> incompatibleVersion
            is CompanionError.SetupTimeout -> setupTimeout
            is CompanionError.SendRejected ->
                formatPattern(sendRejectedPattern, error.code)
            CompanionError.SendTimeout -> sendTimeout
            is CompanionError.ProtocolViolation -> protocolViolation
            CompanionError.LinkLost -> linkLost
        }
        }
    }
}

private fun formatPattern(pattern: String, value: Any): String =
    java.lang.String.format(Locale.getDefault(), pattern, value)
