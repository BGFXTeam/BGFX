package com.znfdev.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.state.ZevController
import com.znfdev.ui.components.EmptyState
import com.znfdev.ui.components.IconAction
import com.znfdev.ui.components.LuaBadge
import com.znfdev.ui.components.ScreenHeader
import com.znfdev.ui.components.TextBadge
import com.znfdev.ui.components.cascadeIn
import com.znfdev.ui.components.pressable
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevMotion
import com.znfdev.ui.theme.ZevShapes
import java.io.File

@Composable
fun ScriptsScreen(c: ZevController) {
    LaunchedEffect(c.hasStorage) { if (c.hasStorage) c.refreshScripts() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Scripts", subtitle = if (c.hasStorage) "${c.scripts.size} in /BGFX/Scripts" else null) {
            IconAction("refresh", { c.refreshScripts() }, enabled = c.hasStorage)
        }
        when {
            !c.hasStorage -> NoAccess(c)
            c.scripts.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState("script", "No scripts found", hint = "Drop .lua or .txt files into /BGFX/Scripts")
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(128.dp),
                contentPadding = PaddingValues(bottom = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(c.scripts, key = { _, f -> f.path }) { index, file ->
                    ScriptCard(
                        file = file,
                        isDefault = file.name.equals(c.defaultScript, ignoreCase = true),
                        onOpen = { c.openScript(file) },
                        onStar = { c.updateDefaultScript(file.name) },
                        modifier = Modifier.cascadeIn(index, key = c.scriptsRevision, stepMs = 20, maxSteps = 12)
                    )
                }
            }
        }
    }
}

@Composable
private fun ScriptCard(
    file: File,
    isDefault: Boolean,
    onOpen: () -> Unit,
    onStar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLua = file.name.endsWith(".lua", ignoreCase = true)
    Box(
        modifier
            .fillMaxWidth()
            .height(128.dp)
            .pressable(ZevShapes.Card, onClick = onOpen)
            .background(ZevColors.Cell)
            .then(if (isDefault) Modifier.border(1.5.dp, ZevColors.Gold.copy(alpha = 0.7f), ZevShapes.Card) else Modifier)
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLua) LuaBadge(size = 42.dp, corner = 14.dp)
            else TextBadge("TXT", ZevColors.Pill, size = 42.dp, corner = 14.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                file.name,
                color = ZevColors.Text,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
            Text(
                (if (isLua) "Lua" else "Text") + "  \u2022  " + formatSize(file.length()),
                color = ZevColors.TextDim,
                fontSize = 10.sp,
                maxLines = 1
            )
        }
        StarButton(isDefault, onStar, Modifier.align(Alignment.TopEnd).padding(4.dp))
    }
}

@Composable
private fun StarButton(on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scale by animateFloatAsState(if (on) 1.15f else 1f, ZevMotion.bouncy(), label = "star")
    Box(
        modifier
            .size(30.dp)
            .pressable(CircleShape, pressedScale = 0.85f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (on) "\u2605" else "\u2606",
            color = if (on) ZevColors.Gold else StarOff,
            fontSize = 18.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
        )
    }
}

private val StarOff = androidx.compose.ui.graphics.Color(0xFF6E6873)
