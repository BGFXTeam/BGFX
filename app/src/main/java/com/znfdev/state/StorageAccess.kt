package com.znfdev.state

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings as AndroidSettings

/** Everything BGFX reads and writes lives under [ROOT], which needs "all files" access on Android 11+. */
object StorageAccess {

    const val ROOT = "/storage/emulated/0/BGFX/"
    const val SCRIPTS = ROOT + "Scripts/"
    const val PLUGINS = ROOT + "plugins/"

    fun hasAccess(context: Context): Boolean = when {
        Build.VERSION.SDK_INT >= 30 -> Environment.isExternalStorageManager()
        Build.VERSION.SDK_INT >= 23 ->
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        else -> true
    }

    fun request(activity: Activity) {
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val uri = Uri.parse("package:" + activity.packageName)
                try {
                    activity.startActivity(Intent(AndroidSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri))
                } catch (e: Exception) {
                    activity.startActivity(Intent(AndroidSettings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                }
            } else if (Build.VERSION.SDK_INT >= 23) {
                activity.requestPermissions(
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
            }
        } catch (_: Throwable) {
        }
    }
}
