package com.znfdev.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.znfdev.state.ZevController
import com.znfdev.ui.components.EmptyState
import com.znfdev.ui.components.SmallPill
import com.znfdev.ui.theme.ZevColors
import java.util.Locale

/** Shown by every screen that reads /BGFX until the user grants file access. */
@Composable
fun NoAccess(c: ZevController, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            EmptyState(
                icon = "lock",
                message = "Storage access needed",
                hint = "BGFX keeps scripts and plugins in /BGFX"
            )
            Spacer(Modifier.height(12.dp))
            SmallPill("Grant access", { c.grantStorage() }, container = ZevColors.Primary, content = ZevColors.OnPrimary)
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024f)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
}
