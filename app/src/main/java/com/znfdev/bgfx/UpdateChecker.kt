package com.znfdev.bgfx

import com.znfdev.bgfx.api.Data
import com.znfdev.bgfx.api.DataService
import com.znfdev.utils.LatestVersion

/** Fetches the server's bgfx.json and compares versions. Callbacks arrive on the main thread. */
object UpdateChecker {

    fun fetch(onResult: (data: Data?, error: Throwable?) -> Unit) {
        DataService.fetchData(object : DataService.DataCallback {
            override fun onSuccsss(data: Data) = onResult(data, null)
            override fun onFailure(t: Throwable) = onResult(null, t)
        })
    }

    /** True when the server advertises a newer build than the one that's running. */
    fun isUpdateAvailable(server: Data): Boolean {
        val remote = server.versionInfo ?: return false
        return LatestVersion.isOld(LatestVersion(), remote)
    }
}
