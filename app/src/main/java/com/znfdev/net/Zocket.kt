package com.znfdev.net

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.znfdev.core.net.Backoff
import com.znfdev.core.net.WsFrame
import com.znfdev.core.net.WsFrames
import com.znfdev.core.net.WsHandshake
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

private const val TAG = "Zocket"

enum class ZocketState { IDLE, CONNECTING, OPEN, RETRYING, CLOSED }

/**
 * A from-scratch WebSocket client (RFC 6455, text frames only) for talking to a server
 * on the same device — no OkHttp/Ktor, just `java.net.Socket` and the frame codec in
 * `core/net`. One dedicated thread does the blocking connect+read loop and reconnects
 * with backoff; every callback is posted back to the main thread.
 *
 * Usage: set [onOpen]/[onMessage]/[onState], then call [start]. [send] is safe to call
 * from any thread; it's dropped (not queued) while the socket is down.
 */
class Zocket(
    private val host: String,
    private val port: Int,
    private val path: String = "/"
) {
    var onOpen: (() -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null
    var onState: ((ZocketState) -> Unit)? = null

    @Volatile var state: ZocketState = ZocketState.IDLE
        private set

    private val main = Handler(Looper.getMainLooper())
    private val backoff = Backoff()
    // sends must never block the caller's thread (Compose onClick = main thread), and
    // must stay in order, so they go through one dedicated writer thread — not straight
    // out on whatever thread called send().
    private val writer = Executors.newSingleThreadExecutor { r -> Thread(r, "zocket-write").apply { isDaemon = true } }
    @Volatile private var wanted = false
    @Volatile private var socket: Socket? = null
    @Volatile private var out: OutputStream? = null
    private var thread: Thread? = null

    fun start() {
        if (wanted) {
            Log.d(TAG, "start() called but already running — ignored")
            return
        }
        Log.d(TAG, "starting, target ws://$host:$port$path")
        wanted = true
        thread = Thread(::runLoop, "zocket").apply { isDaemon = true; start() }
    }

    fun stop() {
        Log.d(TAG, "stop() called")
        wanted = false
        setState(ZocketState.CLOSED)
        closeQuietly()
        writer.shutdown()
    }

    /** Safe to call from any thread, including the main thread — the actual write always
     *  happens on a dedicated background thread. Silently dropped while disconnected. */
    fun send(text: String) {
        if (out == null) {
            Log.w(TAG, "send() while not connected, dropped: ${text.take(80)}")
            return
        }
        writer.execute {
            val stream = out
            if (stream == null) {
                Log.w(TAG, "send() dropped (disconnected before it could go out): ${text.take(80)}")
                return@execute
            }
            try {
                synchronized(stream) { stream.write(WsFrames.encodeText(text)); stream.flush() }
                Log.d(TAG, "-> ${text.take(200)}")
            } catch (t: Throwable) {
                Log.w(TAG, "send() failed: ${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    // ------------------------------------------------------------------ the loop

    private fun runLoop() {
        while (wanted) {
            setState(ZocketState.CONNECTING)
            Log.d(TAG, "connecting to $host:$port ...")
            try {
                connectOnce()
                Log.d(TAG, "connection ended normally, will retry")
                backoff.reset()
            } catch (t: Throwable) {
                Log.w(TAG, "connect/read failed: ${t.javaClass.simpleName}: ${t.message}")
            }
            closeQuietly()
            if (!wanted) break
            val waitMs = backoff.next()
            Log.d(TAG, "retrying in ${waitMs}ms")
            setState(ZocketState.RETRYING)
            try {
                Thread.sleep(waitMs)
            } catch (_: InterruptedException) {
                break
            }
        }
        Log.d(TAG, "runLoop exiting (wanted=$wanted)")
    }

    private fun connectOnce() {
        val s = Socket()
        socket = s
        s.connect(InetSocketAddress(host, port), 4000)
        Log.d(TAG, "TCP connected, sending handshake")
        s.soTimeout = 0
        s.tcpNoDelay = true

        val input = BufferedInputStream(s.getInputStream())
        val output = s.getOutputStream()
        out = output

        val key = WsHandshake.newKey()
        output.write(WsHandshake.buildRequest(host, port, path, key).toByteArray(Charsets.US_ASCII))
        output.flush()
        val headers = readHeaders(input)
        Log.d(TAG, "handshake response: ${headers.substringBefore("\r\n")}")
        WsHandshake.validateResponse(headers, key)

        Log.i(TAG, "OPEN")
        setState(ZocketState.OPEN)
        main.post { onOpen?.invoke() }

        val reader = WsFrames.Reader()
        val buf = ByteArray(8192)
        while (wanted) {
            val n = input.read(buf)
            if (n < 0) {
                Log.w(TAG, "server closed the TCP connection")
                break
            }
            for (frame in reader.feed(buf, n)) {
                when (frame.opcode) {
                    WsFrame.OP_TEXT -> {
                        val text = String(frame.payload, Charsets.UTF_8)
                        Log.d(TAG, "<- ${text.take(200)}")
                        main.post { onMessage?.invoke(text) }
                    }
                    WsFrame.OP_PING -> synchronized(output) {
                        output.write(WsFrames.encodePong(frame.payload)); output.flush()
                    }
                    WsFrame.OP_CLOSE -> {
                        Log.d(TAG, "server sent CLOSE")
                        return
                    }
                    else -> Unit
                }
            }
        }
    }

    /** Reads raw bytes up to the blank line that ends the HTTP upgrade response. */
    private fun readHeaders(input: BufferedInputStream): String {
        val out = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) throw SocketTimeoutException("connection closed during handshake")
            out.append(b.toChar())
            if (out.length >= 4 && out.substring(out.length - 4) == "\r\n\r\n") break
        }
        return out.toString()
    }

    private fun setState(s: ZocketState) {
        state = s
        main.post { onState?.invoke(s) }
    }

    private fun closeQuietly() {
        try { socket?.close() } catch (_: Throwable) { }
        socket = null
        out = null
    }
}