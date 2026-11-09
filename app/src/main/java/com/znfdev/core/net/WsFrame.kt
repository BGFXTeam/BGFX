package com.znfdev.core.net

/** A decoded WebSocket frame. [payload] is already unmasked. */
class WsFrame(val opcode: Int, val payload: ByteArray, val fin: Boolean) {
    companion object {
        const val OP_CONTINUATION = 0x0
        const val OP_TEXT = 0x1
        const val OP_BINARY = 0x2
        const val OP_CLOSE = 0x8
        const val OP_PING = 0x9
        const val OP_PONG = 0xA
    }
}

/**
 * RFC 6455 framing. Encoding is client-side only (frames are always masked, never
 * fragmented — our JSON-RPC messages are small). Decoding is incremental: feed it bytes
 * as they arrive over the socket and it hands back whichever complete frames are ready,
 * buffering the rest for next time (a frame can straddle two TCP reads).
 */
object WsFrames {

    fun encodeText(text: String, maskingKey: ByteArray = randomMask()): ByteArray =
        encode(WsFrame.OP_TEXT, text.toByteArray(Charsets.UTF_8), maskingKey)

    fun encodeClose(code: Int, reason: String, maskingKey: ByteArray = randomMask()): ByteArray {
        val reasonBytes = reason.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(2 + reasonBytes.size)
        payload[0] = (code shr 8).toByte()
        payload[1] = code.toByte()
        reasonBytes.copyInto(payload, 2)
        return encode(WsFrame.OP_CLOSE, payload, maskingKey)
    }

    fun encodePong(payload: ByteArray, maskingKey: ByteArray = randomMask()): ByteArray =
        encode(WsFrame.OP_PONG, payload, maskingKey)

    fun encode(opcode: Int, payload: ByteArray, maskingKey: ByteArray): ByteArray {
        require(maskingKey.size == 4) { "masking key must be 4 bytes" }
        val head = ArrayList<Byte>(14)
        head.add((0x80 or (opcode and 0x0F)).toByte()) // FIN=1, single-frame

        val len = payload.size
        when {
            len < 126 -> head.add((0x80 or len).toByte()) // MASK=1
            len <= 0xFFFF -> {
                head.add((0x80 or 126).toByte())
                head.add((len shr 8).toByte())
                head.add(len.toByte())
            }
            else -> {
                head.add((0x80 or 127).toByte())
                for (shift in intArrayOf(56, 48, 40, 32, 24, 16, 8, 0)) head.add((len.toLong() shr shift).toByte())
            }
        }
        head.addAll(maskingKey.toList())

        val out = ByteArray(head.size + len)
        for (i in head.indices) out[i] = head[i]
        for (i in 0 until len) out[head.size + i] = (payload[i].toInt() xor maskingKey[i % 4].toInt()).toByte()
        return out
    }

    private fun randomMask(): ByteArray {
        val b = ByteArray(4)
        java.security.SecureRandom().nextBytes(b)
        return b
    }

    /** Feed bytes in, get back any frames that are now complete. Keeps partial data between calls. */
    class Reader {
        private var buffer = ByteArray(0)

        fun feed(bytes: ByteArray, len: Int = bytes.size): List<WsFrame> {
            buffer += if (len == bytes.size) bytes else bytes.copyOf(len)
            val frames = ArrayList<WsFrame>()
            while (true) {
                val frame = tryParseOne() ?: break
                frames.add(frame)
            }
            return frames
        }

        /** Server frames are never masked; a masked frame from a server is a protocol error. */
        private fun tryParseOne(): WsFrame? {
            if (buffer.size < 2) return null
            val b0 = buffer[0].toInt() and 0xFF
            val b1 = buffer[1].toInt() and 0xFF
            val fin = (b0 and 0x80) != 0
            val opcode = b0 and 0x0F
            val masked = (b1 and 0x80) != 0
            var len = (b1 and 0x7F).toLong()
            var offset = 2

            if (len == 126L) {
                if (buffer.size < offset + 2) return null
                len = ((buffer[offset].toInt() and 0xFF) shl 8 or (buffer[offset + 1].toInt() and 0xFF)).toLong()
                offset += 2
            } else if (len == 127L) {
                if (buffer.size < offset + 8) return null
                len = 0
                for (i in 0 until 8) len = (len shl 8) or (buffer[offset + i].toLong() and 0xFF)
                offset += 8
            }

            val maskKey = if (masked) {
                if (buffer.size < offset + 4) return null
                val k = buffer.copyOfRange(offset, offset + 4)
                offset += 4
                k
            } else null

            val total = offset + len
            if (buffer.size < total) return null

            val raw = buffer.copyOfRange(offset, total.toInt())
            val payload = if (maskKey != null) {
                ByteArray(raw.size) { i -> (raw[i].toInt() xor maskKey[i % 4].toInt()).toByte() }
            } else raw

            buffer = buffer.copyOfRange(total.toInt(), buffer.size)
            return WsFrame(opcode, payload, fin)
        }
    }
}
