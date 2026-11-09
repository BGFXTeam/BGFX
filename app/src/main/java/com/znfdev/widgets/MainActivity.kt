package com.znfdev.widgets

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.znfdev.bgfx.GuiOverlay
import com.znfdev.bgfx.OverlayService
import com.znfdev.bgfx.UpdateChecker
import com.znfdev.bgfx.api.Data
import com.znfdev.state.StorageAccess
import com.znfdev.ui.dialogs.DialogSpec
import com.znfdev.ui.dialogs.Dialogs
import com.znfdev.ui.dialogs.ZevDialog
import com.znfdev.ui.home.HomeScreen
import com.znfdev.ui.home.HomeState
import com.znfdev.ui.home.ServerStatus
import com.znfdev.ui.theme.ZevTheme
import com.znfdev.utils.LatestVersion
import com.znfdev.utils.LuaApiIndex

/**
 * Launcher activity.
 *
 * Minimal home screen: a single Start / Stop button driving [OverlayService].
 * Server data is still fetched on launch (notices, update check) and surfaces
 * as a small status badge + dialogs. The button reads "Start" by default and
 * flips to a red "Stop" while the overlay service is running.
 *
 * Permissions:
 *  - "Display over other apps" (SYSTEM_ALERT_WINDOW) — required for the overlay.
 *  - "All files access" (MANAGE_EXTERNAL_STORAGE) — required for scripts/plugins.
 * If either is missing on Start, we route the user to the right system screen.
 */
class MainActivity : ComponentActivity() {

    private val home = HomeState()
    private var dialog by mutableStateOf<DialogSpec?>(null)

    private var overlay by mutableStateOf<GuiOverlay?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // setContent first so the home screen is up immediately; the overlay
        // facade is created right after and is what the Start button drives.
        setContent { ZevTheme { Root() } }

        overlay = GuiOverlay.init(this).also { gui ->
            gui.setOnRunListener { code ->
                val firstLine = code.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().take(48)
                Toast.makeText(this, "Run: $firstLine", Toast.LENGTH_SHORT).show()
            }
        }

        home.version = LatestVersion().let { it.versionName + " " + it.versionLabel }
        loadApiIndex()
        loadServerData()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        // Sync button state — e.g. user could have killed the service from the
        // notification, or come back from the permission settings screen.
        home.isServiceRunning = OverlayService.isRunning()
    }

    @Composable
    private fun Root() {
        HomeScreen(
            state = home,
            onToggleService = { onToggleService() }
        )
        dialog?.let { spec -> ZevDialog(spec, onDismiss = { dialog = null }) }
    }

    // --------------------------------------------------------------- service toggle

    private fun onToggleService() {
        // Already running → stop.
        if (OverlayService.isRunning()) {
            OverlayService.stop(this)
            home.isServiceRunning = false
            return
        }

        // 1) overlay permission — without it the service's addView() will throw.
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Grant 'Display over other apps' first", Toast.LENGTH_LONG).show()
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Throwable) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            }
            return
        }

        // 2) storage permission — overlay will start either way, but scripts/plugins
        //    won't load without it. Tell the user instead of silently failing.
        if (!StorageAccess.hasAccess(this)) {
            Toast.makeText(this, "Grant files access to load scripts", Toast.LENGTH_LONG).show()
            StorageAccess.request(this)
            return
        }

        // 3) both green — start the floating overlay and get out of the way so
        //    it floats over the user's actual target app (the game). The bubble
        //    appears; the user taps it once they're in the game to open the panel.
        OverlayService.start(this)
        home.isServiceRunning = true
        moveTaskToBack(true)
    }

    // --------------------------------------------------------------- refresh

    private fun refreshPermissions() {
        home.hasStorage = StorageAccess.hasAccess(this)
        home.hasOverlay = Settings.canDrawOverlays(this)
        // If the overlay is already running, push the (possibly new) storage
        // state into its controller so scripts/plugins reload.
        OverlayService.instance?.refreshAccess()
    }

    private fun loadApiIndex() {
        LuaApiIndex.loadAsync(this, "bgfx_api.json", object : LuaApiIndex.LoadCallback {
            override fun onLoaded(index: LuaApiIndex) {
                overlay?.setApiIndex(index)
            }

            override fun onError(error: Throwable) {
                // Home UI doesn't surface this; the panel's own completion
                // will simply be empty. Toast so the dev knows what happened.
                Toast.makeText(this@MainActivity, "API index failed to load", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadServerData() {
        home.server = ServerStatus.Connecting
        UpdateChecker.fetch { data, error ->
            if (data != null) {
                home.server = ServerStatus.Online
                handleServerData(data)
            } else {
                home.server = ServerStatus.Offline
                dialog = Dialogs.networkError(onRetry = { loadServerData() }, onExit = { finishAffinity() })
            }
        }
    }

    private fun handleServerData(data: Data) {
        val notice = data.notice
        when {
            UpdateChecker.isUpdateAvailable(data) ->
                dialog = Dialogs.update { openLink(data.bgfxLink) }
            notice != null && notice.isActive && !notice.data.isNullOrBlank() ->
                dialog = Dialogs.notice(notice.data, notice.isMarkDown)
        }
    }

    private fun openLink(url: String?) {
        if (url.isNullOrBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open the link", Toast.LENGTH_SHORT).show()
        }
    }
}
