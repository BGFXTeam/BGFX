package com.znfdev.ui.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.znfdev.state.EditorRequest
import com.znfdev.state.ZevController
import com.znfdev.ui.components.AssetVideo
import com.znfdev.ui.components.glassPanel
import com.znfdev.ui.editor.EditorSheet
import com.znfdev.ui.screens.ExecuteScreen
import com.znfdev.ui.screens.FilesScreen
import com.znfdev.ui.screens.PluginsScreen
import com.znfdev.ui.screens.ScriptsScreen
import com.znfdev.ui.screens.SettingsScreen
import com.znfdev.ui.screens.TeamScreen
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevMotion
import com.znfdev.ui.theme.ZevShapes

private val PanelFill = ZevColors.Panel.copy(alpha = 0.92f)

/**
 * The floating bubble — a 56dp circle that plays assets/logo.mp4 in a loop.
 *
 * Uses AssetVideo from components, which already handles the TextureView +
 * MediaPlayer lifecycle, center-crop, looping, mute, and lifecycle-pause.
 *
 * pressed drives the springy press-in scale (driven by the touch listener
 * on the host window in OverlayService — see bubbleTouchListener).
 *
 * onFailed/onRendered are no-ops here; the bubble just stays blank if the
 * asset is missing. Swap onFailed for a static fallback drawable if you want.
 */
@Composable
fun OverlayBubble(pressed: Boolean) {
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "bubblePress"
    )
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(8.dp, CircleShape)
            .clip(CircleShape)
    ) {
        AssetVideo(
            asset = "logo.mp4",
            modifier = Modifier.fillMaxSize(),
            looping = true,
            muted = true
            // The overlay service isn't tied to a foreground activity, so
            // hostStopped shouldn't pause the video. AssetVideo's lifecycle
            // observer listens to LocalLifecycleOwner — which is the service's
            // ServiceViewOwner (always RESUMED while the service runs), so the
            // video keeps playing while the user is in another app.
        )
    }
}

// ================================================================== panel

/**
 * The full-screen panel window's content: a scrim that dismisses on tap, with
 * the actual panel centered on top. Fades + scales in on first composition so
 * adding the window feels smooth.
 */
@Composable
fun OverlayPanel(c: ZevController, onClose: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }

    val alpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = ZevMotion.enter(220),
        label = "panelAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.88f,
        animationSpec = ZevMotion.enter(260),
        label = "panelScale"
    )

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Scrim — tap to close. Only consumes taps; swipes/scrolls in the panel
        // itself are handled by the panel's own pointerInput below.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(ZevColors.Scrim)
                .pointerInput(Unit) { detectTapGestures { onClose() } }
        )

        // Panel — centered, scaled+faded in.
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer { this.alpha = alpha; this.scaleX = scale; this.scaleY = scale }
        ) {
            Panel(c)
        }
    }
}

/**
 * The panel body: tab content on the left, the rail on the right, and the
 * editor sheet overlaying everything when open. Taps on the panel itself are
 * consumed so they don't fall through to the scrim (which would close it).
 */
@Composable
private fun Panel(c: ZevController) {
    var railExpanded by remember { mutableStateOf(false) }

    // keep the last editor around so the sheet can animate out after `editor` becomes null
    var lastEditor by remember { mutableStateOf<EditorRequest?>(null) }
    LaunchedEffect(c.editor) { if (c.editor != null) lastEditor = c.editor }
    val editorRequest = c.editor ?: lastEditor

    Box(
        Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 820.dp)
            .fillMaxHeight(0.88f)
            .heightIn(max = 430.dp)
            // taps on the panel itself must not fall through to the scrim
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .glassPanel(ZevShapes.Panel, PanelFill)
                    .padding(14.dp)
            ) {
                AnimatedContent(
                    targetState = c.tab,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(ZevMotion.enter(240, delay = 40)) { dir * 60 } +
                            fadeIn(ZevMotion.enter(220, delay = 40))) togetherWith
                            (slideOutHorizontally(ZevMotion.exit(150)) { -dir * 60 } + fadeOut(ZevMotion.exit(120)))
                    },
                    label = "tab",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        0 -> ExecuteScreen(c)
                        1 -> FilesScreen(c)
                        2 -> ScriptsScreen(c)
                        3 -> PluginsScreen(c)
                        4 -> TeamScreen(c)
                        else -> SettingsScreen(c)
                    }
                }
            }
            Spacer(Modifier.width(10.dp))
            ZevRail(
                items = PanelCategories,
                selected = c.tab,
                onSelect = { c.tab = it },
                expanded = railExpanded,
                onToggle = { railExpanded = !railExpanded },
                lspStatus = c.lspStatus,
                modifier = Modifier.fillMaxHeight()
            )
        }

        AnimatedVisibility(
            visible = c.editor != null,
            enter = fadeIn(ZevMotion.enter(200)) + scaleIn(ZevMotion.enter(240), initialScale = 0.94f),
            exit = fadeOut(ZevMotion.exit(140)) + scaleOut(ZevMotion.exit(160), targetScale = 0.96f)
        ) {
            val request = editorRequest
            if (request != null) {
                EditorSheet(
                    request = request,
                    symbols = c.symbols,
                    globals = c.globals,
                    lspStatus = c.lspStatus,
                    onClose = { c.closeEditor() }
                )
            }
        }
    }
}
