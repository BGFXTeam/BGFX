package com.znfdev.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.state.TeamMember
import com.znfdev.state.ZevController
import com.znfdev.ui.components.AssetVideo
import com.znfdev.ui.components.EmptyState
import com.znfdev.ui.components.ScreenHeader
import com.znfdev.ui.components.ZevIcon
import com.znfdev.ui.components.cascadeIn
import com.znfdev.ui.components.pressable
import com.znfdev.ui.components.rememberRemoteImage
import com.znfdev.ui.theme.ZevColors
import androidx.compose.ui.text.TextStyle
import com.znfdev.ui.components.GradientTextT

@Composable
fun TeamScreen(c: ZevController) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Credits", subtitle = "The people behind BGFX")
        if (c.members.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("groups", "No one here yet")
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(c.members, key = { i, m -> "$i:${m.name}" }) { index, member ->
                    MemberCard(member, Modifier.cascadeIn(index, stepMs = 40, maxSteps = 6))
                }
            }
        }
    }
}

private fun tintOf(m: TeamMember): Color {
    m.color?.let { return Color(it.toInt()) }
    val hue = ((m.name.hashCode() and 0x7FFFFFFF) % 360).toFloat()
    return Color.hsv(hue, 0.45f, 0.55f)
}

/**
 * A full-bleed card: profile picture and looping video fill it, and a tinted band with a
 * "/" slanted top edge fades into them and carries the name and role.
 */
@Composable
private fun MemberCard(member: TeamMember, modifier: Modifier = Modifier) {
    val tint = remember(member) { tintOf(member) }
    val onTint = if (tint.luminance() > 0.5f) Color(0xFF14121A) else Color.White
    
    // ✅ WE CREATE THE COLORS AT RUNTIME HERE
    // We use remember(tint, onTint) so it generates this list once per member.
    // It sweeps: Readable Color -> Tint Color -> Readable Color (seamless loop)
    val runtimeGradient = remember(tint, onTint) {
        listOf(onTint, tint, onTint)
    }

    val picture by rememberRemoteImage(member.picUrl)
    val picAlpha by animateFloatAsState(if (picture != null) 1f else 0f, tween(220), label = "pic")

    var videoFailed by remember(member) { mutableStateOf(false) }
    var videoRendered by remember(member) { mutableStateOf(false) }
    var muted by remember(member) { mutableStateOf(true) }
    val videoAlpha by animateFloatAsState(if (videoRendered) 1f else 0f, tween(260), label = "video")
    val hasVideo = !member.videoAsset.isNullOrBlank() && !videoFailed

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(0.82f)
            .pressable(com.znfdev.ui.theme.ZevShapes.Large, pressedScale = 0.98f) { if (hasVideo && videoRendered) muted = !muted }
            .background(tint.copy(alpha = 0.35f))
    ) {
        // initials until a picture or the video arrives
        if (picture == null && !videoRendered) {
            Text(
                member.name.take(2).uppercase(),
                color = tint.copy(alpha = 0.9f),
                fontSize = 40.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        picture?.let {
            Image(
                bitmap = it,
                contentDescription = member.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = picAlpha }
            )
        }
        if (hasVideo) {
            AssetVideo(
                asset = member.videoAsset!!,
                muted = muted,
                onFailed = { videoFailed = true },
                onRendered = { videoRendered = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = videoAlpha }
            )
        }

        // "/" band: tint is solid below the slash and dissolves upward along the
        // perpendicular, so the colour feathers into the video instead of cutting hard.
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val yLeft = h * 0.64f       // slash, left end (lower)
            val yRight = h * 0.50f      // slash, right end (higher)
            val fade = h * 0.22f        // how far the blend reaches up from the slash

            val dy = yRight - yLeft
            val len = kotlin.math.sqrt(w * w + dy * dy)
            val nx = -dy / len          // unit normal of the slash, pointing down
            val ny = w / len
            val mid = Offset(w / 2f, (yLeft + yRight) / 2f)

            drawRect(
                Brush.linearGradient(
                    0f to tint.copy(alpha = 0f),
                    1f to tint,
                    start = Offset(mid.x - nx * fade, mid.y - ny * fade),
                    end = mid
                )
            )
        }
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
        ) {
            GradientTextT(
                text = member.name,
                colors = runtimeGradient,
                style = TextStyle(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Text(member.role, color = onTint.copy(alpha = 0.8f), fontSize = 11.sp, maxLines = 1)
            if (!member.note.isNullOrBlank()) {
                Text(
                    member.note,
                    color = onTint.copy(alpha = 0.65f),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (hasVideo && videoRendered) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color(0x66000000))
                    .padding(6.dp)
            ) {
                ZevIcon(if (muted) "volume_off" else "volume_up", size = 16.dp, tint = Color.White)
            }
        }
    }
}