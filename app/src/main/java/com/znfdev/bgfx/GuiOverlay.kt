package com.znfdev.bgfx

import android.app.Activity
import android.content.Context
import com.znfdev.state.ZevController
import com.znfdev.utils.LuaApiIndex

/**
 * Public entry point for the floating BGFX panel.
 *
 * Old design attached a ComposeView to the activity's decorView — which only
 * floated over *this* app's own content. That's not what anyone wants from an
 * "overlay"; the panel needs to live above *other* apps (the game BGFX is
 * injected into, the launcher, anything). So the real work now lives in
 * [OverlayService], which adds the ComposeView to the WindowManager with
 * TYPE_APPLICATION_OVERLAY and keeps it alive as a foreground service.
 *
 * This class is now a thin facade that preserves the old `init/get/show/hide/
 * toggle/setCode/getCode/setOnRunListener/setApiIndex` entry points so existing
 * Java callers (and MainActivity) keep compiling. Values set *before* the
 * service starts are buffered in [pendingRunListener] / [pendingApiIndex] /
 * [pendingCode] and pulled in by the service in its `onCreate`.
 */
class GuiOverlay private constructor(private val app: Context) {

    fun interface OnRunListener {
        fun onRun(code: String)
    }

    /**
     * The live controller, if the service is running. The home screen used to
     * read `controller.lspStatus` from here; the cleaned-up home doesn't, so
     * this is mostly kept for callers that still want it after Launch.
     */
    val controller: ZevController? get() = OverlayService.instance?.controller

    // ---- buffered values (pushed to the service when it starts) ----
    @Volatile internal var pendingRunListener: OnRunListener? = null
    @Volatile internal var pendingApiIndex: LuaApiIndex? = null
    @Volatile internal var pendingCode: String = "-- tap to edit\n"

    // ---- lifecycle ----

    fun toggle() { OverlayService.toggle(app) }
    fun show()   { OverlayService.show(app) }
    fun hide()   { OverlayService.hide(app) }
    fun isShown(): Boolean = OverlayService.isRunning()

    fun setCode(value: String?) {
        val v = value ?: ""
        pendingCode = v
        OverlayService.instance?.setCode(v)
    }

    fun getCode(): String =
        OverlayService.instance?.controller?.code ?: pendingCode

    fun setOnRunListener(listener: OnRunListener?) {
        pendingRunListener = listener
        OverlayService.instance?.setOnRunListener(listener)
    }

    fun setApiIndex(index: LuaApiIndex?) {
        pendingApiIndex = index
        OverlayService.instance?.setApiIndex(index)
    }

    companion object {
        @Volatile
        private var instance: GuiOverlay? = null

        /**
         * Returns the process-wide [GuiOverlay], creating it on first call.
         * `activity` is accepted for API compatibility but only the
         * application context is kept — the facade survives activity death.
         */
        @JvmStatic
        @Synchronized
        fun init(activity: Activity): GuiOverlay {
            instance?.let { return it }
            return GuiOverlay(activity.applicationContext).also { instance = it }
        }

        @JvmStatic
        fun get(): GuiOverlay? = instance
    }
}
