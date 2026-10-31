package fans.crosspoint.pal.ble

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionCodecTest {

    @Test
    fun `hello is the exact contract message`() {
        assertArrayEquals("""{"op":"hello","version":1}""".toByteArray(), CompanionCodec.hello())
    }

    @Test
    fun `content encodes the contract shape`() {
        val encoded = CompanionCodec.content("Morning", "Quiet body", "c1")
        assertEquals(
            """{"op":"content","title":"Morning","body":"Quiet body","contentId":"c1"}""",
            encoded.toString(Charsets.UTF_8),
        )
        assertTrue(encoded.size <= CompanionProtocol.MAX_MESSAGE_BYTES)
    }

    @Test
    fun `content escapes quotes backslashes and control characters`() {
        val encoded = CompanionCodec.content("""a"b\c""", "line1\nline2\tend", "c1")
        // In the raw-string expectation every backslash is literal, exactly
        // as the JSON on the wire reads.
        assertEquals(
            """{"op":"content","title":"a\"b\\c","body":"line1\nline2\tend","contentId":"c1"}""",
            encoded.toString(Charsets.UTF_8),
        )
        assertTrue(encoded.size <= CompanionProtocol.MAX_MESSAGE_BYTES)
    }

    @Test
    fun `title truncates at a code point boundary`() {
        // 33 x two-byte code points = 66 bytes, one code point over the cap.
        val title = "ё".repeat(33)
        val truncated = CompanionCodec.truncateToBytes(title, CompanionProtocol.MAX_TITLE_BYTES)
        assertEquals(32, truncated.length)
        assertEquals(64, truncated.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `four byte code points truncate cleanly`() {
        val title = "𠀀".repeat(20) // 4 bytes each
        val truncated = CompanionCodec.truncateToBytes(title, 10)
        assertEquals(4, truncated.length)
        assertEquals(8, truncated.toByteArray(Charsets.UTF_8).size)
    }

    @Test
    fun `body truncates to the protocol bound`() {
        val body = "a".repeat(400)
        val encoded = CompanionCodec.content("t", body, "c1")
        val decodedText = encoded.toString(Charsets.UTF_8)
        assertTrue(decodedText.contains("\"body\":\"" + "a".repeat(384) + "\""))
        assertTrue(encoded.size <= CompanionProtocol.MAX_MESSAGE_BYTES)
    }

    @Test
    fun `a full length message stays within the control bound`() {
        val title = "т".repeat(32) // 64 bytes
        val body = "b".repeat(384)
        val id = "c" + "0".repeat(17) // 18 chars
        val encoded = CompanionCodec.content(title, body, id)
        assertTrue(encoded.size <= CompanionProtocol.MAX_MESSAGE_BYTES)
        assertTrue(CompanionCodec.isPlainId(id))
    }

    @Test
    fun `escaped bodies shrink to keep the message within the bound`() {
        val title = "t"
        val body = "\n".repeat(384) // every byte doubles when escaped
        val encoded = CompanionCodec.content(title, body, "c1")
        assertTrue(encoded.size <= CompanionProtocol.MAX_MESSAGE_BYTES)
    }

    @Test
    fun `contentId outside the charset is rejected`() {
        val rejected = listOf("c id", "c/id", "", "c\"id", "c".repeat(33))
        rejected.forEach { id ->
            try {
                CompanionCodec.content("t", "b", id)
                throw AssertionError("contentId '$id' must be rejected")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `decodeNotification reads hello_ack`() {
        val message = CompanionCodec.decodeNotification(
            """{"op":"hello_ack","version":1}""".toByteArray(),
        )
        assertEquals(DeviceMessage.HelloAck(1), message)
    }

    @Test
    fun `decodeNotification reads content_ack and validates the charset`() {
        val message = CompanionCodec.decodeNotification(
            """{"op":"content_ack","contentId":"c.1_a-b:2"}""".toByteArray(),
        )
        assertEquals(DeviceMessage.ContentAck("c.1_a-b:2"), message)

        try {
            CompanionCodec.decodeNotification(
                """{"op":"content_ack","contentId":"c 1"}""".toByteArray(),
            )
            throw AssertionError("space in contentId must be rejected")
        } catch (expected: MalformedNotification) {
            // expected
        }
    }

    @Test
    fun `decodeNotification reads button events`() {
        assertEquals(
            DeviceMessage.Button("back", true),
            CompanionCodec.decodeNotification("""{"op":"button","button":"back","pressed":true}""".toByteArray()),
        )
        assertEquals(
            DeviceMessage.Button("confirm", false),
            CompanionCodec.decodeNotification(
                """{"op":"button","button":"confirm","pressed":false}""".toByteArray(),
            ),
        )
    }

    @Test
    fun `decodeNotification reads errors`() {
        val message = CompanionCodec.decodeNotification(
            """{"op":"error","code":"bad_message","message":"Malformed control message"}""".toByteArray(),
        )
        assertEquals(DeviceMessage.Error("bad_message", "Malformed control message"), message)
    }

    @Test
    fun `decodeNotification reports unknown ops without failing`() {
        val message = CompanionCodec.decodeNotification(
            """{"op":"battery","level":42}""".toByteArray(),
        )
        assertEquals(DeviceMessage.Unknown, message)
    }

    @Test
    fun `decodeNotification reports unknown buttons as unknown`() {
        val message = CompanionCodec.decodeNotification(
            """{"op":"button","button":"power","pressed":true}""".toByteArray(),
        )
        assertEquals(DeviceMessage.Unknown, message)
    }

    @Test
    fun `decodeNotification fails closed on malformed input`() {
        val malformed = listOf(
            "".toByteArray(),
            "{".toByteArray(),
            """{"op":"hello_ack"""",
            """{"op":12}""",
            """{"op":"hello_ack","version":"1"}""",
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()),
            ByteArray(CompanionProtocol.MAX_NOTIFICATION_BYTES + 1),
        ).map { if (it is String) it.toByteArray() else it as ByteArray }
        malformed.forEach { data ->
            try {
                CompanionCodec.decodeNotification(data)
                throw AssertionError("input must be rejected: $data")
            } catch (expected: MalformedNotification) {
                // expected
            }
        }
    }

    @Test
    fun `decodeNotification accepts escaped strings and key order`() {
        val message = CompanionCodec.decodeNotification(
            """{"message":"line\nbreak","code":"x","op":"error"}""".toByteArray(),
        )
        assertEquals(DeviceMessage.Error("x", "line\nbreak"), message)
    }

    @Test
    fun `truncateToBytes keeps at least an empty result for tiny budgets`() {
        assertEquals("", CompanionCodec.truncateToBytes("abc", 0))
        assertEquals("a", CompanionCodec.truncateToBytes("abc", 1))
    }
}
