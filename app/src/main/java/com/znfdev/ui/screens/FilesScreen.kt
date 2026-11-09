package com.znfdev.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.state.FilesPage
import com.znfdev.state.ZevController
import com.znfdev.ui.components.EmptyState
import com.znfdev.ui.components.IconAction
import com.znfdev.ui.components.LuaBadge
import com.znfdev.ui.components.ScreenHeader
import com.znfdev.ui.components.SmallPill
import com.znfdev.ui.components.ZevIcon
import com.znfdev.ui.components.cascadeIn
import com.znfdev.ui.components.pressable
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevMotion
import com.znfdev.ui.theme.ZevShapes
import java.io.File

@Composable
fun FilesScreen(c: ZevController) {
    LaunchedEffect(c.hasStorage) { if (c.hasStorage) c.refreshFiles() }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(
            "Files",
            subtitle = c.filesPage.path.removePrefix("/storage/emulated/0/").trimEnd('/')
        ) {
            IconAction("arrow_upward", { c.goUp() }, enabled = c.canGoUp)
            IconAction("refresh", { c.refreshFiles() }, enabled = c.hasStorage)
        }

        if (!c.hasStorage) {
            NoAccess(c)
        } else {
            AnimatedContent(
                targetState = c.filesPage,
                contentKey = { it.path },
                transitionSpec = {
                    val dir = c.filesDirection
                    (slideInHorizontally(ZevMotion.enter(240)) { dir * (it / 6) } + fadeIn(ZevMotion.enter(240))) togetherWith
                        (slideOutHorizontally(ZevMotion.exit(160)) { -dir * (it / 6) } + fadeOut(ZevMotion.exit(160)))
                },
                label = "files",
                modifier = Modifier.fillMaxSize()
            ) { page ->
                FilesGrid(c, page)
            }
        }
    }
}

@Composable
private fun FilesGrid(c: ZevController, page: FilesPage) {
    if (page.entries.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState("folder_off", "Nothing here")
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(104.dp),
        contentPadding = PaddingValues(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(page.entries, key = { _, f -> f.path }) { index, file ->
            FileCard(
                file,
                onOpen = { c.openDirectory(file) },
                onLoad = { c.selectFile(file) },
                modifier = Modifier.cascadeIn(index, key = c.filesRevision, stepMs = 18, maxSteps = 14, rise = 10.dp)
            )
        }
    }
}

private fun iconFor(file: File): String {
    if (file.isDirectory) return "folder"
    return when (file.extension.lowercase()) {
        "txt" -> "article"
        "json" -> "data_object"
        else -> "description"
    }
}

@Composable
private fun FileCard(file: File, onOpen: () -> Unit, onLoad: () -> Unit, modifier: Modifier = Modifier) {
    val isDir = file.isDirectory
    val isLua = !isDir && file.extension.equals("lua", ignoreCase = true)
    val isScript = !isDir && file.extension.equals("lua", ignoreCase = true) || file.extension.equals("txt", ignoreCase = true)
    val base = modifier
        .fillMaxWidth()
        .height(if (isDir) 96.dp else 128.dp)
    Column(
        (if (isDir) base.pressable(ZevShapes.Card, onClick = onOpen) else base.clip(ZevShapes.Card))
            .background(ZevColors.CellHigh)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isLua) {
            LuaBadge(size = 36.dp, corner = 12.dp)
        } else {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDir) Color(0xFF54505C) else ZevColors.PanelRaised),
                contentAlignment = Alignment.Center
            ) {
                ZevIcon(iconFor(file), size = 20.dp, tint = if (isDir) ZevColors.Primary else ZevColors.Text)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            file.name,
            color = ZevColors.Text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        if (isScript) {
            Text(formatSize(file.length()), color = ZevColors.TextDim, fontSize = 9.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            SmallPill("Load", onLoad)
        }
    }
}
