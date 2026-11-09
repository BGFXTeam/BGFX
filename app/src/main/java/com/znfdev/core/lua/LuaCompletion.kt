package com.znfdev.core.lua

/** One completion candidate. [type] is the API dump type ("function", "table", ...) or "keyword". */
data class Sym(val name: String, val type: String, val memberCount: Int = 0, val value: String? = null) {
    val isFunction get() = type == "function"
    val isTable get() = type == "table"
    val isKeyword get() = type == "keyword"
}

/** Where completions come from. The app adapts `LuaApiIndex` to this. */
interface SymbolSource {
    fun completeGlobals(prefix: String): List<Sym>
    fun membersForCompletion(path: String): List<Sym>
}

data class Completion(val replaceStart: Int, val items: List<Sym>)

/** Result of a text edit: the new text and where the caret goes. */
data class Edit(val text: String, val cursor: Int)

/**
 * Completion engine: figures out what the caret is sitting on
 * (`Blockman.Instance():getPlayer():|`), resolves `local x = ...` chains, and asks
 * the API index for members.
 */
object LuaCompletion {

    private const val MAX_WORD = 32

    fun compute(
        text: CharSequence,
        cursor: Int,
        api: SymbolSource?,
        suggestKeywords: Boolean = true,
        minWordLength: Int = 2
    ): Completion? {
        if (cursor < 0 || cursor > text.length) return null

        var wordStart = cursor
        while (wordStart > 0 && LuaLexer.isWordChar(text[wordStart - 1])) wordStart--
        val word = text.substring(wordStart, cursor)
        if (word.length > MAX_WORD) return null

        var qualifier: String? = null
        if (wordStart > 0) {
            val sep = text[wordStart - 1]
            if (sep == '.' || sep == ':') qualifier = parseQualifier(text, wordStart - 1)
        }

        if (qualifier != null && !qualifier.contains('.') && api != null) {
            val resolved = resolveLocalChain(text, cursor, qualifier, 0)
            if (!resolved.isNullOrEmpty() && resolved != qualifier) qualifier = resolved
        }

        val results = ArrayList<Sym>()
        if (qualifier != null) {
            if (api != null && qualifier.isNotEmpty()) {
                for (m in api.membersForCompletion(qualifier)) {
                    if (m.name.startsWith(word, ignoreCase = true)) results.add(m)
                }
            }
        } else if (word.length >= minWordLength) {
            if (api != null) results.addAll(api.completeGlobals(word))
            if (suggestKeywords) {
                for (k in LuaLexer.KEYWORDS) if (k.startsWith(word)) results.add(Sym(k, "keyword"))
                if ("self".startsWith(word)) results.add(Sym("self", "keyword"))
            }
        }

        if (results.isEmpty()) return null
        // nothing to offer when the only candidate is what's already typed
        if (results.size == 1 && results[0].name == word) return null

        results.sortWith(compareBy<Sym> { it.isKeyword }.thenBy { it.name })
        return Completion(wordStart, results)
    }

    /** Replaces text[replaceStart, cursor) with the chosen symbol. Functions get `()`. */
    fun apply(text: String, cursor: Int, replaceStart: Int, item: Sym): Edit {
        val start = replaceStart.coerceIn(0, text.length)
        val end = cursor.coerceIn(start, text.length)
        val nextIsParen = end < text.length && text[end] == '('
        val insert: String
        val caretOffset: Int
        if (item.isFunction && !nextIsParen) {
            insert = item.name + "()"
            caretOffset = item.name.length + 1
        } else {
            insert = item.name
            caretOffset = insert.length
        }
        return Edit(text.substring(0, start) + insert + text.substring(end), start + caretOffset)
    }

    // ------------------------------------------------- qualifier parsing

    /**
     * Turns the expression left of a `.` / `:` into a dotted chain:
     * `Blockman.Instance():getPlayer()` -> `Blockman.Instance.getPlayer`.
     * Returns "" when the chain can't be resolved.
     */
    fun parseQualifier(text: CharSequence, dotPos: Int): String {
        val parts = ArrayList<String>()
        var p = dotPos
        while (p > 0) {
            while (p > 0 && isSpace(text[p - 1])) p--
            if (p == 0) break

            val c = text[p - 1]
            if (c == ')' || c == ']' || c == '}') {
                val open = findMatchingOpen(text, p - 1, c)
                if (open < 0) return chainOrEmpty(parts)
                p = open
                continue
            }
            if (!LuaLexer.isWordChar(c)) return chainOrEmpty(parts)

            val end = p
            var start = end
            while (start > 0 && LuaLexer.isWordChar(text[start - 1])) start--
            parts.add(text.substring(start, end))
            p = start

            if (p == 0) break
            var sp = p - 1
            while (sp > 0 && isSpace(text[sp])) sp--
            val prev = text[sp]
            if (prev == '.' || prev == ':') {
                // `a..b` is concatenation, not a member access
                if (prev == '.' && sp > 0 && text[sp - 1] == '.') break
                p = sp
                continue
            }
            break
        }
        return chainOrEmpty(parts)
    }

    private fun chainOrEmpty(parts: List<String>): String =
        if (parts.isEmpty()) "" else parts.asReversed().joinToString(".")

    private fun findMatchingOpen(text: CharSequence, closePos: Int, close: Char): Int {
        val open = when (close) { ')' -> '('; ']' -> '['; '}' -> '{'; else -> return -1 }
        var depth = 1
        var i = closePos - 1
        while (i >= 0) {
            val c = text[i]
            if (c == '"' || c == '\'') {
                val strStart = skipStringBack(text, i)
                if (strStart < 0) return -1
                i = strStart - 1
                continue
            }
            if (c == close) depth++
            else if (c == open) {
                depth--
                if (depth == 0) return i
            }
            i--
        }
        return -1
    }

    private fun skipStringBack(text: CharSequence, closeQuotePos: Int): Int {
        val quote = text[closeQuotePos]
        var escaped = false
        var i = closeQuotePos - 1
        while (i >= 0) {
            val c = text[i]
            if (escaped) { escaped = false; i--; continue }
            if (c == '\\') { escaped = true; i--; continue }
            if (c == quote) return i
            i--
        }
        return -1
    }

    // ------------------------------------------------- local variable tracking

    private fun resolveLocalChain(text: CharSequence, cursor: Int, chain: String?, depth: Int): String? {
        if (depth > 5 || chain.isNullOrEmpty()) return chain
        if (chain.contains('.')) return chain
        val rhs = findLocalAssignment(text, cursor, chain) ?: return chain
        val sub = expressionToChain(rhs)
        if (sub.isNullOrEmpty()) return chain
        return resolveLocalChain(text, cursor, sub, depth + 1)
    }

    private fun expressionToChain(exprIn: String): String? {
        val expr = exprIn.trim()
        if (expr.isEmpty()) return null
        if (expr.length >= 2) {
            val first = expr.first()
            if ((first == '"' || first == '\'') && expr.last() == first) return "string"
        }
        if (expr == "true" || expr == "false" || expr == "nil") return null
        if (isNumberLiteral(expr)) return null
        val withDot = "$expr."
        val chain = parseQualifier(withDot, withDot.length - 1)
        return if (chain.isNotEmpty()) chain else null
    }

    private fun isNumberLiteral(s: String): Boolean {
        if (s.isEmpty()) return false
        var i = 0
        if (s[i] == '-') i++
        if (i + 1 < s.length && s[i] == '0' && (s[i + 1] == 'x' || s[i + 1] == 'X')) {
            i += 2
            if (i >= s.length) return false
            while (i < s.length) {
                val c = s[i]
                if (!((c in '0'..'9') || (c in 'a'..'f') || (c in 'A'..'F'))) return false
                i++
            }
            return true
        }
        var hasDigit = false
        var hasDot = false
        var hasExp = false
        while (i < s.length) {
            val c = s[i]
            if (c in '0'..'9') { hasDigit = true; i++; continue }
            if (c == '.' && !hasDot && !hasExp) { hasDot = true; i++; continue }
            if ((c == 'e' || c == 'E') && !hasExp && hasDigit) {
                hasExp = true
                if (i + 1 < s.length && (s[i + 1] == '+' || s[i + 1] == '-')) i++
                i++
                continue
            }
            return false
        }
        return hasDigit
    }

    /** RHS of the most recent `local name = EXPR` before [cursor], or null. */
    private fun findLocalAssignment(text: CharSequence, cursor: Int, name: String): String? {
        if (name.isEmpty()) return null
        val searchStart = maxOf(0, cursor - 8000)
        var searchEnd = cursor
        while (searchEnd > searchStart) {
            val localPos = findKeywordBackward(text, "local", searchStart, searchEnd)
            if (localPos < 0) return null

            var p = skipWhitespace(text, localPos + 5, cursor)
            val names = ArrayList<String>()
            var isAssignment = false
            while (p < cursor) {
                val start = p
                while (p < cursor && LuaLexer.isWordChar(text[p])) p++
                if (p == start) break
                names.add(text.substring(start, p))
                p = skipWhitespace(text, p, cursor)
                if (p >= cursor) break
                val c = text[p]
                if (c == ',') { p++; p = skipWhitespace(text, p, cursor); continue }
                if (c == '=') { p++; isAssignment = true; break }
                break
            }

            val nameIdx = names.indexOf(name)
            if (!isAssignment || nameIdx < 0) {
                searchEnd = localPos
                continue
            }
            p = skipWhitespace(text, p, cursor)
            val exprs = parseExprList(text, p, cursor)
            return if (nameIdx < exprs.size) exprs[nameIdx] else null
        }
        return null
    }

    private fun findKeywordBackward(text: CharSequence, keyword: String, start: Int, end: Int): Int {
        val klen = keyword.length
        var i = end - klen
        while (i >= start) {
            var match = true
            for (j in 0 until klen) {
                if (text[i + j] != keyword[j]) { match = false; break }
            }
            if (match &&
                !(i > 0 && LuaLexer.isWordChar(text[i - 1])) &&
                !(i + klen < text.length && LuaLexer.isWordChar(text[i + klen]))
            ) return i
            i--
        }
        return -1
    }

    private fun parseExprList(text: CharSequence, start: Int, limit: Int): List<String> {
        val exprs = ArrayList<String>()
        var p = start
        var exprStart = p
        var depth = 0
        while (p < limit) {
            val c = text[p]
            if (c == '"' || c == '\'') {
                p++
                while (p < limit) {
                    if (text[p] == '\\') { p += 2; continue }
                    if (text[p] == c) { p++; break }
                    p++
                }
                continue
            }
            if (c == '-' && p + 1 < limit && text[p + 1] == '-') {
                p += 2
                while (p < limit && text[p] != '\n') p++
                continue
            }
            if (c == '(' || c == '[' || c == '{') depth++
            else if (c == ')' || c == ']' || c == '}') depth = maxOf(0, depth - 1)
            else if (depth == 0 && (c == '\n' || c == ';')) break
            else if (depth == 0 && c == ',') {
                exprs.add(text.substring(exprStart, minOf(p, limit)).trim())
                p++
                exprStart = p
                continue
            }
            p++
        }
        val endIdx = minOf(p, limit)
        if (endIdx > exprStart) {
            val last = text.substring(exprStart, endIdx).trim()
            if (last.isNotEmpty()) exprs.add(last)
        }
        return exprs
    }

    private fun skipWhitespace(text: CharSequence, from: Int, limit: Int): Int {
        var p = from
        while (p < limit && isSpace(text[p])) p++
        return p
    }

    private fun isSpace(c: Char) = c == ' ' || c == '\t' || c == '\n' || c == '\r'
}
