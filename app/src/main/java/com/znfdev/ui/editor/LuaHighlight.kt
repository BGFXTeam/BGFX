package com.znfdev.ui.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import com.znfdev.core.lua.LuaLexer
import com.znfdev.core.lua.TokenKind
import com.znfdev.ui.theme.ZevColors

/** Turns Lua source into a colored [AnnotatedString]. */
object LuaHighlight {

    private val styles: Map<TokenKind, SpanStyle> = mapOf(
        TokenKind.COMMENT to SpanStyle(color = ZevColors.CodeComment, fontStyle = FontStyle.Italic),
        TokenKind.STRING to SpanStyle(color = ZevColors.CodeString),
        TokenKind.NUMBER to SpanStyle(color = ZevColors.CodeNumber),
        TokenKind.KEYWORD to SpanStyle(color = ZevColors.CodeKeyword),
        TokenKind.SELF to SpanStyle(color = ZevColors.CodeSelf),
        TokenKind.GLOBAL to SpanStyle(color = ZevColors.CodeGlobal),
        TokenKind.CALL to SpanStyle(color = ZevColors.CodeCall)
    )

    fun annotate(text: String, globals: Set<String>?): AnnotatedString = buildAnnotatedString {
        append(text)
        for (t in LuaLexer.tokenize(text, globals)) {
            val style = styles[t.kind] ?: continue
            addStyle(style, t.start, t.end)
        }
    }

    fun colorOf(type: String): Color = when (type) {
        "function" -> ZevColors.CodeCall
        "table" -> ZevColors.CodeGlobal
        "keyword" -> ZevColors.CodeKeyword
        "string" -> ZevColors.CodeString
        "number" -> ZevColors.CodeNumber
        else -> ZevColors.CodeText
    }
}
