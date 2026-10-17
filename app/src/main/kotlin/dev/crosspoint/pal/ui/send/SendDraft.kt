package dev.crosspoint.pal.ui.send

/**
 * Immutable send form state with Phase 0 presentation bounds.
 *
 * The bounds are demo-level presentation limits for a small e-ink display;
 * wire-level message bounds will be defined by the companion protocol when
 * the BLE phase lands and will replace these values.
 */
data class SendDraft(
    val title: String = "",
    val body: String = "",
) {
    val titleCount: Int
        get() = title.length

    val bodyCount: Int
        get() = body.length

    val isSendable: Boolean
        get() = title.isNotBlank() && body.isNotBlank()

    fun withTitle(title: String): SendDraft =
        copy(title = title.take(TITLE_MAX_LENGTH))

    fun withBody(body: String): SendDraft =
        copy(body = body.take(BODY_MAX_LENGTH))

    fun cleared(): SendDraft = SendDraft()

    companion object {
        const val TITLE_MAX_LENGTH = 48
        const val BODY_MAX_LENGTH = 400
    }
}
