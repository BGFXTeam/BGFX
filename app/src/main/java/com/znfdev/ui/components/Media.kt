package com.znfdev.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.util.LruCache
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

// ------------------------------------------------------------------ remote images

private object BitmapCache {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024) { // KB
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    operator fun get(url: String): Bitmap? = cache.get(url)
    operator fun set(url: String, bmp: Bitmap) { cache.put(url, bmp) }
}

private fun downloadBitmap(url: String, maxEdge: Int = 1600): Bitmap? {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "BGFX")
        }
        if (conn.responseCode !in 200..299) return null
        val bytes = conn.inputStream.use { it.readBytes() }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
    } catch (t: Throwable) {
        null
    } finally {
        conn?.disconnect()
    }
}

/** Loads [url] once (memory-cached). `null` while loading or if it failed. */
@Composable
fun rememberRemoteImage(url: String?): State<ImageBitmap?> = produceState<ImageBitmap?>(null, url) {
    if (url.isNullOrBlank()) {
        value = null
        return@produceState
    }
    BitmapCache[url]?.let {
        value = it.asImageBitmap()
        return@produceState
    }
    val bmp = withContext(Dispatchers.IO) { downloadBitmap(url) }
    if (bmp != null) BitmapCache[url] = bmp
    value = bmp?.asImageBitmap()
}

// ------------------------------------------------------------------ asset video

/**
 * Center-cropped video from `assets/`, played through a single TextureView + a swappable
 * MediaPlayer (no extra dependency). [onFailed] fires if the asset is missing or can't
 * be decoded, [onRendered] when the first frame is on screen.
 */
@Composable
fun AssetVideo(
    asset: String,
    modifier: Modifier = Modifier,
    looping: Boolean = true,
    muted: Boolean = true,
    paused: Boolean = false,
    onFailed: () -> Unit = {},
    onRendered: () -> Unit = {},
    onCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val failed by rememberUpdatedState(onFailed)
    val rendered by rememberUpdatedState(onRendered)
    val completed by rememberUpdatedState(onCompleted)

    val controller = remember(looping) {
        VideoController(context.applicationContext, looping, { failed() }, { rendered() }, { completed() })
    }
    controller.muted = muted
    controller.paused = paused
    LaunchedEffect(asset) { controller.setAsset(asset) }

    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> controller.hostStopped = true
                Lifecycle.Event.ON_START -> controller.hostStopped = false
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    // One TextureView for the lifetime of the controller; only the player is rebuilt
    // when the asset changes, so there's no ~1s black flicker from view re-creation.
    AndroidView(
        modifier = modifier,
        factory = { ctx -> TextureView(ctx).also { controller.attach(it) } }
    )
}

private class VideoController(
    private val context: Context,
    private val looping: Boolean,
    private val onFailed: () -> Unit,
    private val onRendered: () -> Unit,
    private val onCompleted: () -> Unit
) : TextureView.SurfaceTextureListener {

    private var view: TextureView? = null
    private var player: MediaPlayer? = null
    private var surface: Surface? = null
    private var prepared = false
    private var videoW = 0
    private var videoH = 0
    private var released = false
    private var asset: String? = null

    var muted = true
        set(value) {
            field = value
            applyVolume()
        }
    var paused = false
        set(value) {
            field = value
            syncPlayback()
        }
    var hostStopped = false
        set(value) {
            field = value
            syncPlayback()
        }

    fun attach(v: TextureView) {
        view = v
        v.isOpaque = false
        v.surfaceTextureListener = this
    }

    fun setAsset(newAsset: String) {
        if (newAsset == asset) return
        asset = newAsset
        if (surface != null) start()   // otherwise onSurfaceTextureAvailable starts it
    }

    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
        surface = Surface(st)
        start()
    }

    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) = applyCrop()

    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
        // the view may get a fresh surface later (detach/attach), so don't mark as released
        stopPlayer()
        surface?.release()
        surface = null
        return true
    }

    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit

    private fun start() {
        if (released) return
        val a = asset ?: return
        stopPlayer()
        val mp = MediaPlayer()
        player = mp
        try {
            openSource(mp, a)
            mp.setSurface(surface)
            mp.isLooping = looping
            mp.setOnVideoSizeChangedListener { _, w, h ->
                videoW = w
                videoH = h
                applyCrop()
            }
            mp.setOnPreparedListener {
                prepared = true
                applyVolume()
                applyCrop()
                syncPlayback()
            }
            mp.setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) onRendered()
                false
            }
            mp.setOnCompletionListener { onCompleted() }
            mp.setOnErrorListener { _, _, _ ->
                onFailed()
                true
            }
            mp.prepareAsync()
        } catch (t: Throwable) {
            stopPlayer()
            onFailed()
        }
    }

    private fun openSource(mp: MediaPlayer, asset: String) {
        try {
            context.assets.openFd(asset).use { fd ->
                mp.setDataSource(fd.fileDescriptor, fd.startOffset, fd.length)
            }
        } catch (e: IOException) {
            // compressed assets can't be opened as a file descriptor: copy once to cache
            val safe = "vid_" + asset.replace(Regex("[^A-Za-z0-9]"), "_") + ".mp4"
            val out = File(context.cacheDir, safe)
            if (!out.exists() || out.length() == 0L) {
                context.assets.open(asset).use { input ->
                    out.outputStream().use { input.copyTo(it) }
                }
            }
            mp.setDataSource(out.absolutePath)
        }
    }

    private fun applyVolume() {
        val v = if (muted) 0f else 1f
        try { player?.setVolume(v, v) } catch (_: Throwable) { }
    }

    private fun syncPlayback() {
        val mp = player ?: return
        if (!prepared) return
        try {
            val shouldPlay = !paused && !hostStopped
            if (shouldPlay && !mp.isPlaying) mp.start()
            else if (!shouldPlay && mp.isPlaying) mp.pause()
        } catch (_: Throwable) { }
    }

    /** Center-crop: keep the video's aspect ratio while filling the whole view. */
    private fun applyCrop() {
        val v = view ?: return
        if (videoW == 0 || videoH == 0) return
        val vw = v.width.toFloat()
        val vh = v.height.toFloat()
        if (vw == 0f || vh == 0f) {
            v.post(::applyCrop)        // <-- retry once layout is done
            return
        }
        val scale = max(vw / videoW, vh / videoH)
        val sx = videoW * scale / vw
        val sy = videoH * scale / vh
        val m = Matrix()
        m.setScale(sx, sy, vw / 2f, vh / 2f)
        v.setTransform(m)
    }

    private fun stopPlayer() {
        prepared = false
        player?.let {
            try { it.setOnErrorListener(null); it.release() } catch (_: Throwable) { }
        }
        player = null
    }

    fun release() {
        released = true
        stopPlayer()
        surface?.release()
        surface = null
        view?.surfaceTextureListener = null
    }
}