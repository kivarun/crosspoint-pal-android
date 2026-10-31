package fans.crosspoint.pal.ble

/**
 * Minimal strict JSON reader for reader→phone notifications.
 *
 * The firmware's writers emit small fixed-shape objects; this parser exists
 * so the phone never trusts byte offsets or string concatenation. It accepts
 * exactly one JSON object, bounds every allocation, follows RFC 8259 string
 * escaping (including surrogate pairs), and rejects anything else.
 */
internal class JsonParser(private val data: ByteArray) {

    private var index = 0

    /** Parses one complete object; null when the input is not exactly that. */
    fun parseObject(): JsonObject? {
        val value = parseValue(maxDepth = 4) as? JsonObject ?: return null
        skipWhitespace()
        return if (index == data.size) value else null
    }

    internal sealed interface Value
    internal data class Str(val value: String) : Value
    internal data class Num(val value: String) : Value
    internal data class Bool(val value: Boolean) : Value
    internal data object NullValue : Value

    /** Parsed representation: only what the protocol messages need. */
    internal class JsonObject : Value {
        val members = mutableListOf<Pair<String, Value>>()

        fun string(key: String): String? = (value(key) as? Str)?.value
        fun integer(key: String): Int? = (value(key) as? Num)?.let { it.value.toIntOrNull() }
        fun boolean(key: String): Boolean? = (value(key) as? Bool)?.value

        private fun value(key: String): Value? = members.lastOrNull { it.first == key }?.second
    }

    private fun parseValue(maxDepth: Int): Value? {
        if (maxDepth == 0) return null
        skipWhitespace()
        if (index >= data.size) return null
        return when (data[index]) {
            '{'.code.toByte() -> parseObjectValue(maxDepth)
            '"'.code.toByte() -> parseString()?.let { Str(it) }
            't'.code.toByte() -> if (matchWord("true")) Bool(true) else null
            'f'.code.toByte() -> if (matchWord("false")) Bool(false) else null
            'n'.code.toByte() -> if (matchWord("null")) NullValue else null
            else -> parseNumber()
        }
    }

    private fun parseObjectValue(maxDepth: Int): JsonObject? {
        index++ // '{'
        val result = JsonObject()
        skipWhitespace()
        if (index < data.size && data[index] == '}'.code.toByte()) {
            index++
            return result
        }
        while (true) {
            skipWhitespace()
            val key = parseString() ?: return null
            skipWhitespace()
            if (index >= data.size || data[index] != ':'.code.toByte()) return null
            index++
            val value = parseValue(maxDepth - 1) ?: return null
            result.members.add(key to value)
            skipWhitespace()
            when {
                index >= data.size -> return null
                data[index] == ','.code.toByte() -> index++
                data[index] == '}'.code.toByte() -> {
                    index++
                    return result
                }
                else -> return null
            }
        }
    }

    private fun parseString(): String? {
        if (index >= data.size || data[index] != '"'.code.toByte()) return null
        index++
        val builder = StringBuilder()
        while (true) {
            if (index >= data.size) return null
            val c = data[index]
            when {
                c == '"'.code.toByte() -> {
                    index++
                    return builder.toString()
                }
                c == '\\'.code.toByte() -> {
                    index++
                    if (index >= data.size) return null
                    when (data[index]) {
                        '"'.code.toByte() -> builder.append('"')
                        '\\'.code.toByte() -> builder.append('\\')
                        '/'.code.toByte() -> builder.append('/')
                        'b'.code.toByte() -> builder.append('\b')
                        'f'.code.toByte() -> builder.append('\u000C')
                        'n'.code.toByte() -> builder.append('\n')
                        'r'.code.toByte() -> builder.append('\r')
                        't'.code.toByte() -> builder.append('\t')
                        'u'.code.toByte() -> {
                            val high = parseHex4() ?: return null
                            if (high in 0xD800..0xDBFF) {
                                if (index + 1 >= data.size || data[index] != '\\'.code.toByte() ||
                                    data[index + 1] != 'u'.code.toByte()
                                ) {
                                    return null
                                }
                                index += 2
                                val low = parseHex4() ?: return null
                                if (low !in 0xDC00..0xDFFF) return null
                                builder.appendCodePoint(0x10000 + ((high - 0xD800) shl 10) + (low - 0xDC00))
                            } else if (high in 0xDC00..0xDFFF) {
                                return null
                            } else {
                                builder.appendCodePoint(high)
                            }
                        }
                        else -> return null
                    }
                    index++
                }
                c >= 0x20 -> {
                    builder.append(c.toInt().toChar())
                    index++
                }
                else -> return null // raw control bytes are not valid JSON
            }
        }
    }

    private fun parseHex4(): Int? {
        if (index + 4 > data.size) return null
        var value = 0
        repeat(4) {
            val digit = hexDigit(data[index]) ?: return null
            value = (value shl 4) or digit
            index++
        }
        return value
    }

    private fun parseNumber(): Value? {
        val start = index
        if (index < data.size && (data[index] == '-'.code.toByte())) index++
        while (index < data.size && data[index] in '0'.code.toByte()..'9'.code.toByte()) index++
        if (index == start) return null
        val text = data.copyOfRange(start, index).toString(Charsets.US_ASCII)
        return Num(text)
    }

    private fun matchWord(word: String): Boolean {
        if (index + word.length > data.size) return false
        for (k in word.indices) {
            if (data[index + k] != word[k].code.toByte()) return false
        }
        index += word.length
        return true
    }

    private fun skipWhitespace() {
        while (index < data.size && data[index] in WHITESPACE) index++
    }

    private fun hexDigit(c: Byte): Int? = when (c.toInt()) {
        in '0'.code..'9'.code -> c.toInt() - '0'.code
        in 'a'.code..'f'.code -> c.toInt() - 'a'.code + 10
        in 'A'.code..'F'.code -> c.toInt() - 'A'.code + 10
        else -> null
    }

    companion object {
        private val WHITESPACE = byteArrayOf(' '.code.toByte(), '\t'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte())
    }
}
