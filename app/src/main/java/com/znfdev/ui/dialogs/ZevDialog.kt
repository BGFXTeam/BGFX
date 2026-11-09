package com.znfdev.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.znfdev.ui.components.AssetVideo
import com.znfdev.ui.components.GradientText
import com.znfdev.ui.components.PillButton
import com.znfdev.ui.markdown.MarkdownView
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevMotion
import com.znfdev.ui.theme.ZevShapes

/** Describes a dialog; build with [Dialogs] and show with [ZevDialog]. */
class DialogSpec(
    val title: String,
    val message: String,
    val markdown: Boolean = false,
    val positive: String? = null,
    val onPositive: (() -> Unit)? = null,
    /** false keeps the dialog open after the positive button (used by "Update"). */
    val dismissOnPositive: Boolean = true,
    val negative: String? = null,
    val onNegative: (() -> Unit)? = null,
    val cancelable: Boolean = true,
    val video: String? = null,
    val icon: String = "info",
    /**
     * true (default): the chosen clip loops forever — no random swapping.
     * false: a fresh random clip is picked every time the current one ends.
     * Has no effect when [video] points to a clip you intentionally want to loop;
     * in that case just leave this true.
     */
    val noRandomClip: Boolean = true
)

private val Clips: List<String> =
    listOf("znfdev.mp4", "the_king.78.mp4") + (1..15).map { "edit$it.mp4" }

private fun randomClip(): String = Clips.random()

/** A square card whose background is a looping video, with the message overlaid at the bottom. */
@Composable
fun ZevDialog(spec: DialogSpec, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = { if (spec.cancelable) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = spec.cancelable,
            dismissOnClickOutside = spec.cancelable,
            usePlatformDefaultWidth = false
        )
    ) {
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }

        AnimatedVisibility(
            visible = entered,
            enter = fadeIn(ZevMotion.enter(200)) + scaleIn(ZevMotion.enter(260), initialScale = 0.9f)
        ) {
            Box(
                Modifier
                    .padding(24.dp)
                    .widthIn(max = 340.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(ZevShapes.Large)
                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), ZevShapes.Large)
                    .background(Color.Black)
            ) {
                DialogVideo(spec, Modifier.matchParentSize())

                // only there so the text stays readable over the video
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xCC000000))
                    )
                )

                Column(
                    Modifier.fillMaxSize().padding(20.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    GradientText(
                        text = spec.title,
                        colors = listOf(Color.LightGray, Color.White),
                        style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                        if (spec.markdown) MarkdownView(spec.message, color = Color(0xFFCAC4D0))
                        else Text(spec.message, color = Color(0xFFCAC4D0), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (spec.negative != null) {
                            PillButton(
                                spec.negative,
                                onClick = {
                                    spec.onNegative?.invoke()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                container = ZevColors.PanelRaised
                            )
                        }
                        if (spec.positive != null) {
                            PillButton(
                                spec.positive,
                                onClick = {
                                    spec.onPositive?.invoke()
                                    if (spec.dismissOnPositive) onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                container = ZevColors.Primary,
                                content = ZevColors.OnPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogVideo(spec: DialogSpec, modifier: Modifier) {
    var clipName by remember { mutableStateOf(spec.video ?: randomClip()) }
    var failed by remember { mutableStateOf(false) }
    if (!failed) {
        AssetVideo(
            asset = clipName,
            looping = spec.noRandomClip,                       // native loop when no random swap
            muted = false,
            onFailed = { failed = true },
            onCompleted = {
                // Only fires when looping = false. Pick a fresh clip; if it happens to be
                // the same as the current one, the AssetVideo's setAsset short-circuits
                // and we'd stall, so guard against that.
                if (!spec.noRandomClip) {
                    var next = randomClip()
                    if (next == clipName) next = randomClip()
                    clipName = next
                }
            },
            modifier = modifier
        )
    }
}