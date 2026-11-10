package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readBytes
import kotlin.io.path.readText

/**
 * EDT-53 / EDT-68 (docs/design/EDT-68.md): every registered editor window has a beginner help article in
 * `src/main/resources/editor/help/<locale>/<topic>.md` (AST-26: editor-only files ship in the jar) for English and French, where the topic is the window name without `window.`.
 * The general topics (Getting started, Keyboard shortcuts) exist too, every article is well-formed Markdown in the
 * subset the Help window renders, and every `[text](topic.md)` link points to an existing article.
 *
 * The structural check below stands in for the EDT-68 parser until `editor/help/HelpMarkdown` exists; switch
 * [articleProblems] to it then.
 */
class HelpArticleCoverageTest {

    private val root: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    private val helpDir: Path = root.resolve(HELP_DIR)

    private val articles: Map<String, Map<String, Path>> by lazy {
        LOCALES.associateWith { locale ->
            val dir = helpDir.resolve(locale)
            if (!Files.isDirectory(dir)) emptyMap()
            else Files.list(dir).use { s ->
                s.filter { it.isRegularFile() && it.name.endsWith(".md") }.toList()
            }.associateBy { it.name.removeSuffix(".md") }
        }
    }

    private val windowTopics: Map<String, String> by lazy {
        val windows = listOf("src/main/kotlin/com/pafoid/skate/editor", "src/main/kotlin/com/pafoid/skate/app").flatMap { dir ->
            Files.walk(root.resolve(dir)).use { s -> s.filter { it.isRegularFile() && it.name.endsWith(".kt") }.toList() }
        }.flatMap { WindowStandardGuardTest.findWindows(it.readText()) }.associateBy { it.className }
        val registered = WindowStandardGuardTest.registeredWindowClasses(root.resolve(REGISTRY).readText())
        registered.associateWith { cls ->
            windows[cls]?.windowName?.removePrefix("window.") ?: "<window name of $cls not found>"
        }
    }

    @Test
    fun HelpCoverage_FindsTheRegisteredWindows() {
        assertTrue(windowTopics.size >= 19, "found only $windowTopics")
        assertEquals("console", windowTopics["ConsoleWindow"])
        assertEquals("video_to_animation", windowTopics["VideoToAnimationWindow"])
    }

    @Test
    fun EveryRegisteredWindow_HasAnArticleInEveryLocale() {
        val missing = windowTopics.flatMap { (cls, topic) ->
            LOCALES.filter { topic !in articles.getValue(it) }.map { "$cls: $HELP_DIR/$it/$topic.md" }
        }
        assertTrue(missing.isEmpty(), "Every registered window needs a help article (EDT-53):\n" + missing.joinToString("\n"))
    }

    @Test
    fun GeneralTopics_ExistInEveryLocale() {
        val missing = LOCALES.flatMap { locale -> GENERAL_TOPICS.filter { it !in articles.getValue(locale) }.map { "$locale/$it.md" } }
        assertTrue(missing.isEmpty(), "Missing general help topics: $missing")
    }

    @Test
    fun Locales_HaveTheSameTopics() {
        val en = articles.getValue("en").keys
        val diff = LOCALES.drop(1).associateWith { (en - articles.getValue(it).keys) + (articles.getValue(it).keys - en) }
            .filterValues { it.isNotEmpty() }
        assertTrue(diff.isEmpty(), "Topics present in only one locale: $diff")
    }

    @Test
    fun EveryArticle_IsAWindowOrGeneralTopic() {
        val known = windowTopics.values.toSet() + GENERAL_TOPICS
        val orphans = articles.flatMap { (locale, byTopic) -> (byTopic.keys - known).map { "$locale/$it.md" } }
        assertTrue(orphans.isEmpty(), "Help articles no window or menu opens (typo, or a removed window?): $orphans")
    }

    @Test
    fun EveryArticle_ParsesAndLinksResolve() {
        val failures = articles.flatMap { (locale, byTopic) ->
            byTopic.flatMap { (topic, path) -> articleProblems(path.readBytes(), byTopic.keys).map { "$locale/$topic.md: $it" } }
        }
        assertTrue(failures.isEmpty(), "Malformed help articles:\n" + failures.joinToString("\n"))
    }

    @Test
    fun ArticleCheck_AcceptsAWellFormedArticle() {
        val md = """
            # Console

            Intro with **bold**, *italic* and `code`.

            ## How to use it

            1. Open [Profiler](profiler.md) or https://example.com.
               - nested item
            > a quote

            ```
            # not a heading inside code
            ```
        """.trimIndent()
        assertEquals(emptyList<String>(), articleProblems(md.toByteArray(), setOf("console", "profiler")))
    }

    @Test
    fun ArticleCheck_ReportsEachKindOfProblem() {
        assertEquals(listOf("does not start with a '# ' title"), articleProblems("Intro\n\n# Title\n".toByteArray(), emptySet()))
        assertEquals(listOf("more than one '# ' title (line 3)"), articleProblems("# A\n\n# B\n".toByteArray(), emptySet()))
        assertEquals(listOf("heading deeper than ### (line 3)"), articleProblems("# A\n\n#### Deep\n".toByteArray(), emptySet()))
        assertEquals(listOf("unclosed code fence"), articleProblems("# A\n\n```\ncode\n".toByteArray(), emptySet()))
        assertEquals(listOf("link to missing topic 'gone.md' (line 3)"), articleProblems("# A\n\nSee [x](gone.md).\n".toByteArray(), setOf("a")))
        assertEquals(listOf("empty link target (line 3)"), articleProblems("# A\n\nSee [x]().\n".toByteArray(), emptySet()))
        assertEquals(listOf("empty article"), articleProblems(ByteArray(0), emptySet()))
        assertEquals(listOf("not valid UTF-8"), articleProblems(byteArrayOf(0x23, 0x20, 0xC3.toByte(), 0x28), emptySet()))
    }

    companion object {
        const val HELP_DIR = "src/main/resources/editor/help"

        val LOCALES = listOf("en", "fr")

        /** Topics opened from the Help menu, not tied to a window (EDT-68 "Routing and menu"). */
        val GENERAL_TOPICS = setOf("getting_started", "shortcuts")

        private const val REGISTRY = "src/main/kotlin/com/pafoid/skate/editor/imgui/WindowRegistry.kt"

        private val HEADING = Regex("""^(#+)\s+\S""")
        private val LINK = Regex("""\[[^\]]*]\(([^)]*)\)""")

        /**
         * Problems that would make the EDT-68 renderer show a broken article: not UTF-8, empty, no single leading
         * `#` title, a heading level the subset does not support, an unclosed ``` fence, or a link to a missing topic.
         */
        fun articleProblems(bytes: ByteArray, topics: Set<String>): List<String> {
            val text = try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: CharacterCodingException) {
                return listOf("not valid UTF-8")
            }
            if (text.isBlank()) return listOf("empty article")
            val out = mutableListOf<String>()
            val lines = text.removePrefix("\uFEFF").lines()
            if (!lines.first { it.isNotBlank() }.startsWith("# ")) out += "does not start with a '# ' title"
            var inFence = false
            var titles = 0
            lines.forEachIndexed { i, line ->
                val n = i + 1
                if (line.trimStart().startsWith("```")) { inFence = !inFence; return@forEachIndexed }
                if (inFence) return@forEachIndexed
                HEADING.find(line)?.let { h ->
                    val level = h.groupValues[1].length
                    if (level == 1 && ++titles > 1) out += "more than one '# ' title (line $n)"
                    if (level > 3) out += "heading deeper than ### (line $n)"
                }
                LINK.findAll(line).forEach { m ->
                    val target = m.groupValues[1].trim()
                    when {
                        target.isEmpty() -> out += "empty link target (line $n)"
                        target.startsWith("http://") || target.startsWith("https://") -> Unit
                        target.removeSuffix(".md") !in topics || !target.endsWith(".md") ->
                            out += "link to missing topic '$target' (line $n)"
                    }
                }
            }
            if (inFence) out += "unclosed code fence"
            return out
        }
    }
}
