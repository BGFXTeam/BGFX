package com.znfdev.core.lua

enum class TokenKind { COMMENT, STRING, NUMBER, KEYWORD, SELF, GLOBAL, CALL }

/** A highlighted range: [start, end). Tokens are sorted and never overlap. */
class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * Tiny Lua tokenizer used for syntax highlighting. Pure Kotlin (no Android or
 * Compose types) so it can be unit-tested on a plain JVM.
 */
object LuaLexer {

    val KEYWORDS: Set<String> = setOf(
        "and", "break", "do", "else", "elseif", "end", "false", "for", "function",
        "if", "in", "local", "nil", "not", "or", "repeat", "return", "then",
        "true", "until", "while"
    )

    fun tokenize(text: CharSequence, knownGlobals: Set<String>? = null): List<Token> {
        val out = ArrayList<Token>()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            if (c == '-' && i + 1 < n && text[i + 1] == '-') {
                val end = commentEnd(text, i)
                out.add(Token(i, end, TokenKind.COMMENT))
                i = end
            } else if (c == '"' || c == '\'') {
                val end = stringEnd(text, i, c)
                out.add(Token(i, end, TokenKind.STRING))
                i = end
            } else if (c == '[') {
                val level = longBracketLevel(text, i)
                if (level >= 0) {
                    val end = longBracketEnd(text, i, level)
                    out.add(Token(i, end, TokenKind.STRING))
                    i = end
                } else {
                    i++
                }
            } else if (c in '0'..'9') {
                val end = numberEnd(text, i)
                out.add(Token(i, end, TokenKind.NUMBER))
                i = end
            } else if (isIdentStart(c)) {
                var end = i + 1
                while (end < n && isWordChar(text[end])) end++
                val word = text.substring(i, end)
                val isMember = precededByAccessor(text, i)
                when {
                    word in KEYWORDS -> out.add(Token(i, end, TokenKind.KEYWORD))
                    word == "self" -> out.add(Token(i, end, TokenKind.SELF))
                    !isMember && knownGlobals != null && word in knownGlobals ->
                        out.add(Token(i, end, TokenKind.GLOBAL))
                    isCall(text, end) -> out.add(Token(i, end, TokenKind.CALL))
                }
                i = end
            } else {
                i++
            }
        }
        return out
    }

    // ------------------------------------------------------------ helpers

    fun isIdentStart(c: Char) = (c in 'a'..'z') || (c in 'A'..'Z') || c == '_'

    fun isWordChar(c: Char) = isIdentStart(c) || (c in '0'..'9')

    private fun precededByAccessor(s: CharSequence, identStart: Int): Boolean {
        var i = identStart - 1
        while (i >= 0 && (s[i] == ' ' || s[i] == '\t')) i--
        if (i < 0) return false
        // `a.b` and `a:b` are member accesses; `a..b` is concatenation
        if (s[i] == ':') return true
        return s[i] == '.' && !(i > 0 && s[i - 1] == '.')
    }

    private fun isCall(s: CharSequence, identEnd: Int): Boolean {
        var i = identEnd
        while (i < s.length && (s[i] == ' ' || s[i] == '\t')) i++
        return i < s.length && s[i] == '('
    }

    private fun commentEnd(s: CharSequence, start: Int): Int {
        val level = longBracketLevel(s, start + 2)
        if (level >= 0) return longBracketEnd(s, start + 2, level)
        var i = start + 2
        while (i < s.length && s[i] != '\n') i++
        return i
    }

    private fun stringEnd(s: CharSequence, start: Int, quote: Char): Int {
        val len = s.length
        var i = start + 1
        while (i < len) {
            val c = s[i]
            if (c == '\\') { i += 2; continue }
            if (c == quote) return i + 1
            if (c == '\n') return i // unterminated
            i++
        }
        return len
    }

    /** '=' count when a long bracket `[[`, `[=[` ... opens at [i], else -1. */
    private fun longBracketLevel(s: CharSequence, i: Int): Int {
        val len = s.length
        if (i < 0 || i >= len || s[i] != '[') return -1
        var j = i + 1
        var level = 0
        while (j < len && s[j] == '=') { level++; j++ }
        return if (j < len && s[j] == '[') level else -1
    }

    private fun longBracketEnd(s: CharSequence, openStart: Int, level: Int): Int {
        val len = s.length
        var i = openStart + level + 2
        while (i < len) {
            if (s[i] == ']') {
                var j = i + 1
                var k = 0
                while (k < level && j < len && s[j] == '=') { k++; j++ }
                if (k == level && j < len && s[j] == ']') return j + 1
            }
            i++
        }
        return len
    }

    private fun numberEnd(s: CharSequence, start: Int): Int {
        val len = s.length
        var i = start
        if (s[i] == '0' && i + 1 < len && (s[i + 1] == 'x' || s[i + 1] == 'X')) {
            i += 2
            while (i < len && isHex(s[i])) i++
            return i
        }
        while (i < len && s[i] in '0'..'9') i++
        if (i < len && s[i] == '.' && i + 1 < len && s[i + 1] in '0'..'9') {
            i++
            while (i < len && s[i] in '0'..'9') i++
        }
        if (i < len && (s[i] == 'e' || s[i] == 'E')) {
            var j = i + 1
            if (j < len && (s[j] == '+' || s[j] == '-')) j++
            if (j < len && s[j] in '0'..'9') {
                i = j
                while (i < len && s[i] in '0'..'9') i++
            }
        }
        return i
    }

    private fun isHex(c: Char) = (c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F')
}
