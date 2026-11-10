package com.pafoid.skate.architecture

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

/**
 * EDT-52 (standard in docs/design/EDT-51.md §2): every editor window and dialog declares a header icon, opens with
 * `beginWindow` (title with icon, tab menu with Help / Close / Reset position, help popover), draws the header strip
 * with `?` (`header(...)`, or `WindowHeader.helpButton` in its own toolbar), and has `help.<id>.what` / `.how` strings
 * in English and French. `EditorDialog` subclasses get beginWindow and the header from their base class. A modal host
 * (a window whose `imgui()` only renders an `EditorModal`: New Project, Switch Project) needs neither: a modal has no
 * tab or header strip (EDT-51 §2.1, EDT-64), but it still declares an icon and has help strings.
 *
 * [ALLOW_LIST] holds the windows not converted yet. Each EDT-52 chunk removes its windows; nobody adds any. EDT-52 is
 * done when it is empty.
 */
class WindowStandardGuardTest {

    private val root: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()

    private val windows: List<WindowClass> by lazy {
        listOf("src/main/kotlin/com/pafoid/skate/editor", "src/main/kotlin/com/pafoid/skate/app").flatMap { dir ->
            Files.walk(root.resolve(dir)).use { s -> s.filter { it.isRegularFile() && it.name.endsWith(".kt") }.toList() }
        }.flatMap { findWindows(it.readText()) }
    }

    private val helpKeys: Map<String, Set<String>> by lazy {
        val dir = root.resolve("assets/strings/editor")
        Files.list(dir).use { s -> s.filter { it.name.endsWith(".properties") }.toList() }
            .groupBy { if (it.name.removeSuffix(".properties").endsWith("_fr")) "fr" else "en" }
            .mapValues { (_, files) -> files.flatMap { keysOf(it.readText()) }.toSet() }
    }

    @Test
    fun Guard_FindsTheEditorWindows() {
        val names = windows.map { it.className }
        assertTrue(names.size >= 20, "found only $names")
        assertTrue("ExportWizardWindow" in names && "ShellWindow" in names, names.toString())
    }

    @Test
    fun Windows_FollowTheStandard() {
        val failures = windows.filter { it.className !in ALLOW_LIST }
            .associate { it.className to violations(it, helpKeys) }
            .filterValues { it.isNotEmpty() }
        assertTrue(failures.isEmpty(), "Windows not meeting the EDT-51 standard (see docs/design/EDT-51.md §2):\n" +
            failures.entries.joinToString("\n") { (name, v) -> "$name: ${v.joinToString("; ")}" })
    }

    @Test
    fun EveryRegisteredWindow_HasBeginnerHelp() {
        val failures = windows.mapNotNull { w ->
            val helpId = w.windowName?.removePrefix("window.")
            val missing = if (helpId == null) {
                mutableListOf("${w.className}: window name not found")
            } else {
                val m = mutableListOf<String>()
                listOf("en", "fr").forEach { locale ->
                    listOf("what", "how").forEach { part ->
                        val key = "help.$helpId.$part"
                        if (key !in helpKeys[locale].orEmpty()) m += "${w.className}: missing $key in $locale"
                    }
                }
                m
            }
            if (missing.isEmpty()) null else w.className to missing
        }
        val detail = failures.joinToString("\n") { (name, v) -> "$name: ${v.joinToString("; ")}" }
        assertTrue(failures.isEmpty(), "Every registered editor window needs beginner help (what/how in English and French):\n" + detail)
    }

    @Test
    fun AllowList_OnlyHoldsWindowsThatStillNeedWork() {
        val byName = windows.associateBy { it.className }
        val unknown = ALLOW_LIST.filter { it !in byName }
        val done = ALLOW_LIST.filter { name -> byName[name]?.let { violations(it, helpKeys).isEmpty() } == true }
        assertTrue(unknown.isEmpty(), "Allow-listed windows that no longer exist: $unknown")
        assertTrue(done.isEmpty(), "These windows meet the standard now; remove them from ALLOW_LIST: $done")
    }

    @Test
    fun Guard_DetectsMissingPartsAndAcceptsAConvertedWindow() {
        val src = """
            class Good(engine: Engine, private val x: Int = f(1, g(2))) : EditorWindow(NAME, true) {
                override val icon = Icons.LIST
                override fun imgui() {
                    beginWindow(strings)
                    header(strings) { }
                    ImGui.end()
                }
                companion object { const val NAME = "window.good" }
            }
            abstract class Base(name: String) : EditorWindow(name)
            class Bad(
                engine: Engine,
            ) :
                EditorWindow("window.bad") {
                override fun imgui() { ImGui.begin("x") }
            }
            class Popup(s: StringManager) : EditorDialog("window.popup", s) {
                override val icon: String = Icons.GEAR
            }
        """.trimIndent()
        val found = findWindows(src)
        assertEquals(listOf("Good", "Bad", "Popup"), found.map { it.className })
        assertEquals(listOf("window.good", "window.bad", "window.popup"), found.map { it.windowName })
        val keys = mapOf(
            "en" to setOf("help.good.what", "help.good.how", "help.popup.what", "help.popup.how"),
            "fr" to setOf("help.good.what", "help.good.how", "help.popup.what"),
        )
        assertEquals(emptyList<String>(), violations(found[0], keys))
        assertEquals(7, violations(found[1], keys).size, violations(found[1], keys).toString())
        assertEquals(listOf("missing help.popup.how in fr"), violations(found[2], keys))
    }

    @Test
    fun Guard_AcceptsModalHostsWithoutHeader() {
        val src = """
            class Switcher(s: StringManager) : EditorWindow("window.switcher") {
                override val icon = Icons.FOLDER_OPEN
                override fun imgui() = modal.render()
                private val modal = object : EditorModal(s, Icons.FOLDER_OPEN) { }
            }
            class NotAHost(s: StringManager) : EditorWindow("window.not_a_host") {
                override val icon = Icons.FOLDER_OPEN
                override fun imgui() { modal.render(); other() }
                private val modal = object : EditorModal(s) { }
            }
        """.trimIndent()
        val (host, notHost) = findWindows(src)
        val keys = listOf("en", "fr").associateWith { setOf("help.switcher.what", "help.switcher.how", "help.not_a_host.what", "help.not_a_host.how") }
        assertEquals(emptyList<String>(), violations(host, keys))
        assertEquals(2, violations(notHost, keys).size, violations(notHost, keys).toString())
    }

    class WindowClass(val className: String, val isDialog: Boolean, val windowName: String?, val source: String)

    companion object {
        /** Windows not converted to the standard yet, by EDT-52 chunk. Remove entries; never add any. */
        val ALLOW_LIST = setOf(
            // C: project & assets
            "ProjectWindow", "AssetBrowserWindow",
            // H: domain windows
            "AudioInspectorWindow", "ScriptEditorWindow",
            // I: input testing move (the window is deleted)
            "InputTestingWindow",
            // EDT-54: the Render Graph rewrite adopts the standard
            "RenderGraphWindow",
        )

        private val CLASS = Regex("""(?<![\w.])(abstract\s+|open\s+)?class\s+(\w+)""")
        private val BASE = Regex("""^\s*:\s*(EditorWindow|EditorDialog)\s*\(""")
        private val STRING = Regex("""^\s*"([^"]+)"""")
        private val CONST = Regex("""^\s*(\w+)""")

        fun findWindows(source: String): List<WindowClass> = CLASS.findAll(source).mapNotNull { m ->
            if (m.groupValues[1].isNotBlank()) return@mapNotNull null
            var i = m.range.last + 1
            while (i < source.length && source[i].isWhitespace()) i++
            if (i < source.length && source[i] == '(') i = skipParens(source, i) ?: return@mapNotNull null
            val base = BASE.find(source.substring(i)) ?: return@mapNotNull null
            val args = source.substring(i + base.range.last + 1)
            val windowName = STRING.find(args)?.groupValues?.get(1)
                ?: CONST.find(args)?.groupValues?.get(1)?.let { constName ->
                    Regex("""const\s+val\s+$constName\s*=\s*"([^"]+)"""").find(source)?.groupValues?.get(1)
                }
            WindowClass(m.groupValues[2], base.groupValues[1] == "EditorDialog", windowName, classBody(source, i))
        }.toList()

        fun violations(window: WindowClass, helpKeys: Map<String, Set<String>>): List<String> {
            val out = mutableListOf<String>()
            if (!Regex("""override\s+val\s+icon\b""").containsMatchIn(window.source)) out += "no header icon (override val icon)"
            if (!window.isDialog && !isModalHost(window.source)) {
                if (!Regex("""\bbeginWindow\s*\(""").containsMatchIn(window.source)) out += "does not open with beginWindow (tab menu, help)"
                if (!Regex("""\bheader\s*\(|\bhelpButton\s*\(""").containsMatchIn(window.source)) out += "no header strip with ? (header(...))"
            }
            val helpId = window.windowName?.removePrefix("window.")
            if (helpId == null) {
                out += "window name not found"
            } else {
                listOf("en", "fr").forEach { locale ->
                    listOf("what", "how").map { "help.$helpId.$it" }
                        .filter { it !in helpKeys[locale].orEmpty() }
                        .forEach { out += "missing $it in $locale" }
                }
            }
            return out
        }

        /** The window only renders an [EditorModal][com.pafoid.skate.editor.imgui.EditorModal] it owns. */
        fun isModalHost(source: String): Boolean =
            Regex(""":\s*EditorModal\s*\(""").containsMatchIn(source) &&
                Regex("""override\s+fun\s+imgui\s*\(\s*\)\s*(=\s*\w+\.render\(\)|\{\s*\w+\.render\(\)\s*})""").containsMatchIn(source)

        private fun keysOf(text: String): List<String> = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .map { it.substringBefore('=').trim() }

        private fun skipParens(src: String, open: Int): Int? {
            var depth = 0
            for (i in open until src.length) {
                when (src[i]) {
                    '(' -> depth++
                    ')' -> if (--depth == 0) return i + 1
                }
            }
            return null
        }

        /** From the class header to its matching closing brace (or the end of the file). */
        private fun classBody(src: String, from: Int): String {
            val open = src.indexOf('{', from).takeIf { it >= 0 } ?: return src.substring(from)
            var depth = 0
            for (i in open until src.length) {
                when (src[i]) {
                    '{' -> depth++
                    '}' -> if (--depth == 0) return src.substring(from, i + 1)
                }
            }
            return src.substring(from)
        }
    }
}
