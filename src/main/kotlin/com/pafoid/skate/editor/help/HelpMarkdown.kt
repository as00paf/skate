package com.pafoid.skate.editor.help

/** A run of an article's inline text (EDT-68). */
sealed class Inline {
    data class Text(val text: String, val bold: Boolean = false, val italic: Boolean = false) : Inline()
    data class Code(val text: String) : Inline()
    data class Link(val text: String, val target: LinkTarget) : Inline()
    object LineBreak : Inline() {
        override fun toString() = "LineBreak"
    }
}

/** Where a link goes: another help topic (`console.md`), a web page, or something the Help window cannot open. */
sealed class LinkTarget {
    data class Topic(val id: String) : LinkTarget()
    data class Web(val url: String) : LinkTarget()
    data class Unsupported(val raw: String) : LinkTarget()

    companion object {
        fun of(raw: String): LinkTarget {
            val target = raw.trim()
            return when {
                target.startsWith("http://") || target.startsWith("https://") -> Web(target)
                target.endsWith(".md") && !target.contains('/') && target.length > 3 -> Topic(target.removeSuffix(".md"))
                else -> Unsupported(target)
            }
        }
    }
}

/** A block of an article. [line] is the 1-based source line it starts on. */
sealed class Block {
    abstract val line: Int

    data class Heading(val level: Int, val inlines: List<Inline>, override val line: Int) : Block()
    data class Paragraph(val inlines: List<Inline>, override val line: Int) : Block()
    /** [depth] 0 for a top-level item; [number] is set for ordered items. */
    data class ListItem(val depth: Int, val number: Int?, val inlines: List<Inline>, override val line: Int) : Block()
    data class Code(val text: String, override val line: Int) : Block()
    data class Quote(val inlines: List<Inline>, override val line: Int) : Block()
    data class Rule(override val line: Int) : Block()
}

/**
 * The Markdown subset of help articles (docs/design/EDT-68.md): `#`–`###` headings, paragraphs and hard line breaks,
 * ordered and unordered (nested) lists, **bold**, *italic*, `inline code`, fenced code blocks, links to topics
 * (`[Console](console.md)`) and web pages, block quotes and horizontal rules. Anything else (tables, images) stays
 * plain text. Pure Kotlin: the ImGui side is [HelpMarkdownRenderer].
 */
object HelpMarkdown {
    private val HEADING = Regex("""^(#{1,6})\s+(.*?)\s*#*\s*$""")
    private val BULLET = Regex("""^(\s*)[-*+]\s+(.*)$""")
    private val ORDERED = Regex("""^(\s*)(\d{1,9})[.)]\s+(.*)$""")
    private val RULE = Regex("""^\s{0,3}([-*_])(\s*\1){2,}\s*$""")
    private val QUOTE = Regex("""^\s{0,3}>\s?(.*)$""")
    private val FENCE = Regex("""^\s*```""")
    private val WEB = Regex("""https?://[^\s<>()\[\]]+""")

    fun parse(markdown: String): List<Block> {
        val lines = markdown.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val blocks = mutableListOf<Block>()
        val listIndents = mutableListOf<Int>()
        var i = 0

        // The open paragraph, list item or quote, whose continuation lines join it.
        var pending: Pending? = null
        fun flush() {
            pending?.let { blocks += it.toBlock() }
            pending = null
        }

        while (i < lines.size) {
            val raw = lines[i]
            val n = i + 1
            when {
                FENCE.containsMatchIn(raw) -> {
                    flush(); listIndents.clear()
                    val body = mutableListOf<String>()
                    i++
                    while (i < lines.size && !FENCE.containsMatchIn(lines[i])) body += lines[i++]
                    blocks += Block.Code(body.joinToString("\n"), n)
                }
                raw.isBlank() -> {
                    flush()
                    // A blank line ends a list unless the next line is still an item or an indented continuation.
                    val next = lines.getOrNull(i + 1)
                    if (next == null || !(BULLET.matches(next) || ORDERED.matches(next) || next.startsWith("  "))) listIndents.clear()
                }
                HEADING.matches(raw) -> {
                    flush(); listIndents.clear()
                    val m = HEADING.find(raw)!!
                    blocks += Block.Heading(m.groupValues[1].length, inlines(m.groupValues[2]), n)
                }
                RULE.matches(raw) -> { flush(); listIndents.clear(); blocks += Block.Rule(n) }
                QUOTE.matches(raw) -> {
                    val text = QUOTE.find(raw)!!.groupValues[1]
                    val p = pending
                    if (p is Pending.Quote) p.add(text) else { flush(); listIndents.clear(); pending = Pending.Quote(n, text) }
                }
                BULLET.matches(raw) || ORDERED.matches(raw) -> {
                    flush()
                    val ordered = ORDERED.find(raw)
                    val bullet = BULLET.find(raw)
                    val indent = (ordered ?: bullet)!!.groupValues[1].replace("\t", "    ").length
                    while (listIndents.isNotEmpty() && listIndents.last() > indent) listIndents.removeAt(listIndents.lastIndex)
                    if (listIndents.isEmpty() || listIndents.last() < indent) listIndents += indent
                    val depth = listIndents.size - 1
                    pending = if (ordered != null) {
                        Pending.Item(n, depth, ordered.groupValues[2].toInt(), ordered.groupValues[3])
                    } else {
                        Pending.Item(n, depth, null, bullet!!.groupValues[2])
                    }
                }
                else -> {
                    val p = pending
                    when {
                        p is Pending.Paragraph || p is Pending.Item || p is Pending.Quote -> p.add(raw)
                        else -> { listIndents.clear(); pending = Pending.Paragraph(n, raw) }
                    }
                }
            }
            i++
        }
        flush()
        return blocks
    }

    /** Lines joined into one block; a line ending in two spaces or `\` keeps a hard line break. */
    private sealed class Pending(val line: Int, first: String) {
        val lines = mutableListOf(first)
        fun add(text: String) { lines += text }

        protected fun joined(): List<Inline> {
            val out = mutableListOf<Inline>()
            lines.forEachIndexed { i, raw ->
                val hard = raw.endsWith("  ") || raw.trimEnd().endsWith("\\")
                val text = raw.trim().removeSuffix("\\").trimEnd()
                if (i > 0 && out.lastOrNull() != Inline.LineBreak) out += Inline.Text(" ")
                out += inlines(text)
                if (hard && i < lines.lastIndex) out += Inline.LineBreak
            }
            return merge(out)
        }

        abstract fun toBlock(): Block

        class Paragraph(line: Int, first: String) : Pending(line, first) {
            override fun toBlock() = Block.Paragraph(joined(), line)
        }

        class Quote(line: Int, first: String) : Pending(line, first) {
            override fun toBlock() = Block.Quote(joined(), line)
        }

        class Item(line: Int, private val depth: Int, private val number: Int?, first: String) : Pending(line, first) {
            override fun toBlock() = Block.ListItem(depth, number, joined(), line)
        }
    }

    /** Inline runs of one line of text: emphasis, code spans, links, bare web addresses and `\` escapes. */
    fun inlines(text: String): List<Inline> {
        val out = mutableListOf<Inline>()
        val buf = StringBuilder()
        var bold = false
        var italic = false
        fun emit() {
            if (buf.isNotEmpty()) out += Inline.Text(buf.toString(), bold, italic)
            buf.clear()
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE) {
                buf.append(text[i + 1]); i += 2; continue
            }
            if (c == '`') {
                val end = text.indexOf('`', i + 1)
                if (end > i) { emit(); out += Inline.Code(text.substring(i + 1, end)); i = end + 1; continue }
            }
            if (text.startsWith("![", i)) { buf.append("!["); i += 2; continue } // images stay text
            if (c == '[') {
                val link = link(text, i)
                if (link != null) { emit(); out += Inline.Link(link.first, LinkTarget.of(link.second)); i = link.third; continue }
            }
            if (text.startsWith("**", i) || text.startsWith("__", i)) { emit(); bold = !bold; i += 2; continue }
            if (c == '*' && opens(text, i, italic)) { emit(); italic = !italic; i++; continue }
            if (c == '_' && opens(text, i, italic) && wordEdge(text, i, italic)) { emit(); italic = !italic; i++; continue }
            if ((text.startsWith("http://", i) || text.startsWith("https://", i)) && (i == 0 || !text[i - 1].isLetterOrDigit())) {
                val url = WEB.find(text, i)!!.value.trimEnd('.', ',', ';', ':', '!', '?', '\'', '"')
                emit(); out += Inline.Link(url, LinkTarget.Web(url)); i += url.length; continue
            }
            buf.append(c); i++
        }
        emit()
        return merge(out)
    }

    /** A `[label](target)` starting at [start]: the label, the target and the index after it; null when not a link. */
    private fun link(text: String, start: Int): Triple<String, String, Int>? {
        val close = text.indexOf("](", start + 1).takeIf { it > start } ?: return null
        if (text.substring(start + 1, close).contains('[')) return null
        val end = text.indexOf(')', close + 2).takeIf { it >= 0 } ?: return null
        val label = inlines(text.substring(start + 1, close)).joinToString("") { plain(it) }
        return Triple(label, text.substring(close + 2, end), end + 1)
    }

    /** `*` / `_` toggles emphasis when it opens before a non-space or closes after one. */
    private fun opens(text: String, i: Int, open: Boolean): Boolean =
        if (open) i > 0 && !text[i - 1].isWhitespace() else i + 1 < text.length && !text[i + 1].isWhitespace()

    /** `_` only at a word's edge, so snake_case names stay intact. */
    private fun wordEdge(text: String, i: Int, open: Boolean): Boolean =
        if (open) i + 1 >= text.length || !text[i + 1].isLetterOrDigit() else i == 0 || !text[i - 1].isLetterOrDigit()

    private const val ESCAPABLE = "\\`*_[]()#+-.!>"

    /** Adjacent text runs with the same style become one. */
    private fun merge(runs: List<Inline>): List<Inline> {
        val out = mutableListOf<Inline>()
        runs.forEach { run ->
            val last = out.lastOrNull()
            if (run is Inline.Text && last is Inline.Text && last.bold == run.bold && last.italic == run.italic) {
                out[out.lastIndex] = last.copy(text = last.text + run.text)
            } else if (!(run is Inline.Text && run.text.isEmpty())) {
                out += run
            }
        }
        return out
    }

    /** The visible text of a run. */
    fun plain(inline: Inline): String = when (inline) {
        is Inline.Text -> inline.text
        is Inline.Code -> inline.text
        is Inline.Link -> inline.text
        Inline.LineBreak -> "\n"
    }

    /** The visible text of several runs. */
    fun plain(inlines: List<Inline>): String = inlines.joinToString("") { plain(it) }

    /** The article title: the text of its first `#` heading. */
    fun title(blocks: List<Block>): String? =
        blocks.firstOrNull { it is Block.Heading && it.level == 1 }?.let { plain((it as Block.Heading).inlines).trim() }

    /**
     * What would make the Help window show a broken article (used by `HelpArticleCoverageTest`): empty, no single
     * leading `#` title, a heading deeper than `###`, an unclosed code fence, an empty link or a link to a topic not
     * in [topics].
     */
    fun problems(markdown: String, topics: Set<String>): List<String> {
        if (markdown.isBlank()) return listOf("empty article")
        val out = mutableListOf<String>()
        val lines = markdown.removePrefix("\uFEFF").lines()
        if (!lines.first { it.isNotBlank() }.startsWith("# ")) out += "does not start with a '# ' title"
        var inFence = false
        var titles = 0
        lines.forEachIndexed { i, line ->
            val n = i + 1
            if (FENCE.containsMatchIn(line)) { inFence = !inFence; return@forEachIndexed }
            if (inFence) return@forEachIndexed
            HEADING.find(line)?.let { h ->
                val level = h.groupValues[1].length
                if (level == 1 && ++titles > 1) out += "more than one '# ' title (line $n)"
                if (level > 3) out += "heading deeper than ### (line $n)"
            }
            inlines(line).filterIsInstance<Inline.Link>().forEach { link ->
                when (val target = link.target) {
                    is LinkTarget.Web -> Unit
                    is LinkTarget.Topic -> if (target.id !in topics) out += "link to missing topic '${target.id}.md' (line $n)"
                    is LinkTarget.Unsupported ->
                        out += if (target.raw.isEmpty()) "empty link target (line $n)" else "link to missing topic '${target.raw}' (line $n)"
                }
            }
        }
        if (inFence) out += "unclosed code fence"
        return out
    }
}
