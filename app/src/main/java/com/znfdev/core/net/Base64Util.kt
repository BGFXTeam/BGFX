package com.znfdev.core.net

/**
 * Minimal Base64 encoder (RFC 4648, standard alphabet, padded). `java.util.Base64` needs
 * API 26+ and `android.util.Base64` isn't available on a plain JVM, so this small
 * from-scratch version works everywhere and is what the WebSocket handshake uses.
 */
object Base64Util {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

    fun encode(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size + 2) / 3 * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
            val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
            val triple = (b0 shl 16) or (b1 shl 8) or b2

            out.append(ALPHABET[(triple shr 18) and 0x3F])
            out.append(ALPHABET[(triple shr 12) and 0x3F])
            out.append(if (i + 1 < bytes.size) ALPHABET[(triple shr 6) and 0x3F] else '=')
            out.append(if (i + 2 < bytes.size) ALPHABET[triple and 0x3F] else '=')
            i += 3
        }
        return out.toString()
    }
}
