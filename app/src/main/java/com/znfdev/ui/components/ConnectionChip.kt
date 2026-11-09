package com.znfdev.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.net.LspStatus
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevShapes

private fun toneFor(status: LspStatus): Pair<Color, String> = when (status) {
    LspStatus.CONNECTED -> ZevColors.Green to "LSP live"
    LspStatus.CONNECTING -> ZevColors.Gold to "Connecting\u2026"
    LspStatus.RETRYING -> ZevColors.Gold to "Retrying\u2026"
    LspStatus.DISCONNECTED -> ZevColors.TextDim to "Offline"
}

/** Small pulsing dot + label reflecting the LSP socket's state. [compact] drops the label. */
@Composable
fun ConnectionChip(status: LspStatus, modifier: Modifier = Modifier, compact: Boolean = false) {
    val (tone, label) = toneFor(status)
    val pulsing = status == LspStatus.CONNECTING || status == LspStatus.RETRYING

    if (compact) {
        PulsingDot(tone, pulsing, modifier)
        return
    }

    Row(
        modifier
            .clip(ZevShapes.Pill)
            .background(ZevColors.PanelRaised)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PulsingDot(tone, pulsing)
        Text(label, color = ZevColors.Text, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
private fun PulsingDot(tone: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (pulsing) 1.8f else 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "dotScale"
    )
    Canvas(modifier.size(9.dp)) {
        if (pulsing) drawCircle(tone.copy(alpha = 0.25f), radius = size.minDimension / 2 * scale)
        drawCircle(tone, radius = size.minDimension / 2.6f)
    }
}
