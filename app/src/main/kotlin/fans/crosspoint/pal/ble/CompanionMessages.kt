package fans.crosspoint.pal.ble

/** A message received on the Event characteristic. */
sealed interface DeviceMessage {
    data class HelloAck(val version: Int) : DeviceMessage

    data class ContentAck(val contentId: String) : DeviceMessage

    data class Button(val button: String, val pressed: Boolean) : DeviceMessage

    data class Error(val code: String, val message: String) : DeviceMessage

    /** Well-formed JSON that is not a v1 message. The session must fail closed. */
    data object Unknown : DeviceMessage
}
