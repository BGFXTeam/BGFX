package com.znfdev.core.net

/**
 * Exponential backoff with jitter for the reconnect loop. `next()` returns how long to
 * wait before the next attempt and advances the internal state; `reset()` goes back to
 * [initialMs] once a connection succeeds.
 */
class Backoff(
    private val initialMs: Long = 500,
    private val maxMs: Long = 8000,
    private val factor: Double = 2.0,
    private val jitter: Double = 0.2,
    private val random: () -> Double = { Math.random() }
) {
    private var attempt = 0

    fun next(): Long {
        val raw = (initialMs * Math.pow(factor, attempt.toDouble())).coerceAtMost(maxMs.toDouble())
        attempt++
        val spread = raw * jitter
        val jittered = raw - spread + random() * (2 * spread)
        return jittered.toLong().coerceIn(0, maxMs)
    }

    fun reset() {
        attempt = 0
    }
}
