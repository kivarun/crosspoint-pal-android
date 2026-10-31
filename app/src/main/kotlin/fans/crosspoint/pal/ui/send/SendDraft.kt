package fans.crosspoint.pal.ui.send

import fans.crosspoint.pal.ble.CompanionCodec
import fans.crosspoint.pal.ble.CompanionProtocol

/**
 * Immutable send form state with protocol-derived bounds.
 *
 * The bounds are the companion protocol v1 field caps in UTF-8 bytes; the
 * phone truncates at code-point boundaries. The final combined message bound
 * (512 bytes including JSON overhead and escaping) is enforced again by the
 * codec at send time.
 */
data class SendDraft(
    val title: String = "",
    val body: String = "",
) {
    val titleCount: Int
        get() = title.toByteArray(Charsets.UTF_8).size

    val bodyCount: Int
        get() = body.toByteArray(Charsets.UTF_8).size

    val isSendable: Boolean
        get() = title.isNotBlank() && body.isNotBlank()

    fun withTitle(title: String): SendDraft =
        copy(title = CompanionCodec.truncateToBytes(title, TITLE_MAX_BYTES))

    fun withBody(body: String): SendDraft =
        copy(body = CompanionCodec.truncateToBytes(body, BODY_MAX_BYTES))

    fun cleared(): SendDraft = SendDraft()

    companion object {
        const val TITLE_MAX_BYTES = CompanionProtocol.MAX_TITLE_BYTES
        const val BODY_MAX_BYTES = CompanionProtocol.MAX_BODY_BYTES
    }
}
