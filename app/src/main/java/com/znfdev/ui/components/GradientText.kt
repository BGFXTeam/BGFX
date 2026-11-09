package com.znfdev.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import android.graphics.Shader

@Composable
fun GradientTextT(
    text: String,
    colors: List<Color>? = null,
    style: TextStyle = TextStyle(),
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val gradientColors = colors ?: listOf(
        Color(0xFF0D47A1),
        Color(0xFF1976D2),
        Color(0xFF42A5F5),
        Color(0xFF90CAF9),
        Color(0xFF42A5F5),
        Color(0xFF1976D2),
        Color(0xFF0D47A1)
    )

    val transition = rememberInfiniteTransition(label = "gradient")
    val progress = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            // 👈 ADD LinearEasing HERE to remove the pause/stutter
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    val brush = remember(gradientColors) {
        AnimatedLinearGradientBrush(
            colors = gradientColors,
            progress = { progress.value }
        )
    }

    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(brush = brush),
        maxLines = maxLines,
        overflow = overflow
    )
}

private class AnimatedLinearGradientBrush(
    private val colors: List<Color>,
    private val progress: () -> Float,
    private val period: Float = 0.8f
) : ShaderBrush() {
    override fun createShader(size: Size): Shader {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val shift = progress() * period

        val start = Offset(w * (1f - shift),            h * (1f - shift))
        val end   = Offset(w * (1f - shift - period),   h * (1f - shift - period))

        return LinearGradientShader(
            from = start,
            to = end,
            colors = colors,
            colorStops = null,
            tileMode = TileMode.Repeated
        )
    }
}