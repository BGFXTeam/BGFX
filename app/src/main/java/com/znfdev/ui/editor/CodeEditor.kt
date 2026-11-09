package com.znfdev.ui.editor

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.znfdev.core.lua.Completion
import com.znfdev.core.lua.Edit
import com.znfdev.core.lua.EditHistory
import com.znfdev.core.lua.LuaCompletion
import com.znfdev.core.lua.LuaEditing
import com.znfdev.core.lua.Sym
import com.znfdev.core.lua.SymbolSource
import com.znfdev.ui.components.IconAction
import com.znfdev.ui.components.pressable
import com.znfdev.ui.theme.ZevColors
import com.znfdev.ui.theme.ZevMotion
import com.znfdev.ui.theme.ZevShapes

/** Text + caret + undo history for one editing session. */
@Stable
class CodeEditorState(val initial: String) {

    val fieldState = TextFieldState(initial)

    private val history = EditHistory()
    private var tick by mutableIntStateOf(0)

    val text: String
        get() = fieldState.text.toString()

    val selection: TextRange
        get() = TextRange(
            fieldState.selection.start,
            fieldState.selection.end
        )

    val canUndo: Boolean
        get() = tick >= 0 && history.canUndo

    val canRedo: Boolean
        get() = tick >= 0 && history.canRedo

    val isDirty: Boolean
        get() = text != initial

    fun insert(s: String, caretBack: Int = 0) {
        val start = fieldState.selection.min
        val end = fieldState.selection.max

        fieldState.edit {
            replace(start, end, s)
            placeCursorBeforeCharAt(start + s.length - caretBack)
        }
    }

    fun complete(c: Completion, item: Sym) {
        val e = LuaCompletion.apply(
            text,
            fieldState.selection.start,
            c.replaceStart,
            item
        )

        fieldState.edit {
            replace(0, length, e.text)
            placeCursorBeforeCharAt(e.cursor)
        }
    }

    fun undo() {
        val e = history.undo(
            Edit(text, fieldState.selection.start)
        ) ?: return

        fieldState.edit {
            replace(0, length, e.text)
            placeCursorBeforeCharAt(e.cursor)
        }

        tick++
    }

    fun redo() {
        val e = history.redo(
            Edit(text, fieldState.selection.start)
        ) ?: return

        fieldState.edit {
            replace(0, length, e.text)
            placeCursorBeforeCharAt(e.cursor)
        }

        tick++
    }
}

private val EditorTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 13.sp,
    lineHeight = 19.sp,
    color = ZevColors.CodeText
)

/** Re-highlights only when the text actually changed (selection moves also call filter). */
private class HighlightTransformation(
    private val globals: Set<String>?
) : OutputTransformation {
    private var lastText: String? = null
    private var lastOut: AnnotatedString? = null

    override fun TextFieldBuffer.transformOutput() {
        val src = toString()
        val cached = lastOut
        val out = if (src == lastText && cached != null) {
            cached
        } else {
            LuaHighlight.annotate(src, globals).also {
                lastText = src
                lastOut = it
            }
        }
    
        for (span in out.spanStyles) {
            addStyle(span.item, span.start, span.end)
        }
    }
}

private class SymbolKey(val label: String, val insert: String, val caretBack: Int = 0)

private val SymbolKeys = listOf(
    SymbolKey("Tab", "  "),
    SymbolKey("( )", "()", 1),
    SymbolKey("[ ]", "[]", 1),
    SymbolKey("{ }", "{}", 1),
    SymbolKey("\" \"", "\"\"", 1),
    SymbolKey("'", "''", 1),
    SymbolKey(".", "."),
    SymbolKey(":", ":"),
    SymbolKey(",", ","),
    SymbolKey("=", " = "),
    SymbolKey("==", " == "),
    SymbolKey("~=", " ~= "),
    SymbolKey("..", " .. "),
    SymbolKey("#", "#"),
    SymbolKey("end", "end"),
    SymbolKey("then", " then"),
    SymbolKey("local", "local ")
)

/** Syntax-highlighted, line-numbered Lua editor with completion chips and a symbol row. */
@Composable
fun CodeEditor(
    state: CodeEditorState,
    modifier: Modifier = Modifier,
    symbols: SymbolSource? = null,
    globals: Set<String>? = null
) {
    val text = state.text
    val selection = state.fieldState.selection

    val transformation = remember(globals) {
        HighlightTransformation(globals)
    }

    val lineCount = remember(text) {
        text.count { it == '\n' } + 1
    }

    val gutterText = remember(lineCount) {
        (1..lineCount).joinToString("\n")
    }

    // a RemoteSymbolSource bumps this once an in-flight LSP request lands, so a completion
    // that was computed from stale/empty cache gets recomputed without needing a keystroke
    val remoteVersion = (symbols as? com.znfdev.state.RemoteSymbolSource)?.version?.value
    val completion: Completion? = remember(text, selection, symbols, remoteVersion) {
        if (selection.collapsed) {
            LuaCompletion.compute(
                text,
                selection.start,
                symbols
            )
        } else {
            null
        }
    }
    var dismissedAt by remember { mutableStateOf<String?>(null) }
    val showChips = completion != null && dismissedAt != text

    Column(modifier) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val viewportH = maxHeight
            val gutterW = (maxOf(2, lineCount.toString().length) * 8 + 18).dp
            val fieldMinW = maxWidth - gutterW - 12.dp

            Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    text = gutterText,
                    style = EditorTextStyle.copy(color = ZevColors.CodeGutter),
                    textAlign = TextAlign.End,
                    softWrap = false,
                    modifier = Modifier
                        .width(gutterW)
                        .heightIn(min = viewportH)
                        .background(ZevColors.Editor)
                        .padding(start = 4.dp, end = 8.dp, top = 10.dp)
                )
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    BasicTextField(
                        state = state.fieldState,
                        textStyle = EditorTextStyle,
                        cursorBrush = SolidColor(ZevColors.Primary),
                        inputTransformation = null,
                        outputTransformation = transformation,
                        lineLimits = TextFieldLineLimits.MultiLine(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false
                        ),
                        modifier = Modifier
                            .widthIn(min = fieldMinW)
                            .heightIn(min = viewportH)
                            .padding(
                                start = 8.dp,
                                end = 12.dp,
                                top = 10.dp,
                                bottom = 10.dp
                            )
                            .onPreviewKeyEvent { e ->
                                if (e.type == KeyEventType.KeyDown && e.key == Key.Tab) {
                                    state.insert("  ")
                                    true
                                } else {
                                    false
                                }
                            }
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showChips,
            enter = fadeIn(ZevMotion.enter(160)) + slideInVertically(ZevMotion.enter(200)) { it / 2 },
            exit = fadeOut(ZevMotion.exit(120)) + slideOutVertically(ZevMotion.exit(120)) { it / 2 }
        ) {
            // keep showing the last suggestions while the strip animates out
            val c = completion
            if (c != null) SuggestionStrip(c, onPick = { state.complete(c, it) }, onDismiss = { dismissedAt = state.text })
        }

        SymbolBar(onKey = { state.insert(it.insert, it.caretBack) })
    }
}

@Composable
private fun SuggestionStrip(completion: Completion, onPick: (Sym) -> Unit, onDismiss: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ZevColors.PanelRaised)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconAction("close", onDismiss, size = 32.dp)
        Row(
            Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (item in completion.items.take(40)) {
                Row(
                    Modifier
                        .padding(end = 6.dp)
                        .pressable(ZevShapes.Pill) { onPick(item) }
                        .background(ZevColors.Cell)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(LuaHighlight.colorOf(item.type), ZevShapes.Pill)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.name,
                        color = ZevColors.Text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    if (item.isTable && item.memberCount > 0) {
                        Text(
                            "  ${item.memberCount}",
                            color = ZevColors.TextDim,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolBar(onKey: (SymbolKey) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(ZevColors.Panel)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 5.dp)
    ) {
        for (k in SymbolKeys) {
            Box(
                Modifier
                    .padding(end = 5.dp)
                    .pressable(ZevShapes.Small, pressedScale = 0.9f) { onKey(k) }
                    .background(ZevColors.Cell)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    k.label,
                    color = ZevColors.Text,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp
                )
            }
        }
    }
}