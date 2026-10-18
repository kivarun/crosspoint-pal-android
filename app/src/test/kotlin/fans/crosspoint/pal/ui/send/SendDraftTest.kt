package fans.crosspoint.pal.ui.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendDraftTest {

    @Test
    fun `empty draft is not sendable`() {
        val draft = SendDraft()
        assertFalse(draft.isSendable)
    }

    @Test
    fun `blank-only draft is not sendable`() {
        val draft = SendDraft(title = "   ", body = "  \n ")
        assertFalse(draft.isSendable)
    }

    @Test
    fun `draft with title and body is sendable`() {
        val draft = SendDraft(title = "Note", body = "Content")
        assertTrue(draft.isSendable)
    }

    @Test
    fun `title input is truncated to the demo bound`() {
        val long = "x".repeat(SendDraft.TITLE_MAX_LENGTH + 10)
        assertEquals(SendDraft.TITLE_MAX_LENGTH, draftWith(long).titleCount)
    }

    @Test
    fun `body input is truncated to the demo bound`() {
        val long = "y".repeat(SendDraft.BODY_MAX_LENGTH + 10)
        assertEquals(SendDraft.BODY_MAX_LENGTH, draftBody(long).bodyCount)
    }

    @Test
    fun `cleared returns an empty draft`() {
        val cleared = SendDraft(title = "Note", body = "Content").cleared()
        assertEquals("", cleared.title)
        assertEquals("", cleared.body)
        assertFalse(cleared.isSendable)
    }

    @Test
    fun `counts track input lengths`() {
        val draft = SendDraft(title = "abc", body = "de")
        assertEquals(3, draft.titleCount)
        assertEquals(2, draft.bodyCount)
    }

    private fun draftWith(title: String): SendDraft = SendDraft().withTitle(title)
    private fun draftBody(body: String): SendDraft = SendDraft().withBody(body)
}
