package com.znfdev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.models.PluginItem
import com.znfdev.state.ZevController
import com.znfdev.ui.components.EmptyState
import com.znfdev.ui.components.IconAction
import com.znfdev.ui.components.ScreenHeader
import com.znfdev.ui.components.SmallPill
import com.znfdev.ui.components.ZevIcon
import com.znfdev.ui.components.cascadeIn
import com.znfdev.ui.components.pressable
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevShapes

@Composable
fun PluginsScreen(c: ZevController) {
    LaunchedEffect(c.hasStorage) { if (c.hasStorage) c.refreshPlugins() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Plugins", subtitle = if (c.hasStorage) "${c.plugins.size} in /BGFX/plugins" else null) {
            IconAction("refresh", { c.refreshPlugins() }, enabled = c.hasStorage)
        }
        when {
            !c.hasStorage -> NoAccess(c)
            c.plugins.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("extension", "No plugins found", hint = "Add a plugin .json with name and main")
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(230.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(c.plugins, key = { i, p -> "$i:${p.name}:${p.main}" }) { index, plugin ->
                    PluginCard(
                        plugin,
                        onLoad = { c.loadPlugin(plugin) },
                        modifier = Modifier.cascadeIn(index, key = c.pluginsRevision, stepMs = 24, maxSteps = 10)
                    )
                }
            }
        }
    }
}

@Composable
private fun PluginCard(plugin: PluginItem, onLoad: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .height(76.dp)
            .pressable(ZevShapes.Card, onClick = onLoad)
            .background(ZevColors.Cell)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(ZevColors.Green),
            contentAlignment = Alignment.Center
        ) {
            ZevIcon("extension", size = 22.dp, tint = ZevColors.Text)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                plugin.name ?: "Unnamed",
                color = ZevColors.Text,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                plugin.main ?: "",
                color = ZevColors.TextDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        SmallPill("Load", onLoad)
    }
}
