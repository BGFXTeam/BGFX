package com.znfdev.core.markdown

/** Block-level nodes. */
sealed class MdBlock {
    data class Heading(val level: Int, val content: List<MdInline>) : MdBlock()
    data class Paragraph(val content: List<MdInline>) : MdBlock()
    data class Code(val lang: String, val code: String) : MdBlock()
    data class Quote(val blocks: List<MdBlock>) : MdBlock()
    data class ListBlock(val ordered: Boolean, val start: Int, val items: List<MdListItem>) : MdBlock()
    data class Table(
        val header: List<List<MdInline>>,
        val rows: List<List<List<MdInline>>>,
        val aligns: List<MdAlign>
    ) : MdBlock()
    data class Image(val alt: String, val url: String) : MdBlock()
    object Rule : MdBlock()
}

enum class MdAlign { START, CENTER, END }

/** [checked] is null for a normal item and true/false for a `- [x]` / `- [ ]` task. */
data class MdListItem(val blocks: List<MdBlock>, val checked: Boolean?)

/** Inline nodes. */
sealed class MdInline {
    data class Text(val text: String) : MdInline()
    data class Bold(val children: List<MdInline>) : MdInline()
    data class Italic(val children: List<MdInline>) : MdInline()
    data class Strike(val children: List<MdInline>) : MdInline()
    data class Code(val text: String) : MdInline()
    data class Link(val children: List<MdInline>, val url: String) : MdInline()
    data class Image(val alt: String, val url: String) : MdInline()
    object LineBreak : MdInline()
}

/**
 * A small, forgiving Markdown parser (headings, emphasis, lists, task lists, quotes,
 * fenced code, tables, rules, links, images). Pure Kotlin so it runs on a plain JVM.
 * Soft line breaks are kept as line breaks, which suits short server notices.
 */
object MdParser {

    fun parse(source: String): List<MdBlock> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        return parseBlocks(lines)
    }

    // ================================================================ blocks

    private val headingRe = Regex("^ {0,3}(#{1,6})\\s+(.*?)(?:\\s+#+)?\\s*$")
    private val ruleRe = Regex("^ {0,3}([-*_])(?:\\s*\\1){2,}\\s*$")
    private val bulletRe = Regex("^(\\s*)([-*+])\\s+(.*)$")
    private val orderedRe = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val tableSepRe = Regex("^\\s*\\|?\\s*:?-+:?\\s*(\\|\\s*:?-+:?\\s*)*\\|?\\s*$")
    private val taskRe = Regex("^\\[([ xX])]\\s+(.*)$")

    private fun parseBlocks(lines: List<String>): List<MdBlock> {
        val out = ArrayList<MdBlock>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()
            if (trimmed.isEmpty()) { i++; continue }

            // fenced code
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val marker = trimmed.substring(0, 3)
                val lang = trimmed.substring(3).trim().takeWhile { !it.isWhitespace() }
                val code = ArrayList<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith(marker)) { code.add(lines[i]); i++ }
                if (i < lines.size) i++ // closing fence
                out.add(MdBlock.Code(lang, code.joinToString("\n")))
                continue
            }

            val hm = headingRe.matchEntire(line)
            if (hm != null) {
                out.add(MdBlock.Heading(hm.groupValues[1].length, parseInline(hm.groupValues[2].trim())))
                i++
                continue
            }

            if (ruleRe.matches(line)) { out.add(MdBlock.Rule); i++; continue }

            if (trimmed.startsWith(">")) {
                val inner = ArrayList<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    var t = lines[i].trim().substring(1)
                    if (t.startsWith(" ")) t = t.substring(1)
                    inner.add(t)
                    i++
                }
                out.add(MdBlock.Quote(parseBlocks(inner)))
                continue
            }

            if (isTableStart(lines, i)) {
                i = parseTable(lines, i, out)
                continue
            }

            if (listMarker(line) != null) {
                i = parseList(lines, i, out)
                continue
            }

            // paragraph: runs until a blank line or the start of another block
            val para = ArrayList<String>()
            while (i < lines.size) {
                val l = lines[i]
                val t = l.trim()
                if (t.isEmpty()) break
                if (para.isNotEmpty() && startsBlock(lines, i)) break
                para.add(l.trimEnd())
                i++
            }
            val inlines = parseInline(para.joinToString("\n"))
            val only = inlines.singleOrNull()
            if (only is MdInline.Image) out.add(MdBlock.Image(only.alt, only.url))
            else out.add(MdBlock.Paragraph(inlines))
        }
        return out
    }

    private fun startsBlock(lines: List<String>, i: Int): Boolean {
        val line = lines[i]
        val t = line.trim()
        return t.startsWith("```") || t.startsWith("~~~") || t.startsWith(">") ||
            headingRe.matches(line) || ruleRe.matches(line) ||
            listMarker(line) != null || isTableStart(lines, i)
    }

    private class Marker(val indent: Int, val ordered: Boolean, val number: Int, val contentIndent: Int, val text: String)

    private fun listMarker(line: String): Marker? {
        if (ruleRe.matches(line)) return null
        bulletRe.matchEntire(line)?.let { m ->
            val indent = m.groupValues[1].replace("\t", "    ").length
            return Marker(indent, false, 0, indent + 2, m.groupValues[3])
        }
        orderedRe.matchEntire(line)?.let { m ->
            val indent = m.groupValues[1].replace("\t", "    ").length
            val num = m.groupValues[2]
            return Marker(indent, true, num.toIntOrNull() ?: 1, indent + num.length + 2, m.groupValues[3])
        }
        return null
    }

    private fun indentOf(line: String): Int {
        var n = 0
        for (c in line) {
            if (c == ' ') n++ else if (c == '\t') n += 4 else break
        }
        return n
    }

    private fun stripIndent(line: String, n: Int): String {
        var removed = 0
        var idx = 0
        while (idx < line.length && removed < n) {
            val c = line[idx]
            if (c == ' ') removed++ else if (c == '\t') removed += 4 else break
            idx++
        }
        return line.substring(idx)
    }

    private fun parseList(lines: List<String>, startIdx: Int, out: MutableList<MdBlock>): Int {
        val first = listMarker(lines[startIdx])!!
        val ordered = first.ordered
        val base = first.indent
        val items = ArrayList<MdListItem>()
        var i = startIdx

        while (i < lines.size) {
            val marker = listMarker(lines[i])
            if (marker == null || marker.indent != base || marker.ordered != ordered) break

            var text = marker.text
            var checked: Boolean? = null
            taskRe.matchEntire(text)?.let { m ->
                checked = m.groupValues[1] != " "
                text = m.groupValues[2]
            }
            val body = arrayListOf(text)
            i++

            // continuation lines: deeper-indented text (possibly after blank lines)
            while (i < lines.size) {
                val l = lines[i]
                if (l.isBlank()) {
                    var j = i
                    while (j < lines.size && lines[j].isBlank()) j++
                    if (j < lines.size && indentOf(lines[j]) > base) {
                        for (k in i until j) body.add("")
                        i = j
                        continue
                    }
                    break
                }
                val ind = indentOf(l)
                if (ind > base) {
                    body.add(stripIndent(l, minOf(ind, marker.contentIndent - base)))
                    i++
                } else break
            }
            items.add(MdListItem(parseBlocks(body), checked))

            // a blank line between items of the same list is fine
            var j = i
            while (j < lines.size && lines[j].isBlank()) j++
            if (j < lines.size) {
                val next = listMarker(lines[j])
                if (next != null && next.indent == base && next.ordered == ordered) i = j
            }
        }
        out.add(MdBlock.ListBlock(ordered, if (ordered) first.number else 1, items))
        return i
    }

    private fun isTableStart(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size) return false
        val head = lines[i]
        val sep = lines[i + 1]
        return head.contains('|') && sep.contains('-') && tableSepRe.matches(sep)
    }

    private fun parseTable(lines: List<String>, start: Int, out: MutableList<MdBlock>): Int {
        val header = splitRow(lines[start]).map { parseInline(it) }
        val aligns = splitRow(lines[start + 1]).map { cell ->
            val t = cell.trim()
            when {
                t.startsWith(":") && t.endsWith(":") -> MdAlign.CENTER
                t.endsWith(":") -> MdAlign.END
                else -> MdAlign.START
            }
        }
        val rows = ArrayList<List<List<MdInline>>>()
        var i = start + 2
        while (i < lines.size && lines[i].isNotBlank() && lines[i].contains('|')) {
            val cells = splitRow(lines[i]).map { parseInline(it) }.toMutableList()
            while (cells.size < header.size) cells.add(emptyList())
            rows.add(cells.take(header.size))
            i++
        }
        out.add(MdBlock.Table(header, rows, aligns))
        return i
    }

    private fun splitRow(row: String): List<String> {
        var t = row.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|") && !t.endsWith("\\|")) t = t.substring(0, t.length - 1)
        val cells = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < t.length) {
            val c = t[i]
            if (c == '\\' && i + 1 < t.length && t[i + 1] == '|') { sb.append('|'); i += 2; continue }
            if (c == '|') { cells.add(sb.toString().trim()); sb.setLength(0); i++; continue }
            sb.append(c)
            i++
        }
        cells.add(sb.toString().trim())
        return cells
    }

    // ================================================================ inlines

    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"

    fun parseInline(s: String): List<MdInline> = inline(s, 0, s.length)

    private fun inline(s: String, from: Int, end: Int): List<MdInline> {
        val out = ArrayList<MdInline>()
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) { out.add(MdInline.Text(sb.toString())); sb.setLength(0) }
        }

        var i = from
        while (i < end) {
            val c = s[i]
            when {
                c == '\\' && i + 1 < end && s[i + 1] in ESCAPABLE -> {
                    sb.append(s[i + 1]); i += 2
                }
                c == '\n' -> {
                    flush(); out.add(MdInline.LineBreak); i++
                }
                c == '`' -> {
                    var n = 0
                    while (i + n < end && s[i + n] == '`') n++
                    val close = findBacktickRun(s, i + n, end, n)
                    if (close >= 0) {
                        flush()
                        out.add(MdInline.Code(s.substring(i + n, close).trim()))
                        i = close + n
                    } else {
                        sb.append("`".repeat(n)); i += n
                    }
                }
                c == '!' && i + 1 < end && s[i + 1] == '[' -> {
                    val link = parseLink(s, i + 1, end)
                    if (link != null) {
                        flush()
                        out.add(MdInline.Image(s.substring(link.textStart, link.textEnd), link.url))
                        i = link.next
                    } else { sb.append(c); i++ }
                }
                c == '[' -> {
                    val link = parseLink(s, i, end)
                    if (link != null) {
                        flush()
                        out.add(MdInline.Link(inline(s, link.textStart, link.textEnd), link.url))
                        i = link.next
                    } else { sb.append(c); i++ }
                }
                c == '*' || c == '_' || c == '~' -> {
                    var n = 0
                    while (i + n < end && s[i + n] == c) n++
                    val handled = tryEmphasis(s, i, n, end, c, out, ::flush)
                    if (handled > 0) i = handled else { sb.append(s, i, i + n); i += n }
                }
                (c == 'h') && (s.startsWith("http://", i) || s.startsWith("https://", i)) &&
                    (i == 0 || !s[i - 1].isLetterOrDigit()) -> {
                    var j = i
                    while (j < end && !s[j].isWhitespace() && s[j] != ')' && s[j] != '>' && s[j] != ']') j++
                    while (j > i && s[j - 1] in ".,;:!?") j--
                    flush()
                    val url = s.substring(i, j)
                    out.add(MdInline.Link(listOf(MdInline.Text(url)), url))
                    i = j
                }
                else -> { sb.append(c); i++ }
            }
        }
        flush()
        return out
    }

    /** Returns the index just past the emphasis span, or 0 when this run is literal. */
    private fun tryEmphasis(
        s: String, i: Int, run: Int, end: Int, ch: Char,
        out: MutableList<MdInline>, flush: () -> Unit
    ): Int {
        val next = i + run
        if (next >= end || s[next].isWhitespace()) return 0
        if (ch == '_' && i > 0 && s[i - 1].isLetterOrDigit()) return 0 // snake_case

        if (ch == '~') {
            if (run < 2) return 0
            val close = findClosing(s, next, end, '~', 2)
            if (close < 0) return 0
            flush()
            out.add(MdInline.Strike(inline(s, i + 2, close)))
            return close + 2
        }

        var m = minOf(run, 3)
        while (m >= 1) {
            val close = findClosing(s, i + m, end, ch, m)
            if (close > i + m) {
                flush()
                val inner = inline(s, i + m, close)
                out.add(
                    when (m) {
                        3 -> MdInline.Bold(listOf(MdInline.Italic(inner)))
                        2 -> MdInline.Bold(inner)
                        else -> MdInline.Italic(inner)
                    }
                )
                return close + m
            }
            m--
        }
        return 0
    }

    private fun findClosing(s: String, from: Int, end: Int, ch: Char, m: Int): Int {
        var i = from
        while (i < end) {
            val c = s[i]
            if (c == '\\') { i += 2; continue }
            if (c == '`') {
                var n = 0
                while (i + n < end && s[i + n] == '`') n++
                val close = findBacktickRun(s, i + n, end, n)
                i = if (close >= 0) close + n else i + n
                continue
            }
            if (c == ch) {
                var j = i
                while (j < end && s[j] == ch) j++
                val run = j - i
                // a lone `*` must not close on the first half of a `**` (nested bold)
                if (m == 1 && run >= 2) { i = j; continue }
                val prevOk = i > from && !s[i - 1].isWhitespace()
                val nextOk = ch != '_' || j >= end || !s[j].isLetterOrDigit()
                if (run >= m && prevOk && nextOk) return i
                i = j
                continue
            }
            i++
        }
        return -1
    }

    private fun findBacktickRun(s: String, from: Int, end: Int, n: Int): Int {
        var i = from
        while (i < end) {
            if (s[i] == '`') {
                var k = 0
                while (i + k < end && s[i + k] == '`') k++
                if (k == n) return i
                i += k
            } else i++
        }
        return -1
    }

    private class LinkParts(val textStart: Int, val textEnd: Int, val url: String, val next: Int)

    /** Parses `[text](url "title")` starting at the '[' at [open]. */
    private fun parseLink(s: String, open: Int, end: Int): LinkParts? {
        var depth = 0
        var i = open
        var close = -1
        while (i < end) {
            val c = s[i]
            if (c == '\\') { i += 2; continue }
            if (c == '[') depth++
            else if (c == ']') { depth--; if (depth == 0) { close = i; break } }
            i++
        }
        if (close < 0 || close + 1 >= end || s[close + 1] != '(') return null
        var j = close + 2
        var parens = 1
        while (j < end) {
            val c = s[j]
            if (c == '(') parens++
            else if (c == ')') { parens--; if (parens == 0) break }
            else if (c == '\n') return null
            j++
        }
        if (j >= end) return null
        var url = s.substring(close + 2, j).trim()
        val space = url.indexOf(' ')
        if (space > 0) url = url.substring(0, space) // drop "title"
        if (url.startsWith("<") && url.endsWith(">") && url.length >= 2) url = url.substring(1, url.length - 1)
        return LinkParts(open + 1, close, url, j + 1)
    }
}
