package com.znfdev.ui.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.net.LspStatus
import com.znfdev.ui.components.ConnectionChip
import com.znfdev.ui.components.IconAction
import com.znfdev.ui.components.ZevIcon
import com.znfdev.ui.components.pressable
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevMotion
import com.znfdev.ui.theme.ZevShapes

private val ItemHeight = 46.dp
private val RailCollapsed = 64.dp
private val RailExpanded = 136.dp

/** Vertical tab rail. A highlight pill glides between tabs; the chevron widens it to show labels. */
@Composable
fun ZevRail(
    items: List<Category>,
    selected: Int,
    onSelect: (Int) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    lspStatus: LspStatus,
    modifier: Modifier = Modifier
) {
    val width by animateDpAsState(if (expanded) RailExpanded else RailCollapsed, ZevMotion.snappy<Dp>(), label = "railWidth")
    val indicatorY by animateDpAsState(ItemHeight * selected, ZevMotion.bouncy<Dp>(), label = "railIndicator")

    Column(
        modifier
            .width(width)
            .clip(ZevShapes.Panel)
            .background(ZevColors.Panel.copy(alpha = 0.92f))
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconAction(
            icon = if (expanded) "chevron_right" else "chevron_left",
            onClick = onToggle,
            tint = ZevColors.Primary,
            size = 36.dp
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp)
        ) {
            Box(
                Modifier
                    .offset(y = indicatorY + 3.dp)
                    .fillMaxWidth()
                    .height(ItemHeight - 6.dp)
                    .clip(ZevShapes.Pill)
                    .background(ZevColors.PrimaryContainer)
            )
            Column {
                items.forEachIndexed { index, cat ->
                    val on = index == selected
                    val tint = if (on) ZevColors.OnPrimaryContainer else ZevColors.TextDim
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(ItemHeight)
                            .pressable(ZevShapes.Pill, pressedScale = 0.94f) { onSelect(index) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZevIcon(cat.icon, size = 22.dp, tint = tint)
                        AnimatedVisibility(
                            visible = expanded,
                            enter = fadeIn(ZevMotion.enter(200, delay = 60)),
                            exit = fadeOut(ZevMotion.exit(90))
                        ) {
                            Text(
                                cat.label,
                                color = tint,
                                fontSize = 12.sp,
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(start = 10.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConnectionChip(lspStatus, compact = true)
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(ZevMotion.enter(200, delay = 60)),
                exit = fadeOut(ZevMotion.exit(90))
            ) {
                Text(
                    when (lspStatus) {
                        LspStatus.CONNECTED -> "Live"
                        LspStatus.CONNECTING -> "Connecting"
                        LspStatus.RETRYING -> "Retrying"
                        LspStatus.DISCONNECTED -> "Offline"
                    },
                    color = ZevColors.TextDim,
                    fontSize = 10.sp,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}
