package com.znfdev.core.lua

/**
 * Smart-editing rules layered on top of a plain text field: auto-indent,
 * bracket/quote pairing, type-over of closers, and electric dedent.
 *
 * [smartEdit] looks at one raw edit (old text -> new text) and either returns a
 * corrected result or null when the edit should be accepted as typed.
 */
object LuaEditing {

    const val INDENT = 2

    fun smartEdit(old: String, new: String, newCursor: Int, indent: Int = INDENT): Edit? {
        // only a single character typed at the caret is interesting; anything else
        // (deletes, pastes, IME composition, autocorrect) is accepted as-is
        if (new.length != old.length + 1) return null
        val p = newCursor - 1
        if (p < 0 || p >= new.length) return null
        if (new.substring(0, p) + new.substring(p + 1) != old) return null
        val ch = new[p]

        // type-over: typing a closer that is already right there
        if ((ch == ')' || ch == ']' || ch == '}' || ch == '"' || ch == '\'') &&
            p < old.length && old[p] == ch
        ) {
            return Edit(old, p + 1)
        }

        if (ch == '\n') return onNewline(old, p, indent)

        // auto-pair
        val closer = when (ch) { '(' -> ')'; '[' -> ']'; '{' -> '}'; '"' -> '"'; '\'' -> '\''; else -> null }
        if (closer != null) {
            val prev = if (p > 0) old[p - 1] else ' '
            val next = if (p < old.length) old[p] else ' '
            val quote = ch == '"' || ch == '\''
            val okNext = !LuaLexer.isWordChar(next) && next != ch
            val okPrev = !quote || (!LuaLexer.isWordChar(prev) && prev != ch && prev != '\\')
            if (okNext && okPrev) {
                val text = old.substring(0, p) + ch + closer + old.substring(p)
                return Edit(text, p + 1)
            }
            return null
        }

        // electric dedent
        return maybeDedent(new, p + 1, ch, indent)
    }

    private fun onNewline(old: String, at: Int, indent: Int): Edit {
        var text = old
        var pos = at

        val lineStart = startOfLine(text, pos)
        val line = text.substring(lineStart, pos)
        var indentEnd = 0
        while (indentEnd < line.length && (line[indentEnd] == ' ' || line[indentEnd] == '\t')) indentEnd++
        var lead = line.substring(0, indentEnd)
        val body = line.substring(indentEnd).trim()

        // `else` typed on an auto-indented line and then Enter: pull it back one level.
        // Only when the line is not already shallower than the block body above it.
        if (body == "else" && lead.length >= indent && lead.length >= previousIndent(text, lineStart)) {
            text = text.substring(0, lineStart) + text.substring(lineStart + indent)
            pos -= indent
            lead = lead.substring(indent)
        }

        var insert = "\n" + lead
        if (opensBlock(body)) insert += " ".repeat(indent)

        // `{|}` / `(|)` pressed Enter between a pair: put the closer on its own line
        val next = if (pos < text.length) text[pos] else ' '
        val between = body.endsWith("{") && next == '}' || body.endsWith("(") && next == ')'
        return if (between) {
            val caret = pos + insert.length
            Edit(text.substring(0, pos) + insert + "\n" + lead + text.substring(pos), caret)
        } else {
            Edit(text.substring(0, pos) + insert + text.substring(pos), pos + insert.length)
        }
    }

    private fun maybeDedent(text: String, caret: Int, ch: Char, indent: Int): Edit? {
        val lineStart = startOfLine(text, caret)
        val line = text.substring(lineStart, caret)
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        if (caret < text.length && LuaLexer.isWordChar(text[caret])) return null

        val closer = trimmed == "end" || trimmed == "until" || trimmed == "}" ||
            (ch == ' ' && (trimmed == "else" || trimmed == "elseif"))
        if (!closer) return null

        var lead = 0
        while (lead < line.length && (line[lead] == ' ' || line[lead] == '\t')) lead++
        if (lead < indent) return null
        // only when the closer is the first thing on the line
        if (line.substring(lead) != trimmed && !(ch == ' ' && line.substring(lead).trimEnd() == trimmed)) return null

        val out = text.substring(0, lineStart) + text.substring(lineStart + indent)
        return Edit(out, caret - indent)
    }

    fun opensBlock(t: String): Boolean {
        if (t.endsWith("{") || t.endsWith("(")) return true
        if (t.contains("function") && t.endsWith(")")) return true
        for (opener in listOf("then", "do", "else", "elseif", "repeat")) {
            if (t.endsWith(opener) &&
                (t.length == opener.length || !LuaLexer.isWordChar(t[t.length - opener.length - 1]))
            ) return true
        }
        return false
    }

    /** Indent width of the closest non-blank line above the line starting at [lineStart]. */
    private fun previousIndent(text: String, lineStart: Int): Int {
        var end = lineStart - 1 // the '\n' that ends the previous line
        while (end >= 0) {
            val start = startOfLine(text, end)
            val line = text.substring(start, end)
            if (line.isNotBlank()) {
                var n = 0
                while (n < line.length && (line[n] == ' ' || line[n] == '\t')) n++
                return n
            }
            end = start - 1
        }
        return 0
    }

    private fun startOfLine(text: String, pos: Int): Int {
        var i = pos
        while (i > 0 && text[i - 1] != '\n') i--
        return i
    }
}
