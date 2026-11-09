package com.znfdev.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.core.lua.SymbolSource
import com.znfdev.net.LspStatus
import com.znfdev.state.EditorRequest
import com.znfdev.ui.components.ConnectionChip
import com.znfdev.ui.components.IconAction
import com.znfdev.ui.components.PillButton
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevShapes

/** Full-panel editor: title bar with undo/redo/close, the editor, and a Save button. */
@Composable
fun EditorSheet(
    request: EditorRequest,
    symbols: SymbolSource?,
    globals: Set<String>?,
    lspStatus: LspStatus,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state = remember(request) { CodeEditorState(request.initialCode) }

    Column(
        modifier
            .fillMaxSize()
            // swallow taps so they don't reach whatever is behind the sheet
            .pointerInput(Unit) { detectTapGestures { } }
            .clip(ZevShapes.Panel)
            .background(ZevColors.Panel)
            .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        request.title,
                        color = ZevColors.Text,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (state.isDirty) {
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.size(7.dp).background(ZevColors.Gold, CircleShape))
                    }
                }
                if (request.subtitle != null) {
                    Text(request.subtitle, color = ZevColors.TextDim, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            ConnectionChip(lspStatus, compact = true, modifier = Modifier.padding(end = 4.dp))
            IconAction("undo", { state.undo() }, enabled = state.canUndo)
            IconAction("redo", { state.redo() }, enabled = state.canRedo)
            IconAction("close", onClose)
        }

        CodeEditor(
            state = state,
            symbols = symbols,
            globals = globals,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(ZevShapes.Medium)
                .background(ZevColors.Editor)
        )

        PillButton(
            label = "Save",
            icon = "save",
            onClick = {
                request.onSave(state.text)
                onClose()
            },
            modifier = Modifier.fillMaxWidth(),
            container = ZevColors.Primary,
            content = ZevColors.OnPrimary
        )
    }
}
