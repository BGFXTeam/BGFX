package com.znfdev.state

import org.json.JSONObject
import java.io.File

/** Mirrors /storage/emulated/0/BGFX/key.json exactly, typo and all — that's the file BGFX writes. */
class LicenseKey(val key: String, val isVaild: Boolean, val expireTime: Long) {
    fun remainingMs(nowMs: Long = System.currentTimeMillis()): Long = expireTime - nowMs
}

/** How close to expiry we are, used to decide whether (and how urgently) to remind. */
enum class LicenseBucket { NORMAL, HOUR, MINUTES, EXPIRED }

fun bucketFor(remainingMs: Long): LicenseBucket = when {
    remainingMs <= 0 -> LicenseBucket.EXPIRED
    remainingMs <= 5 * 60_000 -> LicenseBucket.MINUTES
    remainingMs <= 60 * 60_000 -> LicenseBucket.HOUR
    else -> LicenseBucket.NORMAL
}

object LicenseKeyStore {
    private const val PATH = StorageAccess.ROOT + "key.json"

    /** Reads key.json off whatever thread calls this — callers already do this on IO. */
    fun read(): LicenseKey? = runCatching {
        val text = File(PATH).readText()
        val json = JSONObject(text)
        LicenseKey(
            key = json.optString("key", ""),
            isVaild = json.optBoolean("isVaild", true),
            expireTime = json.optLong("expireTime", 0)
        )
    }.getOrNull()
}
