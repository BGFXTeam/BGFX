package com.znfdev.bgfx

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.znfdev.state.ZevController
import com.znfdev.ui.overlay.OverlayBubble
import com.znfdev.ui.overlay.OverlayPanel
import com.znfdev.ui.theme.ZevTheme
import com.znfdev.utils.LuaApiIndex
import kotlin.math.abs

/**
 * Foreground service that hosts the BGFX panel as a **real** system overlay.
 *
 * ## Two-window architecture (this is the fix for "can't touch anything")
 *
 * The old design used one full-screen window. Even though the Compose content
 * was mostly transparent (just a bubble), the *window itself* consumed every
 * touch in its bounds — so the game/app underneath was completely unplayable.
 *
 * Now there are two separate windows:
 *
 * 1. **Bubble window** — exactly 56dp × 56dp, positioned via `LayoutParams.x/y`.
 *    `FLAG_NOT_TOUCH_MODAL` lets touches *outside* this tiny window fall through
 *    to the app below. Touches *on* the bubble are handled by an
 *    `OnTouchListener` that drags the window (via `updateViewLayout`) or, on a
 *    clean tap, opens the panel.
 *
 * 2. **Panel window** — `MATCH_PARENT`, only added when the panel is open.
 *    Contains the scrim + panel. When closed, this window doesn't exist at all,
 *    so there's nothing blocking touches.
 *
 * The bubble window is always present while the service runs; the panel window
 * is added/removed on open/close. Both use `TYPE_APPLICATION_OVERLAY` so they
 * float above other apps.
 */
class OverlayService : Service() {

    companion object {
        @Volatile
        private var _instance: OverlayService? = null
        val instance: OverlayService? get() = _instance

        fun isRunning(): Boolean = _instance != null

        /** Starts the service if it isn't running. The bubble appears; the panel stays closed. */
        fun start(ctx: Context) {
            if (_instance != null) return
            startForegroundSafely(ctx, Intent(ctx, OverlayService::class.java))
        }

        /** Starts the service (if needed) and opens the panel immediately. */
        fun show(ctx: Context) {
            val intent = Intent(ctx, OverlayService::class.java).setAction(ACTION_SHOW)
            if (_instance == null) startForegroundSafely(ctx, intent) else ctx.startService(intent)
        }

        /** Closes the panel (the bubble stays so the user can re-open it). */
        fun hide(ctx: Context) {
            if (_instance == null) return
            ctx.startService(Intent(ctx, OverlayService::class.java).setAction(ACTION_HIDE))
        }

        /** Toggles the panel without restarting the service. */
        fun toggle(ctx: Context) {
            val i = Intent(ctx, OverlayService::class.java).setAction(ACTION_TOGGLE)
            if (_instance == null) startForegroundSafely(ctx, i) else ctx.startService(i)
        }

        /** Fully tears the overlay down (removes both windows, stops the service). */
        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }

        private fun startForegroundSafely(ctx: Context, intent: Intent) {
            try {
                ContextCompat.startForegroundService(ctx, intent)
            } catch (_: Throwable) {
                try { ctx.startService(intent) } catch (_: Throwable) {}
            }
        }

        private const val ACTION_SHOW = "com.znfdev.bgfx.action.SHOW"
        private const val ACTION_HIDE = "com.znfdev.bgfx.action.HIDE"
        private const val ACTION_TOGGLE = "com.znfdev.bgfx.action.TOGGLE"

        private const val NOTIF_ID = 4242
        private const val CHANNEL_ID = "bgfx_overlay"

        private const val BUBBLE_SIZE_DP = 56
        private const val BUBBLE_MARGIN_DP = 18
        private const val BUBBLE_BOTTOM_INSET_DP = 90
    }

    lateinit var controller: ZevController
        private set

    private lateinit var windowManager: WindowManager
    private lateinit var viewOwner: ServiceViewOwner

    // ---- bubble window ----
    private lateinit var bubbleView: BubbleHostView
    private lateinit var bubbleParams: WindowManager.LayoutParams
    private var bubblePressed by mutableStateOf(false)

    /** Bubble position in pixels (relative to gravity TOP|START). */
    private var bubbleX = 0
    private var bubbleY = 0

    // ---- panel window (null when closed) ----
    private var panelView: ComposeView? = null

    // ---- drag tracking ----
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop.toFloat() }

    private val density: Float get() = resources.displayMetrics.density
    private fun dp(n: Int): Int = (n * density).toInt()

    private val displayWidth: Int get() = resources.displayMetrics.widthPixels
    private val displayHeight: Int get() = resources.displayMetrics.heightPixels

    private val bubbleSizePx get() = dp(BUBBLE_SIZE_DP)
    private val marginPx get() = dp(BUBBLE_MARGIN_DP)
    private val bottomInsetPx get() = dp(BUBBLE_BOTTOM_INSET_DP)

    override fun onBind(intent: Intent?): IBinder? = null

    // ================================================================ lifecycle

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        startForegroundCompat()

        _instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        controller = ZevController(applicationContext).apply {
            requestStorage = null
        }

        // Pull buffered values from the facade (run listener, API index, code)
        // that were set before the service started.
        GuiOverlay.get()?.let { facade ->
            facade.pendingRunListener?.let { controller.onRun = fun(code: String) { it.onRun(code) } }
            facade.pendingApiIndex?.let { controller.updateApiIndex(it) }
            controller.code = facade.pendingCode
        }

        viewOwner = ServiceViewOwner().apply { resume() }

        // Place the bubble at the bottom-right on first launch.
        //bubbleX = (displayWidth - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
        //bubbleY = (displayHeight - bubbleSizePx - bottomInsetPx).coerceAtLeast(marginPx)
        // Place the bubble at the top-left on first launch.
        bubbleX = marginPx
        bubbleY = marginPx

        setupBubbleWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        when (intent?.action) {
            ACTION_HIDE -> hidePanel()
            ACTION_TOGGLE -> if (panelView != null) hidePanel() else showPanel()
            ACTION_SHOW -> showPanel()
            null -> Unit // plain start() — just the bubble
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hidePanel()
        try { if (::bubbleView.isInitialized) windowManager.removeView(bubbleView) } catch (_: Throwable) {}
        if (::viewOwner.isInitialized) viewOwner.destroy()
        if (::controller.isInitialized) controller.dispose()
        if (_instance === this) _instance = null
    }

    // ================================================================ bubble window

    private fun setupBubbleWindow() {
    bubbleView = BubbleHostView(this).apply {
        setViewTreeLifecycleOwner(viewOwner)
        setViewTreeSavedStateRegistryOwner(viewOwner)
        setViewTreeViewModelStoreOwner(viewOwner)

        val composeView = ComposeView(this@OverlayService).apply {
            setViewTreeLifecycleOwner(viewOwner)
            setViewTreeSavedStateRegistryOwner(viewOwner)
            setViewTreeViewModelStoreOwner(viewOwner)

            setContent {
                ZevTheme {
                    OverlayBubble(pressed = bubblePressed)
                }
            }
        }

        addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
    }

    bubbleView.setOnTouchListener(bubbleTouchListener)

    bubbleParams = WindowManager.LayoutParams().apply {
        width = bubbleSizePx
        height = bubbleSizePx
        type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        format = PixelFormat.TRANSLUCENT
        flags = (
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        gravity = Gravity.TOP or Gravity.START
        x = bubbleX
        y = bubbleY

        if (Build.VERSION.SDK_INT >= 28) {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }

    try {
        windowManager.addView(bubbleView, bubbleParams)
    } catch (t: Throwable) {
        stopSelf()
    }
}

    private val bubbleTouchListener = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                isDragging = false
                bubblePressed = true
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchX
                val dy = event.rawY - lastTouchY
                lastTouchX = event.rawX
                lastTouchY = event.rawY
                if (!isDragging && (abs(event.rawX - initialTouchX) > touchSlop ||
                        abs(event.rawY - initialTouchY) > touchSlop)) {
                    isDragging = true
                }
                if (isDragging) {
                    val maxX = (displayWidth - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
                    val maxY = (displayHeight - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
                    bubbleX = (bubbleX + dx.toInt()).coerceIn(marginPx, maxX)
                    bubbleY = (bubbleY + dy.toInt()).coerceIn(marginPx, maxY)
                    updateBubblePosition()
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                bubblePressed = false
                if (!isDragging) {
                    // clean tap → open the panel
                    showPanel()
                } else {
                    snapBubbleToEdge()
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                bubblePressed = false
                if (isDragging) snapBubbleToEdge()
                true
            }
            else -> false
        }
    }

    private fun updateBubblePosition() {
        try {
            bubbleParams.x = bubbleX
            bubbleParams.y = bubbleY
            windowManager.updateViewLayout(bubbleView, bubbleParams)
        } catch (_: Throwable) {}
    }

    /** Animates the bubble to the nearest left/right screen edge. */
    private fun snapBubbleToEdge() {
        /*val targetX = if (bubbleX + bubbleSizePx / 2 < displayWidth / 2) {
            marginPx
        } else {
            (displayWidth - bubbleSizePx - marginPx).coerceAtLeast(marginPx)
        }
        val startX = bubbleX
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                bubbleX = (startX + (targetX - startX) * t).toInt()
                updateBubblePosition()
            }
        }.start()*/
    }

    // ================================================================ panel window

    private fun showPanel() {
        if (panelView != null) return
        val pv = ComposeView(this).apply {
            setViewTreeLifecycleOwner(viewOwner)
            setViewTreeSavedStateRegistryOwner(viewOwner)
            setViewTreeViewModelStoreOwner(viewOwner)
            setContent {
                ZevTheme {
                    OverlayPanel(c = controller, onClose = { hidePanel() })
                }
            }
        }
        val params = WindowManager.LayoutParams().apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            format = PixelFormat.TRANSLUCENT
        
            flags = (
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        
            gravity = Gravity.TOP or Gravity.START
        
            softInputMode = (
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                    WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED
            )
        
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            windowManager.addView(pv, params)
            panelView = pv
            // Hide the bubble while the panel is open (it's behind the panel
            // window anyway, but this also stops it from consuming touches
            // in the corner that peeks past the panel).
            bubbleView.visibility = View.INVISIBLE
        } catch (t: Throwable) {
            pv.disposeComposition()
        }
    }

    private fun hidePanel() {
        panelView?.let { pv ->
            try { windowManager.removeView(pv) } catch (_: Throwable) {}
            pv.disposeComposition()
        }
        panelView = null
        if (::bubbleView.isInitialized) bubbleView.visibility = View.VISIBLE
    }

    // ================================================================ push from facade

    internal fun setApiIndex(index: LuaApiIndex?) {
        if (::controller.isInitialized) controller.updateApiIndex(index)
    }

    internal fun setOnRunListener(listener: GuiOverlay.OnRunListener?) {
        if (!::controller.isInitialized) return
        controller.onRun = if (listener == null) null else fun(code: String) { listener.onRun(code) }
    }

    internal fun setCode(code: String) {
        if (::controller.isInitialized) controller.code = code
    }

    internal fun refreshAccess() {
        if (::controller.isInitialized) controller.refreshAccess()
    }

    // ================================================================ notification

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "BGFX",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the BGFX overlay alive while you're in Blockman Go app."
                    setShowBadge(false)
                }
            )
        }
    }

    private fun startForegroundCompat() {
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("BGFX")
            .setContentText("BGFX UI is active")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }
}

/**
 * Lets a ComposeView live inside a Service: Compose needs a lifecycle, a
 * saved-state registry and a ViewModelStore on the view tree, so we provide
 * our own (Services don't have any of those by default). Shared by both the
 * bubble and panel ComposeViews.
 */
private class ServiceViewOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private val store = ViewModelStore()

    init {
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
    }

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = store

    fun resume() { registry.currentState = Lifecycle.State.RESUMED }
    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
        store.clear()
    }
}

/**
 * ComposeView subclass used for the bubble. Overrides `onInterceptTouchEvent`
 * to always return true so the host view grabs every touch and routes it to
 * the `OnTouchListener` set in `setupBubbleWindow`.
 *
 * Why this is needed: a plain ComposeView is a ViewGroup. Its children (the
 * AssetVideo's TextureView, plus Compose's own pointer-input layer) sit inside
 * it and consume `ACTION_DOWN` before the parent's `OnTouchListener` fires.
 * With touches being swallowed by the children, the drag never starts and the
 * bubble appears "stuck". Intercepting forces the parent to handle the event
 * itself, so the `OnTouchListener` (which does the drag-via-updateViewLayout
 * and the tap-to-open-panel logic) actually runs.
 *
 * The Compose content keeps rendering normally — interception only affects
 * touch dispatch, not drawing. The video still plays, the press-scale still
 * animates (driven by `bubblePressed` state we flip in the touch listener).
 */
private class BubbleHostView(context: Context) : FrameLayout(context) {
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return true
    }
}