package com.znfdev.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.ui.components.PillButton
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevShapes

//enum class ServerStatus { Connecting, Online, Offline }

/** What the launcher screen shows; the Activity updates these as things finish loading. */
/*class HomeState {
    var server by mutableStateOf(ServerStatus.Connecting)
    var hasStorage by mutableStateOf(false)
    var hasOverlay by mutableStateOf(false)
    var version by mutableStateOf("")
    /** Drives the Start/Stop button: false → "Start", true → red "Stop". */
    var isServiceRunning by mutableStateOf(false)
}*/

/**
 * Minimal launcher: a small server-status badge and a single Start / Stop button.
 *
 * - Button reads "Start" (theme primary) while the overlay service is stopped.
 * - Flips to a red "Stop" while [HomeState.isServiceRunning] is true.
 *
 * Everything else that used to live here (title, subtitle, version line, hint,
 * credits tap-target) is gone — server data is still fetched by the activity;
 * only the resulting status dot is shown.
 */
@Composable
fun HomeScreen(
    state: HomeState,
    onToggleService: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(ZevColors.Background)
            .drawBehind {
                drawCircle(
                    Brush.radialGradient(
                        listOf(ZevColors.PrimaryContainer.copy(alpha = 0.55f), Color.Transparent),
                        center = Offset(size.width * 0.2f, size.height * 0.15f),
                        radius = size.minDimension * 0.9f
                    ),
                    radius = size.minDimension * 0.9f,
                    center = Offset(size.width * 0.2f, size.height * 0.15f)
                )
                drawCircle(
                    Brush.radialGradient(
                        listOf(ZevColors.Green.copy(alpha = 0.22f), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.9f),
                        radius = size.minDimension * 0.8f
                    ),
                    radius = size.minDimension * 0.8f,
                    center = Offset(size.width * 0.95f, size.height * 0.9f)
                )
            }
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(24.dp)
                .widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "BGFX",
                color = ZevColors.Text,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold
            )
        
            Spacer(Modifier.height(36.dp))
        
            val running = state.isServiceRunning
            PillButton(
                label = if (running) "Stop" else "Start",
                icon = if (running) "stop" else "play_arrow",
                onClick = onToggleService,
                container = if (running) StopRed else ZevColors.Primary,
                content = ZevColors.OnPrimary,
                height = 56.dp,
                modifier = Modifier.widthIn(min = 240.dp)
            )
        }
    }
}

@Composable
private fun ServerStatusBadge(status: ServerStatus) {
    val (dot, label) = when (status) {
        ServerStatus.Online -> ZevColors.Green to "Server online"
        ServerStatus.Offline -> StatusRed to "Server offline"
        ServerStatus.Connecting -> StatusAmber to "Connecting…"
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(ZevColors.PrimaryContainer, ZevShapes.Medium)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(dot, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = ZevColors.TextDim,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

// Off-theme colors for status dots / Stop button. Kept local so the launcher
// look doesn't depend on whatever else is in ZevColors.
private val StatusRed = Color(0xFFE53935)
private val StatusAmber = Color(0xFFFFA726)
private val StopRed = Color(0xFFE53935)
