package fans.crosspoint.pal.ble

/**
 * CrossPoint Pal companion protocol v1 constants.
 *
 * Canonical contract: firmware repository, `docs/ble-companion-protocol.md`
 * (kivarun/crosspoint-pal, feature/ble-companion-v0 @ 6a5cf3c). These values
 * are a shared product contract; neither side may change them alone.
 */
object CompanionProtocol {
    const val SERVICE_UUID = "7b1f1001-6f70-4f69-8f31-63726f737370"
    const val CONTROL_UUID = "7b1f1002-6f70-4f69-8f31-63726f737370"
    const val EVENT_UUID = "7b1f1003-6f70-4f69-8f31-63726f737370"

    const val VERSION = 1

    /** Both characteristics carry complete UTF-8 JSON objects up to this size. */
    const val MAX_MESSAGE_BYTES = 512

    /** Field caps in bytes. The phone truncates at UTF-8 code-point boundaries. */
    const val MAX_TITLE_BYTES = 64
    const val MAX_BODY_BYTES = 384

    /**
     * contentId is echoed back inside a JSON string, so its charset is
     * restricted to characters that need no JSON escaping.
     */
    const val MAX_CONTENT_ID_BYTES = 32

    /**
     * The phone must request this MTU before service discovery; the reader
     * sets the same value on its side. A complete 512-byte control message
     * only fits in one write when MTU >= [MAX_MESSAGE_BYTES] + 3.
     */
    const val REQUESTED_MTU = 517
    const val MIN_USABLE_MTU = MAX_MESSAGE_BYTES + 3

    /** A reader notification is at most MTU-3 bytes. */
    const val MAX_NOTIFICATION_BYTES = REQUESTED_MTU - 3

    /** Characters allowed in contentId: `A-Z a-z 0 9 . _ - :`. */
    val CONTENT_ID_CHARS = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('.', '_', '-', ':')

    /** Button names on the wire; power is never forwarded. */
    const val BUTTON_BACK = "back"
    const val BUTTON_CONFIRM = "confirm"
    const val BUTTON_LEFT = "left"
    const val BUTTON_RIGHT = "right"
    const val BUTTON_UP = "up"
    const val BUTTON_DOWN = "down"

    /** Reader error codes the phone understands. */
    const val ERROR_VERSION = "version"
    const val ERROR_HELLO_FIRST = "hello_first"
    const val ERROR_BAD_MESSAGE = "bad_message"
}
