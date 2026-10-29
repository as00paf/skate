package com.pafoid.skate.engine.scripting.completion

/** Per-language syntax knowledge used by [ApiCompletionProvider]; the analysis itself is shared. */
class LanguageProfile(
    val name: String,
    val keywords: List<String>,
    val snippets: List<Snippet>,
    val lineComment: String,
    val blockComment: Pair<String, String>? = null,
    val quotes: String = "\"'",
    /** Group 1 = name of the context parameter of a lifecycle handler. */
    val handlerParam: Regex,
    /** Group 1 = a declared identifier. */
    val declaration: Regex,
    /** Group 1 = variable name, group 2 = dotted expression it aliases (e.g. `val e = ctx.entity`). */
    val alias: Regex,
    val declarationWords: Set<String>,
    val adaptIndex: (ApiIndex) -> ApiIndex = { it },
    val renderSignature: (ApiMember) -> String = { it.signature },
) {
    /** [body] contains `$0` where the caret goes. */
    class Snippet(val label: String, val body: String, val doc: String)

    companion object {
        private val LIFECYCLE = listOf("onCreate" to "Runs once when the script starts.", "onUpdate" to "Runs every frame.",
            "onFixedUpdate" to "Runs at the fixed time step.", "onDestroy" to "Runs when the script is destroyed.")

        private val SIG = Regex("^(?:operator )?fun (?:<[^>]*> )?(\\w+)\\((.*)\\): ([^:]*)$")
        private val PROP = Regex("^(?:val|var) (\\w+): (.*)$")

        private fun tsType(t: String): String {
            val s = t.trim()
            Regex("^\\((.*)\\) -> (.*)$").matchEntire(s)?.let { m ->
                val arg = m.groupValues[1].takeIf { it.isNotBlank() }?.let { "arg: ${tsType(it)}" } ?: ""
                return "($arg) => ${tsType(m.groupValues[2])}"
            }
            val nullable = s.endsWith("?")
            val mapped = when (val base = s.removeSuffix("?")) {
                "String" -> "string"
                "Float", "Int", "Double" -> "number"
                "Boolean" -> "boolean"
                "Unit" -> "void"
                "Any" -> "any"
                else -> base
            }
            return if (nullable) "$mapped | null" else mapped
        }

        private fun tsSignature(m: ApiMember): String {
            SIG.matchEntire(m.signature)?.let { s ->
                val params = s.groupValues[2].takeIf { it.isNotBlank() }?.split(", ")?.joinToString(", ") { p ->
                    val (n, t) = p.split(": ", limit = 2)
                    "$n: ${tsType(t)}"
                }.orEmpty()
                return "${s.groupValues[1]}($params): ${tsType(s.groupValues[3])}"
            }
            PROP.matchEntire(m.signature)?.let { return "${it.groupValues[1]}: ${tsType(it.groupValues[2])}" }
            return m.signature
        }

        private fun luaSignature(m: ApiMember): String {
            SIG.matchEntire(m.signature)?.let { s ->
                val names = s.groupValues[2].takeIf { it.isNotBlank() }?.split(", ")?.joinToString(", ") { it.substringBefore(":") }.orEmpty()
                return "${s.groupValues[1]}($names)"
            }
            return PROP.matchEntire(m.signature)?.let { it.groupValues[1] } ?: m.signature
        }

        /** JS/Lua scripts look components up by name and use `state.get/set`; hide the Kotlin-only forms. */
        private fun adaptDynamic(index: ApiIndex): ApiIndex {
            val entity = index.type("ScriptEntity") ?: return index
            val members = entity.members.filter { it.name !in setOf("component", "getComponent", "hasComponent") } + listOf(
                ApiMember("getComponent", CompletionKind.METHOD, null, "fun getComponent(name: String): Any?",
                    Docs.of("ScriptEntity", "getComponent")),
                ApiMember("hasComponent", CompletionKind.METHOD, null, "fun hasComponent(name: String): Boolean",
                    Docs.of("ScriptEntity", "hasComponent")),
            )
            val state = ApiType("MutableMap", listOf(
                ApiMember("get", CompletionKind.METHOD, null, "fun get(key: String): Any?", Docs.of("Map", "get")),
                ApiMember("set", CompletionKind.METHOD, null, "fun set(key: String, value: Any?): Unit", "Stores a value under the key."),
            ))
            return index.withType(entity.copy(members = members.sortedBy { it.name })).withType(state)
        }

        private fun lifecycleSnippets(body: (String, String) -> String) =
            LIFECYCLE.map { (n, d) -> Snippet(n, body(n, if (n == "onUpdate" || n == "onFixedUpdate") "dt" else ""), d) }

        val KOTLIN = LanguageProfile(
            name = "kotlin",
            keywords = listOf("val", "var", "fun", "if", "else", "when", "for", "while", "return", "true", "false", "null",
                "in", "is", "as", "break", "continue", "try", "catch", "finally"),
            snippets = lifecycleSnippets { n, dt -> "$n { ctx${if (dt.isEmpty()) "" else ", $dt"} ->\n    \$0\n}" } + listOf(
                Snippet("if", "if (\$0) {\n}", "if statement"),
                Snippet("for", "for (i in 0 until \$0) {\n}", "counted loop"),
            ),
            lineComment = "//", blockComment = "/*" to "*/",
            handlerParam = Regex("on(?:Create|Update|FixedUpdate|Destroy)\\s*\\{\\s*(\\w+)"),
            declaration = Regex("\\b(?:val|var|fun)\\s+(\\w+)"),
            alias = Regex("\\b(?:val|var)\\s+(\\w+)\\s*(?::\\s*[\\w?<>]+\\s*)?=\\s*([\\w.?()]+)\\s*$"),
            declarationWords = setOf("val", "var", "fun", "class", "object"),
        )

        val TYPESCRIPT = LanguageProfile(
            name = "typescript",
            keywords = listOf("const", "let", "var", "function", "if", "else", "for", "while", "return", "true", "false",
                "null", "undefined", "export", "import", "class", "interface", "type", "of", "in", "new", "typeof",
                "break", "continue"),
            snippets = lifecycleSnippets { n, dt -> "export function $n(ctx: any${if (dt.isEmpty()) "" else ", $dt: number"}) {\n  \$0\n}" } +
                listOf(Snippet("if", "if (\$0) {\n}", "if statement"), Snippet("for", "for (let i = 0; i < \$0; i++) {\n}", "counted loop")),
            lineComment = "//", blockComment = "/*" to "*/", quotes = "\"'`",
            handlerParam = Regex("function\\s+on(?:Create|Update|FixedUpdate|Destroy)\\s*\\(\\s*(\\w+)"),
            declaration = Regex("\\b(?:const|let|var|function|class|interface)\\s+(\\w+)"),
            alias = Regex("\\b(?:const|let|var)\\s+(\\w+)\\s*(?::\\s*[\\w<>\\[\\]|]+\\s*)?=\\s*([\\w.?()]+)\\s*;?\\s*$"),
            declarationWords = setOf("const", "let", "var", "function", "class", "interface", "type"),
            adaptIndex = ::adaptDynamic,
            renderSignature = ::tsSignature,
        )

        val LUA = LanguageProfile(
            name = "lua",
            keywords = listOf("and", "break", "do", "else", "elseif", "end", "false", "for", "function", "if", "in",
                "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while"),
            snippets = lifecycleSnippets { n, dt -> "function $n(ctx${if (dt.isEmpty()) "" else ", $dt"})\n  \$0\nend" } +
                listOf(Snippet("if", "if \$0 then\nend", "if statement"), Snippet("for", "for i = 1, \$0 do\nend", "counted loop")),
            lineComment = "--", blockComment = "--[[" to "]]",
            handlerParam = Regex("function\\s+on(?:Create|Update|FixedUpdate|Destroy)\\s*\\(\\s*(\\w+)"),
            declaration = Regex("\\b(?:local\\s+function|local|function)\\s+(\\w+)"),
            alias = Regex("\\blocal\\s+(\\w+)\\s*=\\s*([\\w.()]+)\\s*$"),
            declarationWords = setOf("local", "function"),
            adaptIndex = ::adaptDynamic,
            renderSignature = ::luaSignature,
        )
    }
}
