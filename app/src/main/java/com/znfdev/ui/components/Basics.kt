package com.znfdev.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.ui.theme.LocalIconFont
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevMotion
import com.znfdev.ui.theme.ZevShapes
import kotlin.math.min

// ------------------------------------------------------------------ icons

/** Material Symbols glyph by ligature name ("folder", "play_arrow", ...). */
@Composable
fun ZevIcon(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color = LocalContentColor.current
) {
    val font = LocalIconFont.current
    val fontSize = with(LocalDensity.current) { size.toSp() }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        if (font != null) {
            Text(
                text = name,
                color = tint,
                maxLines = 1,
                softWrap = false,
                style = TextStyle(
                    fontFamily = font,
                    fontSize = fontSize,
                    lineHeight = fontSize,
                    fontFeatureSettings = "'liga' 1"
                ),
                modifier = Modifier.wrapContentSize(unbounded = true)
            )
        }
    }
}

// ------------------------------------------------------------------ interaction

/**
 * Click target with a springy press-in, a ripple clipped to [shape], and no other
 * chrome. Use it instead of `clickable` on cards and pills so everything feels alike.
 */
fun Modifier.pressable(
    shape: Shape = ZevShapes.Card,
    enabled: Boolean = true,
    pressedScale: Float = 0.96f,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) pressedScale else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 650f),
        label = "press"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clip(shape)
        .clickable(
            interactionSource = source,
            indication = LocalIndication.current,
            enabled = enabled,
            onClick = onClick
        )
}

/** Staggered fade + rise used when a grid of cards first appears. */
fun Modifier.cascadeIn(
    index: Int,
    key: Any = Unit,
    stepMs: Int = 24,
    maxSteps: Int = 10,
    rise: Dp = 14.dp
): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        progress.snapTo(0f)
        progress.animateTo(
            1f,
            tween(durationMillis = 280, delayMillis = min(index, maxSteps) * stepMs, easing = ZevMotion.Decelerate)
        )
    }
    val risePx = with(LocalDensity.current) { rise.toPx() }
    graphicsLayer {
        val p = progress.value
        alpha = p
        translationY = (1f - p) * risePx
        val s = 0.94f + 0.06f * p
        scaleX = s
        scaleY = s
    }
}

/**
 * A soft, blur-free halo: a few translucent, ever-larger rings of [color] behind
 * whatever this modifier is applied to. Reads as a glow without an actual blur pass,
 * so it's cheap and looks the same on every API level.
 */
fun Modifier.glow(color: Color, radius: Dp = 18.dp): Modifier = drawBehind {
    val steps = 4
    for (i in steps downTo 1) {
        val t = i / steps.toFloat()
        drawRoundRect(
            color = color.copy(alpha = 0.10f * (1f - t) + 0.03f),
            topLeft = androidx.compose.ui.geometry.Offset(-radius.toPx() * t, -radius.toPx() * t),
            size = androidx.compose.ui.geometry.Size(
                size.width + radius.toPx() * 2 * t,
                size.height + radius.toPx() * 2 * t
            ),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2 + radius.toPx() * t)
        )
    }
}

/**
 * The "glass" treatment used on the panel and dialog cards: a faint top-to-bottom
 * gradient (brighter at the top, like light catching an edge) plus a 1px highlight
 * along the top border.
 */
fun Modifier.glassPanel(shape: Shape, base: Color): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            listOf(base.copy(alpha = (base.alpha + 0.06f).coerceAtMost(1f)), base)
        )
    )
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.14f), Color.Transparent)), shape)

// ------------------------------------------------------------------ buttons

@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    container: Color = ZevColors.Pill,
    content: Color = ZevColors.Text,
    height: Dp = 44.dp,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            //.then(if (enabled && container == ZevColors.Primary) Modifier.glow(ZevColors.Primary, radius = -14.dp) else Modifier)
            .height(height)
            .pressable(shape = ZevShapes.Pill, enabled = enabled, onClick = onClick)
            .background(if (enabled) container else container.copy(alpha = 0.4f))
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ZevIcon(icon, size = 20.dp, tint = content)
            Spacer(Modifier.width(8.dp))
        }
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Compact "Load"-style pill for use inside cards. */
@Composable
fun SmallPill(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = ZevColors.Pill,
    content: Color = ZevColors.Text
) {
    Box(
        modifier = modifier
            .height(28.dp)
            .pressable(shape = ZevShapes.Pill, pressedScale = 0.9f, onClick = onClick)
            .background(container)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = content, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun IconAction(
    icon: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = ZevColors.TextDim,
    size: Dp = 36.dp,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(size)
            .pressable(shape = CircleShape, enabled = enabled, pressedScale = 0.88f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        ZevIcon(icon, size = 20.dp, tint = if (enabled) tint else tint.copy(alpha = 0.35f))
    }
}

// ------------------------------------------------------------------ layout bits

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = ZevColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            if (subtitle != null) {
                Text(
                    subtitle,
                    color = ZevColors.TextDim,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        actions()
    }
}

@Composable
fun EmptyState(icon: String, message: String, modifier: Modifier = Modifier, hint: String? = null) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ZevIcon(icon, size = 36.dp, tint = ZevColors.Pill)
        Spacer(Modifier.height(8.dp))
        Text(message, color = ZevColors.TextDim, fontSize = 13.sp, textAlign = TextAlign.Center)
        if (hint != null) {
            Spacer(Modifier.height(2.dp))
            Text(hint, color = ZevColors.Pill, fontSize = 11.sp, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun StatusChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: String? = null,
    container: Color = ZevColors.PanelRaised,
    content: Color = ZevColors.Text
) {
    Row(
        modifier = modifier
            .clip(ZevShapes.Pill)
            .background(container)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 7.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            ZevIcon(icon, size = 16.dp, tint = content)
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = content, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

// ------------------------------------------------------------------ text

/** Text painted with a (optionally drifting) gradient. */
@OptIn(ExperimentalTextApi::class)
@Composable
fun GradientText(
    text: String,
    colors: List<Color>,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    animate: Boolean = false,
    durationMs: Int = 5000,
    maxLines: Int = Int.MAX_VALUE,
    textAlign: TextAlign? = null
) {
    var width by remember { mutableFloatStateOf(1f) }
    val transition = rememberInfiniteTransition(label = "gradient")
    val drift: State<Float> = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMs, easing = LinearEasing), RepeatMode.Restart),
        label = "drift"
    )
    val shift = if (animate) drift.value else 0f
    val brush = Brush.linearGradient(
        colors = colors,
        start = Offset(shift * width, 0f),
        end = Offset(shift * width + width, 0f),
        tileMode = TileMode.Mirror
    )
    Text(
        text = text,
        modifier = modifier,
        style = style.copy(brush = brush),
        maxLines = maxLines,
        textAlign = textAlign,
        onTextLayout = { width = it.size.width.toFloat().coerceAtLeast(1f) }
    )
}

// ------------------------------------------------------------------ Lua logo

/** The Lua moon-and-satellite mark, drawn so it stays crisp at any size. */
@Composable
fun LuaBadge(modifier: Modifier = Modifier, size: Dp = 40.dp, corner: Dp = 12.dp) {
    Canvas(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(ZevColors.LuaBlue)
    ) {
        val w = this.size.width
        val c = Offset(w * 0.47f, this.size.height * 0.53f)
        val r = w * 0.29f
        drawCircle(Color.White, radius = r, center = c)
        drawCircle(ZevColors.LuaBlue, radius = r * 0.56f, center = c)
        val moon = Offset(c.x + r * 0.95f, c.y - r * 0.95f)
        drawCircle(ZevColors.LuaBlue, radius = r * 0.36f, center = moon)
        drawCircle(Color.White, radius = r * 0.22f, center = moon)
    }
}

/** Small colored square with centered text, e.g. "TXT". */
@Composable
fun TextBadge(text: String, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp, corner: Dp = 12.dp) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = ZevColors.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
