package fans.crosspoint.pal.ble

/** Detailed phase of the GATT link and session, owned by the connection manager. */
sealed interface LinkPhase {
    /** No session attempt is running; a remembered device may exist. */
    data object Idle : LinkPhase

    /** Establishing the system bond (pairing dialog may be visible). */
    data object Bonding : LinkPhase

    /** GATT connection in progress. */
    data object Connecting : LinkPhase

    /** MTU negotiation towards [CompanionProtocol.REQUESTED_MTU]. */
    data object NegotiatingMtu : LinkPhase

    /** Service and characteristic discovery. */
    data object Discovering : LinkPhase

    /** Subscribing to Event notifications via the CCCD. */
    data object Subscribing : LinkPhase

    /** `hello` sent; waiting for `hello_ack`. */
    data object Handshaking : LinkPhase

    /** `hello_ack` accepted; content sends and button events are live. */
    data object Ready : LinkPhase

    /** The last attempt or session ended in a failure. */
    data class Failed(val error: CompanionError) : LinkPhase
}

/** Typed companion failures. UI layers translate these into messages. */
sealed interface CompanionError {
    data object BluetoothOff : CompanionError
    data object PermissionsMissing : CompanionError
    data object ScanFailed : CompanionError

    /** Bonding did not complete (user cancelled or stack failure). */
    data object PairingFailed : CompanionError

    data class ConnectionFailed(val gattStatus: Int) : CompanionError

    /** The negotiated MTU cannot carry a complete control message. */
    data class InsufficientMtu(val negotiated: Int) : CompanionError

    /** Discovered GATT layout does not match the companion contract. */
    data object IncompatibleGatt : CompanionError

    /** The reader rejected the phone's protocol version. */
    data object IncompatibleVersion : CompanionError

    /** No paired companion identity (or its system bond) is available. */
    data object NotPaired : CompanionError

    data class SetupTimeout(val phase: LinkPhase) : CompanionError

    data class SendRejected(val code: String, val message: String) : CompanionError
    data object SendTimeout : CompanionError

    /** A message violated the session contract (malformed, out of sequence). */
    data class ProtocolViolation(val detail: String) : CompanionError

    /** The link dropped unexpectedly. */
    data object LinkLost : CompanionError
}

/** Result of a content send attempt. */
sealed interface SendResult {
    /** The reader acknowledged the exact contentId. */
    data object Sent : SendResult

    /** No ready session to send on. */
    data object NotReady : SendResult

    data class Failed(val error: CompanionError) : SendResult
}

/** A device found by the bounded scan. */
data class ScannedDevice(
    val address: String,
    val name: String?,
)

/**
 * State of the bounded diagnostic wait for the paired reader's next wake
 * advertisement. One wait scans one bounded window, connects at most once,
 * and never loops or retries.
 */
sealed interface AwaitWakeState {
    /** No wait is running; the previous outcome is retained until the next wait. */
    data object Idle : AwaitWakeState

    /** The bounded scan window is open, waiting for the paired reader. */
    data object Waiting : AwaitWakeState

    /** The window elapsed without the paired reader advertising. */
    data object TimedOut : AwaitWakeState

    /** The paired reader appeared and the session reached Ready. */
    data object Connected : AwaitWakeState

    /** The wait ended in a typed failure before or during the session. */
    data class Failed(val error: CompanionError) : AwaitWakeState
}
