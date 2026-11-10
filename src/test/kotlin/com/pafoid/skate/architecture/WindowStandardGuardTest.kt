package com.pafoid.skate.architecture

import com.pafoid.skate.editor.imgui.WindowCategory
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
 * `beginWindow` (title with icon, tab menu with Help / Close / Reset position), draws the header strip with `?`
 * (`header(...)`, or `WindowHeader.helpButton` in its own toolbar), and has a help article
 * `editor/help/<locale>/<id>.md` in English and French (EDT-68; `HelpArticleCoverageTest` checks the articles). `EditorDialog` subclasses get beginWindow and the header from their base class. A modal host
 * (a window whose `imgui()` only renders an `EditorModal`: New Project, Switch Project) needs neither: a modal has no
 * tab or header strip (EDT-51 §2.1, EDT-64), but it still declares an icon and has help articles, and its modal names
 * the host as `helpWindow` so the modal shows the host's `?` (EDT-67).
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

    /** Help topics with an article, by locale (EDT-68). */
    private val articles: Map<String, Set<String>> by lazy {
        HelpArticleCoverageTest.LOCALES.associateWith { locale ->
            val dir = root.resolve(HelpArticleCoverageTest.HELP_DIR).resolve(locale)
            if (!Files.isDirectory(dir)) emptySet()
            else Files.list(dir).use { s -> s.map { it.name }.filter { it.endsWith(".md") }.map { it.removeSuffix(".md") }.toList() }.toSet()
        }
    }

    @Test
    fun Guard_FindsTheEditorWindows() {
        val names = windows.map { it.className }
        assertTrue(names.size >= 19, "found only $names") // 19 since EDT-52I deleted Input Testing
        assertTrue("ExportWizardWindow" in names && "ShellWindow" in names, names.toString())
    }

    @Test
    fun Windows_FollowTheStandard() {
        val failures = windows.filter { it.className !in ALLOW_LIST }
            .associate { it.className to violations(it, articles) }
            .filterValues { it.isNotEmpty() }
        assertTrue(failures.isEmpty(), "Windows not meeting the EDT-51 standard (see docs/design/EDT-51.md §2):\n" +
            failures.entries.joinToString("\n") { (name, v) -> "$name: ${v.joinToString("; ")}" })
    }

    @Test
    fun AllowList_OnlyHoldsWindowsThatStillNeedWork() {
        val byName = windows.associateBy { it.className }
        val unknown = ALLOW_LIST.filter { it !in byName }
        val done = ALLOW_LIST.filter { name -> byName[name]?.let { violations(it, articles).isEmpty() } == true }
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
            "en" to setOf("good", "popup"),
            "fr" to setOf("good"),
        )
        assertEquals(emptyList<String>(), violations(found[0], keys))
        assertEquals(5, violations(found[1], keys).size, violations(found[1], keys).toString())
        assertEquals(listOf("missing help article fr/popup.md"), violations(found[2], keys))
    }

    @Test
    fun Guard_AcceptsModalHostsWithoutHeader() {
        val src = """
            class Switcher(s: StringManager) : EditorWindow("window.switcher") {
                override val icon = Icons.FOLDER_OPEN
                override fun imgui() = modal.render()
                private val modal = object : EditorModal(s, Icons.FOLDER_OPEN) { override val helpWindow get() = this@Switcher }
            }
            class NotAHost(s: StringManager) : EditorWindow("window.not_a_host") {
                override val icon = Icons.FOLDER_OPEN
                override fun imgui() { modal.render(); other() }
                private val modal = object : EditorModal(s) { }
            }
        """.trimIndent()
        val (host, notHost) = findWindows(src)
        val keys = listOf("en", "fr").associateWith { setOf("switcher", "not_a_host") }
        assertEquals(emptyList<String>(), violations(host, keys))
        assertEquals(2, violations(notHost, keys).size, violations(notHost, keys).toString())
        val withoutHelp = findWindows(src.replace("{ override val helpWindow get() = this@Switcher }", "{ }")).first()
        assertEquals(listOf("modal host without a `?` (override val helpWindow in its EditorModal)"), violations(withoutHelp, keys))
    }

    /**
     * A registered window that is not a dialog must be reachable from View > Windows, i.e. have a [WindowCategory];
     * otherwise nothing can open it (Video to Animation and Asset Store were missed this way). Exempt windows are
     * listed, with the reason, in [NO_CATEGORY].
     */
    @Test
    fun EveryRegisteredDockableWindow_HasAWindowCategory() {
        val byClass = windows.associateBy { it.className }
        val registered = registeredWindowClasses(root.resolve(REGISTRY).readText())
        assertTrue(registered.size >= 19, "found only $registered in $REGISTRY")
        val unknown = registered.filter { it !in byClass }
        assertTrue(unknown.isEmpty(), "Registered windows the guard could not find: $unknown")
        val missing = registered.mapNotNull { byClass[it] }
            .filter { !it.isDialog && it.className !in NO_CATEGORY }
            .filter { w -> w.windowName == null || WindowCategory.of(w.windowName) == null }
            .map { "${it.className} (${it.windowName})" }
        assertTrue(missing.isEmpty(), "Windows missing from View > Windows; add them to WindowCategory.byWindow: $missing")
        val stale = NO_CATEGORY.filter { name -> name !in registered || byClass[name]?.windowName?.let { WindowCategory.of(it) } != null }
        assertTrue(stale.isEmpty(), "NO_CATEGORY entries that are not registered or have a category now: $stale")
    }

    /** EDT-67: the human found `?` on only some windows. Every registered window must draw one, with no exemption. */
    @Test
    fun EveryRegisteredWindow_HasAHelpButton() {
        val byClass = windows.associateBy { it.className }
        val registered = registeredWindowClasses(root.resolve(REGISTRY).readText())
        assertTrue(registered.size >= 19, "found only $registered in $REGISTRY")
        val missing = registered.filter { name -> byClass[name]?.let { helpButtonRoute(it) } == null }
        assertTrue(missing.isEmpty(), "Registered windows without a `?` help button (EDT-67): $missing")
        val dialog = root.resolve("src/main/kotlin/com/pafoid/skate/editor/imgui/EditorDialog.kt").readText()
        assertTrue(Regex("""\bheader\s*\(""").containsMatchIn(dialog), "EditorDialog no longer draws the header with `?`")
        val modal = root.resolve("src/main/kotlin/com/pafoid/skate/editor/imgui/EditorModal.kt").readText()
        assertTrue("helpWindow?.let" in modal && "WindowHeader.helpRow" in modal, "EditorModal no longer draws its host's `?`")
    }

    @Test
    fun Guard_FindsTheHelpButtonRoute() {
        val src = """
            class Docked(s: StringManager) : EditorWindow("window.docked") {
                override fun imgui() { beginWindow(s); header(s) { tools() }; ImGui.end() }
            }
            class Viewport(s: StringManager) : EditorWindow("window.viewport") {
                private val bar = Toolbar { WindowHeader.helpButton(s, this) }
            }
            class Settings(s: StringManager) : EditorDialog("window.settings", s) { }
            class Host(s: StringManager) : EditorWindow("window.host") {
                override fun imgui() = modal.render()
                private val modal = object : EditorModal(s) { override val helpWindow: EditorWindow get() = this@Host }
            }
            class BareHost(s: StringManager) : EditorWindow("window.bare_host") {
                override fun imgui() = modal.render()
                private val modal = object : EditorModal(s) { }
            }
            class Bare(s: StringManager) : EditorWindow("window.bare") {
                override fun imgui() { ImGui.begin("x"); ImGui.end() }
            }
        """.trimIndent()
        assertEquals(
            listOf("header", "helpButton", "EditorDialog header", "EditorModal.helpWindow", null, null),
            findWindows(src).map { helpButtonRoute(it) },
        )
    }

    @Test
    fun Guard_ReadsTheRegisteredWindowClasses() {
        val src = """
            class WindowRegistry(engine: Engine) {
                val windows: List<EditorWindow> = listOf(
                    SceneHierarchyWindow(engine, editor.clipboardService, projectManager::hasProject),
                    EditorSettingsWindow(a, b) { engine.eventSystem.publish(WindowAction.Show("window.keybindings")) },
                    VideoToAnimationWindow(engine, editor, projectManager),
                )
                val other = listOf(NotAWindow(1))
            }
        """.trimIndent()
        assertEquals(listOf("SceneHierarchyWindow", "EditorSettingsWindow", "VideoToAnimationWindow"), registeredWindowClasses(src))
    }

    class WindowClass(val className: String, val isDialog: Boolean, val windowName: String?, val source: String)

    companion object {
        /** Windows not converted to the standard yet, by EDT-52 chunk. Remove entries; never add any. */
        val ALLOW_LIST = emptySet<String>()

        private const val REGISTRY = "src/main/kotlin/com/pafoid/skate/editor/imgui/WindowRegistry.kt"

        /** Registered non-dialog windows deliberately absent from View > Windows. Keep each entry commented. */
        val NO_CATEGORY = setOf(
            // Search Anywhere palette: transient popup, opened from Edit > Search, its shortcut and the title-bar search.
            "SearchEverywhereWindow",
            // New Project modal host: opened from File > New Project, the no-project empty state and Switch Project.
            "ProjectWizardWindow",
            // Switch Project modal host: opened from the project name button in the menu bar.
            "ProjectSwitcherDialog",
        )

        /** Class names constructed in `val windows ... = listOf(...)` of the registry source, in order. */
        fun registeredWindowClasses(registrySource: String): List<String> {
            val start = Regex("""val\s+windows\b[^=]*=\s*listOf\(""").find(registrySource) ?: return emptyList()
            val open = start.range.last
            val end = skipParens(registrySource, open) ?: return emptyList()
            val body = registrySource.substring(open + 1, end - 1)
            // Top-level entries only: split on commas at depth 0 of () and {}.
            val entries = mutableListOf<String>()
            var depth = 0
            var from = 0
            body.forEachIndexed { i, c ->
                when (c) {
                    '(', '{' -> depth++
                    ')', '}' -> depth--
                    ',' -> if (depth == 0) { entries += body.substring(from, i); from = i + 1 }
                }
            }
            entries += body.substring(from)
            return entries.mapNotNull { Regex("""^\s*(\w+)\s*\(""").find(it)?.groupValues?.get(1) }
        }

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

        /** What [window] lacks; [articles] holds the help topics with an article, by locale. */
        fun violations(window: WindowClass, articles: Map<String, Set<String>>): List<String> {
            val out = mutableListOf<String>()
            if (!Regex("""override\s+val\s+icon\b""").containsMatchIn(window.source)) out += "no header icon (override val icon)"
            if (isModalHost(window.source) && !HELP_WINDOW.containsMatchIn(window.source)) {
                out += "modal host without a `?` (override val helpWindow in its EditorModal)"
            }
            if (!window.isDialog && !isModalHost(window.source)) {
                if (!Regex("""\bbeginWindow\s*\(""").containsMatchIn(window.source)) out += "does not open with beginWindow (tab menu, help)"
                if (!Regex("""\bheader\s*\(|\bhelpButton\s*\(""").containsMatchIn(window.source)) out += "no header strip with ? (header(...))"
            }
            val helpId = window.windowName?.removePrefix("window.")
            if (helpId == null) {
                out += "window name not found"
            } else {
                listOf("en", "fr").filter { helpId !in articles[it].orEmpty() }
                    .forEach { out += "missing help article $it/$helpId.md" }
            }
            return out
        }

        private val HELP_WINDOW = Regex("""override\s+val\s+helpWindow\b""")

        /**
         * How a window shows its `?` (EDT-67), or null when it does not: dialogs through `EditorDialog`'s header,
         * modal hosts through `EditorModal.helpWindow`, every other window through `header(...)` or
         * `WindowHeader.helpButton` in its own toolbar.
         */
        fun helpButtonRoute(window: WindowClass): String? = when {
            window.isDialog -> "EditorDialog header"
            isModalHost(window.source) -> if (HELP_WINDOW.containsMatchIn(window.source)) "EditorModal.helpWindow" else null
            Regex("""\bheader\s*\(""").containsMatchIn(window.source) -> "header"
            Regex("""\bhelpButton\s*\(""").containsMatchIn(window.source) -> "helpButton"
            else -> null
        }

        /** The window only renders an [EditorModal][com.pafoid.skate.editor.imgui.EditorModal] it owns. */
        fun isModalHost(source: String): Boolean =
            Regex(""":\s*EditorModal\s*\(""").containsMatchIn(source) &&
                Regex("""override\s+fun\s+imgui\s*\(\s*\)\s*(=\s*\w+\.render\(\)|\{\s*\w+\.render\(\)\s*})""").containsMatchIn(source)

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
