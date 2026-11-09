package com.znfdev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.core.util.DurationFormat
import com.znfdev.state.ZevController
import com.znfdev.state.bucketFor
import com.znfdev.state.LicenseBucket
import com.znfdev.ui.components.ConnectionChip
import com.znfdev.ui.components.ScreenHeader
import com.znfdev.ui.components.SmallPill
import com.znfdev.ui.components.ZevIcon
import com.znfdev.ui.theme.ZevColors

@Composable
fun SettingsScreen(c: ZevController) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Settings", subtitle = "BGFX ${c.versionText}")
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            SettingRow(
                icon = "bolt",
                title = "Auto execute",
                subtitle = "Saved to settings.json for BGFX to read"
            ) {
                Switch(
                    checked = c.autoExec,
                    onCheckedChange = { c.updateAutoExec(it) },
                    enabled = c.hasStorage
                )
            }
            SettingRow(
                icon = "star_rate",
                title = "Default script",
                subtitle = c.defaultScript.ifBlank { "None \u2014 star a script to set one" }
            )
            SettingRow(
                icon = if (c.hasStorage) "lock_open" else "lock",
                title = "Files access",
                subtitle = if (c.hasStorage) "Granted" else "Needed to read scripts and plugins",
                accent = if (c.hasStorage) ZevColors.Green else ZevColors.Danger
            ) {
                if (!c.hasStorage) SmallPill("Grant", { c.grantStorage() }, container = ZevColors.Primary, content = ZevColors.OnPrimary)
            }
            LicenseRow(c)
            SettingRow(
                icon = "integration_instructions",
                title = "API index",
                subtitle = c.globals?.let { "${it.size} globals for completion" } ?: "Not loaded"
            )
            SettingRow(
                icon = "dns",
                title = "Socket server",
                subtitle = "127.0.0.1:8085"
            ) {
                ConnectionChip(c.lspStatus)
            }
            SettingRow(
                icon = "update",
                title = "Version ${c.versionText}",
                subtitle = "Check the server for a newer build"
            ) {
                SmallPill("Check", { c.checkForUpdates() })
            }
        }
    }
}

@Composable
private fun LicenseRow(c: ZevController) {
    val lic = c.license
    val remainingMs = lic?.remainingMs() ?: 0L
    val bucket = if (lic == null) LicenseBucket.NORMAL else bucketFor(remainingMs)
    val accent = when (bucket) {
        LicenseBucket.NORMAL -> ZevColors.Green
        LicenseBucket.HOUR -> ZevColors.Gold
        LicenseBucket.MINUTES, LicenseBucket.EXPIRED -> ZevColors.Danger
    }
    val subtitle = when {
        lic == null -> "No key.json found in /BGFX"
        !lic.isVaild -> "${lic.key} \u00B7 marked invalid"
        else -> "${lic.key} \u00B7 ${DurationFormat.remaining(remainingMs)}"
    }
    SettingRow(
        icon = if (bucket == LicenseBucket.EXPIRED || (lic != null && !lic.isVaild)) "lock" else "badge",
        title = "License",
        subtitle = subtitle,
        accent = accent
    )
}

@Composable
private fun SettingRow(
    icon: String,
    title: String,
    subtitle: String,
    accent: Color = ZevColors.Primary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(ZevColors.Cell)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(ZevColors.PanelRaised),
            contentAlignment = Alignment.Center
        ) {
            ZevIcon(icon, size = 20.dp, tint = accent)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ZevColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(subtitle, color = ZevColors.TextDim, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
