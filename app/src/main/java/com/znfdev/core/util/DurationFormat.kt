package com.znfdev.core.util

/** Formats a remaining/elapsed duration the way a settings row or a reminder toast wants it. */
object DurationFormat {

    /**
     * [remainingMs] is `expiry - now`. Positive -> "3d 4h left" / "42m left" / "58s left".
     * Non-positive -> "Expired" (or "Expired 2h ago" once [showElapsed] and it's been a while).
     */
    fun remaining(remainingMs: Long, showElapsed: Boolean = true): String {
        if (remainingMs <= 0) {
            if (!showElapsed) return "Expired"
            val elapsed = -remainingMs
            return if (elapsed < 60_000) "Expired" else "Expired ${compact(elapsed)} ago"
        }
        return "${compact(remainingMs)} left"
    }

    /** "3d 4h", "42m", "58s", "0s" — at most two units, smallest included one only when it's the top unit. */
    fun compact(ms: Long): String {
        val totalSeconds = ms / 1000
        val days = totalSeconds / 86400
        val hours = (totalSeconds % 86400) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}
