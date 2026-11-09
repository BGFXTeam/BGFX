package com.znfdev.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.state.ZevController
import com.znfdev.ui.components.IconAction
import com.znfdev.ui.components.PillButton
import com.znfdev.ui.components.ScreenHeader
import com.znfdev.ui.components.ZevIcon
import com.znfdev.ui.components.pressable
import com.znfdev.ui.editor.LuaHighlight
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevShapes

@Composable
fun ExecuteScreen(c: ZevController) {
    val code = c.code
    val preview = remember(code, c.globals) { LuaHighlight.annotate(code.take(3000), c.globals) }
    val lines = remember(code) { code.count { it == '\n' } + 1 }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Execute", subtitle = "$lines lines \u00B7 ${code.length} chars") {
            IconAction("edit", { c.openExecuteEditor() })
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pressable(ZevShapes.Card, pressedScale = 0.985f) { c.openExecuteEditor() }
                .background(ZevColors.CellHigh)
        ) {
            Text(
                text = preview,
                color = ZevColors.CodeText,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                softWrap = false,
                overflow = TextOverflow.Clip,
                modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 40.dp, bottom = 12.dp)
            )
            ZevIcon(
                "edit",
                size = 16.dp,
                tint = ZevColors.TextDim,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
            )
        }

        Spacer(Modifier.height(10.dp))
        PillButton(
            label = "Run",
            icon = "play_arrow",
            onClick = { c.run() },
            modifier = Modifier.fillMaxWidth(),
            container = ZevColors.Primary,
            content = ZevColors.OnPrimary
        )
    }
}
