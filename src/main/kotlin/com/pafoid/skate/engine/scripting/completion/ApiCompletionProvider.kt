package com.pafoid.skate.engine.scripting.completion

/**
 * Completion over the public script API, shared by the Kotlin, TypeScript and Lua hosts. It works on raw text
 * before the caret (no parsing of the whole file), so it is fast and tolerant of unfinished or invalid code.
 *
 * Supported: member completion after `.` on chains rooted at the context parameter or at simple local aliases
 * (`val e = ctx.entity`), top-level completion (context names, declared identifiers, keywords, snippets) and
 * partial identifiers (prefix, camel-hump and substring matching). No completions inside strings or comments.
 * Not resolved: values returned by generic lookups such as `getComponent(...)`, indexers and arbitrary expressions.
 */
class ApiCompletionProvider(
    private val profile: LanguageProfile,
    index: ApiIndex = ApiIndex.fromReflection(),
) : CompletionProvider {
    private val index = profile.adaptIndex(index)

    override fun complete(request: CompletionRequest): List<Completion> {
        val token = request.cancellation
        val offset = request.offset.coerceIn(0, request.text.length)
        val before = request.text.substring(0, offset)
        if (inCommentOrString(before)) return emptyList()
        token.throwIfCancelled()

        var prefixStart = offset
        while (prefixStart > 0 && isIdent(before[prefixStart - 1])) prefixStart--
        val prefix = before.substring(prefixStart)
        var j = prefixStart
        while (j > 0 && (before[j - 1] == ' ' || before[j - 1] == '\t')) j--

        val scope = Scope(before.substring(0, prefixStart))
        val candidates = if (j > 0 && before[j - 1] == '.') {
            val segments = parseChain(before, j - 1) ?: return emptyList()
            val type = index.type(scope.resolve(segments)) ?: return emptyList()
            memberCandidates(type)
        } else {
            if (declaresName(before, prefixStart)) return emptyList()
            if (prefix.isEmpty() && !request.explicit) return emptyList()
            topLevelCandidates(scope)
        }
        token.throwIfCancelled()

        return candidates.mapNotNull { c -> score(prefix, c.label)?.let { it to c } }
            .sortedWith(compareBy({ it.first }, { it.second.kind.ordinal }, { it.second.label.lowercase() }))
            .take(request.maxResults)
            .map { (_, c) -> c.toCompletion(prefixStart, offset) }
    }

    private class Candidate(
        val label: String,
        val kind: CompletionKind,
        val insertText: String,
        val cursorOffset: Int,
        val signature: String?,
        val doc: String?,
        val type: String?,
    ) {
        fun toCompletion(start: Int, end: Int) = Completion(label, kind, insertText, cursorOffset, signature, doc, type, start, end)
    }

    private fun memberCandidates(type: ApiType): List<Candidate> = type.members.map { m ->
        val sig = profile.renderSignature(m)
        if (m.kind == CompletionKind.METHOD) {
            val noParams = sig.substringAfter('(').trimStart().startsWith(")")
            Candidate(m.name, m.kind, m.name + "()", m.name.length + if (noParams) 2 else 1, sig, m.doc, m.type)
        } else {
            Candidate(m.name, m.kind, m.name, m.name.length, sig, m.doc, m.type)
        }
    }

    private fun topLevelCandidates(scope: Scope): List<Candidate> {
        val out = mutableListOf<Candidate>()
        scope.names().forEach { (name, type) ->
            out += Candidate(name, CompletionKind.VARIABLE, name, name.length, type?.let { "$name: $it" }, null, type)
        }
        profile.snippets.forEach { s ->
            val caret = s.body.indexOf("\$0").takeIf { it >= 0 } ?: s.body.length
            out += Candidate(s.label, CompletionKind.SNIPPET, s.body.replace("\$0", ""), caret, null, s.doc, null)
        }
        profile.keywords.forEach { out += Candidate(it, CompletionKind.KEYWORD, it, it.length, null, null, null) }
        return out.distinctBy { it.kind to it.label }
    }

    /** Variables visible at the caret: context parameters, aliases and other declared identifiers. */
    private inner class Scope(private val textBefore: String) {
        private val contexts = LinkedHashMap<String, String>().apply {
            index.roots.forEach { (k, v) -> put(k, v) }
            profile.handlerParam.findAll(textBefore).forEach { put(it.groupValues[1], "ScriptContext") }
        }
        private val aliases = HashMap<String, List<String>>()
        private val declared = LinkedHashSet<String>()

        init {
            textBefore.lineSequence().forEach { line ->
                profile.alias.find(line)?.let { m ->
                    chainOf(m.groupValues[2])?.let { aliases[m.groupValues[1]] = it }
                }
                profile.declaration.findAll(line).forEach { declared += it.groupValues[1] }
            }
        }

        fun names(): List<Pair<String, String?>> {
            val result = LinkedHashMap<String, String?>()
            contexts.forEach { (k, v) -> result[k] = v }
            aliases.keys.forEach { result[it] = resolve(listOf(it)) }
            declared.forEach { result.putIfAbsent(it, null) }
            return result.toList()
        }

        fun resolve(segments: List<String>, depth: Int = 0): String? {
            if (segments.isEmpty() || depth > 4) return null
            var type: String? = contexts[segments[0]]
            if (type == null) {
                val target = aliases[segments[0]] ?: return null
                type = resolve(target, depth + 1)
            }
            for (seg in segments.drop(1)) type = index.type(type)?.member(seg)?.type ?: return null
            return type?.let { index.type(it) }?.name
        }
    }

    private fun chainOf(expr: String): List<String>? {
        val parts = expr.replace("?.", ".").replace("()", "").split('.')
        return parts.takeIf { it.isNotEmpty() && it.all { p -> p.isNotEmpty() && p.all(::isIdent) } }
    }

    /** Parses `a.b(x).c` ending at the dot at [dot]; null when the receiver is not a simple chain. */
    private fun parseChain(s: String, dot: Int): List<String>? {
        val segs = ArrayDeque<String>()
        var p = dot
        while (true) {
            var e = p
            if (e > 0 && (s[e - 1] == '?' || s[e - 1] == '!')) e--
            while (e > 0 && s[e - 1].isWhitespace()) e--
            if (e > 0 && s[e - 1] == ')') {
                var depth = 0
                var k = e - 1
                while (k >= 0) {
                    if (s[k] == ')') depth++ else if (s[k] == '(') { depth--; if (depth == 0) break }
                    k--
                }
                if (k < 0) return null
                e = k
                while (e > 0 && s[e - 1].isWhitespace()) e--
            }
            var st = e
            while (st > 0 && isIdent(s[st - 1])) st--
            if (st == e) return null
            segs.addFirst(s.substring(st, e))
            var q = st
            while (q > 0 && s[q - 1].isWhitespace()) q--
            if (q > 0 && s[q - 1] == '.') p = q - 1 else break
        }
        return segs.toList()
    }

    private fun declaresName(before: String, wordEnd: Int): Boolean {
        var e = wordEnd
        while (e > 0 && before[e - 1].isWhitespace()) e--
        var st = e
        while (st > 0 && isIdent(before[st - 1])) st--
        return st < e && before.substring(st, e) in profile.declarationWords && wordEnd > e
    }

    /** True when the end of [text] lies inside a comment or string literal. Single-line strings end at newlines. */
    private fun inCommentOrString(text: String): Boolean {
        var i = 0
        val block = profile.blockComment
        while (i < text.length) {
            val c = text[i]
            when {
                block != null && text.startsWith(block.first, i) -> {
                    val end = text.indexOf(block.second, i + block.first.length)
                    if (end < 0) return true
                    i = end + block.second.length
                }
                text.startsWith(profile.lineComment, i) -> {
                    val end = text.indexOf('\n', i)
                    if (end < 0) return true
                    i = end + 1
                }
                c in profile.quotes -> {
                    var k = i + 1
                    var closed = false
                    while (k < text.length) {
                        if (text[k] == '\\') { k += 2; continue }
                        if (text[k] == c) { closed = true; break }
                        if (text[k] == '\n' && c != '`') break
                        k++
                    }
                    if (!closed) return k >= text.length
                    i = k + 1
                }
                else -> i++
            }
        }
        return false
    }

    /** Lower is better; null means no match. */
    private fun score(prefix: String, label: String): Int? {
        if (prefix.isEmpty()) return 0
        if (label.startsWith(prefix)) return 0
        if (label.startsWith(prefix, ignoreCase = true)) return 1
        val humps = label.filterIndexed { i, ch -> i == 0 || ch.isUpperCase() }
        if (humps.startsWith(prefix, ignoreCase = true)) return 2
        if (label.contains(prefix, ignoreCase = true)) return 3
        return null
    }

    private fun isIdent(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'
}
