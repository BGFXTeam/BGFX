package com.znfdev.core.net

import java.security.MessageDigest

/** RFC 6455 opening handshake: the client key/accept-key math, framed as pure functions. */
object WsHandshake {

    private const val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"

    /** A fresh `Sec-WebSocket-Key` value from 16 random bytes. */
    fun newKey(random: () -> ByteArray = { randomBytes(16) }): String = Base64Util.encode(random())

    /** What the server's `Sec-WebSocket-Accept` header must equal for [key] to be valid. */
    fun expectedAccept(key: String): String {
        val sha1 = MessageDigest.getInstance("SHA-1")
        val digest = sha1.digest((key + GUID).toByteArray(Charsets.US_ASCII))
        return Base64Util.encode(digest)
    }

    fun buildRequest(host: String, port: Int, path: String, key: String): String =
        "GET $path HTTP/1.1\r\n" +
            "Host: $host:$port\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: Upgrade\r\n" +
            "Sec-WebSocket-Key: $key\r\n" +
            "Sec-WebSocket-Version: 13\r\n" +
            "\r\n"

    /** Parses the raw response headers and validates status + accept-key. Throws on any mismatch. */
    fun validateResponse(rawHeaders: String, key: String) {
        val statusLine = rawHeaders.substringBefore("\r\n")
        require("101" in statusLine) { "Expected HTTP 101, got: $statusLine" }
        val accept = headerValue(rawHeaders, "Sec-WebSocket-Accept")
            ?: error("Missing Sec-WebSocket-Accept header")
        require(accept == expectedAccept(key)) { "Sec-WebSocket-Accept mismatch" }
    }

    private fun headerValue(headers: String, name: String): String? {
        for (line in headers.split("\r\n")) {
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).equals(name, ignoreCase = true)) {
                return line.substring(idx + 1).trim()
            }
        }
        return null
    }

    private fun randomBytes(n: Int): ByteArray {
        val r = java.security.SecureRandom()
        val b = ByteArray(n)
        r.nextBytes(b)
        return b
    }
}
