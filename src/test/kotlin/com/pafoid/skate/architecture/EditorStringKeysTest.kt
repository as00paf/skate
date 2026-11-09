package com.pafoid.skate.architecture

import com.pafoid.skate.editor.commands.CommandNames
import com.pafoid.skate.editor.commands.EnvironmentPropertyCommand
import com.pafoid.skate.editor.commands.editor.TogglePhysicsDebugCommand
import com.pafoid.skate.editor.commands.input.BindingOperation
import com.pafoid.skate.editor.commands.input.EditBindingsCommand
import com.pafoid.skate.editor.commands.input.SetDeadZonesCommand
import com.pafoid.skate.editor.settings.ShadowQuality
import com.pafoid.skate.editor.ui.windows.ActionBindingsModel
import com.pafoid.skate.editor.ui.windows.EditorSettingsWindow
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.StringTables
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.BindingProfile
import com.pafoid.skate.engine.input.DeadZoneSettings
import com.pafoid.skate.engine.input.InputMappings
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * EDT-50: every string key the editor references exists in the editor tables (`assets/strings/editor`), so no
 * `!!key!!` placeholder reaches the screen. French parity is enforced by `StringTablesTest`, so checking the English
 * tables covers every locale.
 *
 * The scan resolves keys from literals in: StringManager calls (`getString`, `label`, `tipped`, ...), file-local
 * helpers that prefix their `key` argument (`fun s(key: String) = strings.getString("component.X.$key")`, resolved
 * transitively), `*Key = "..."` assignments, key-typed constructors (`EditorWindow`, `EditorError`, ...) and enums
 * whose first parameter is a `*Key`. Keys built at runtime are checked from the code that builds them below.
 */
class EditorStringKeysTest {

    private val root: Path = Paths.get(System.getProperty("user.dir")).toAbsolutePath()
    private val editorKeys = StringTables.merged(File(Assets.Strings.EDITOR_STRINGS)).stringPropertyNames()

    private fun strings(locale: String = "en") = StringManager(mockk(relaxed = true), "en").apply {
        loadStrings(Assets.Strings.EDITOR_STRINGS)
        if (locale != "en") setLocale(locale)
    }

    @Test
    fun EditorSources_ReferenceOnlyExistingKeys() {
        val missing = listOf("src/main/kotlin/com/pafoid/skate/editor", "src/main/kotlin/com/pafoid/skate/app").flatMap { dir ->
            Files.walk(root.resolve(dir)).use { s -> s.filter { it.isRegularFile() && it.toString().endsWith(".kt") }.toList() }
        }.sorted().flatMap { file ->
            val rel = root.relativize(file).invariantSeparatorsPathString
            referencedKeys(file.readText()).filterNot { (_, key) -> exists(key, editorKeys) }.map { (line, key) -> "$rel:$line -> $key" }
        }
        assertTrue(missing.isEmpty(), "Keys missing from assets/strings/editor (add them to <area>.properties + _fr):\n" +
            missing.joinToString("\n"))
    }

    @Test
    fun RuntimeBuiltKeys_Exist() {
        val keys = GraphicsBackend.entries.map { it.labelKey } +
            ShadowQuality.entries.map { "settings.graphics.shadow_quality.${it.name.lowercase()}" } +
            ActionBindingsModel.MOUSE_BUTTON_KEYS
        assertEquals(emptyList<String>(), keys.filterNot { it in editorKeys })
    }

    @Test
    fun CommandHistory_NamesAndTargetsAreTranslated() {
        val fr = strings("fr")
        val map = ActionMap()
        val commands = listOf(
            EnvironmentPropertyCommand("command.environment.set_sun_color", null, { _: Int -> }, 0, 1),
            EditBindingsCommand(map, InputMappings(), BindingOperation.Reset(emptyList())),
            EditBindingsCommand(map, InputMappings(), BindingOperation.Clear("jump")),
            EditBindingsCommand(map, InputMappings(), BindingOperation.LoadProfile(BindingProfile("pro"))),
            SetDeadZonesCommand(DeadZoneSettings(), DeadZoneSettings(), DeadZoneSettings(), "Stick gauche"),
            SetDeadZonesCommand(DeadZoneSettings(), DeadZoneSettings(), DeadZoneSettings()),
        )
        commands.forEach { command ->
            val name = CommandNames.displayName(command, fr)
            assertFalse(name.startsWith("!!") || name.startsWith("command."), "untranslated name: $name")
        }
        assertEquals("Effacer les assignations de jump", CommandNames.displayName(commands[2], fr))
        assertEquals("Modifier la zone morte : Stick gauche", CommandNames.displayName(commands[4], fr))

        assertEquals("Raccourcis Clavier", CommandNames.targetName(commands[1], fr))
        assertEquals("Paramètres d'entrée", CommandNames.targetName(commands[4], fr))
        assertEquals("Vue du jeu", CommandNames.targetName(TogglePhysicsDebugCommand(mockk(relaxed = true)), fr))
    }

    @Test
    fun CommandHistory_ObjectTargetsAreShownAsTheyAre() {
        val command = EnvironmentPropertyCommand("command.environment.set_sun_color", "Player", { _: Int -> }, 0, 1)
        assertEquals("Player", CommandNames.targetName(command, strings()))
    }

    @Test
    fun BindingLabels_AreTranslated() {
        val fr = strings("fr")
        assertEquals("Souris bouton 4", ActionBindingsModel.label(ActionBinding.MouseButton(3), fr))
        assertEquals("Souris bouton 9", ActionBindingsModel.label(ActionBinding.MouseButton(8), fr))
        assertFalse(ActionBindingsModel.label(ActionBinding.GamepadAxis(0, -1f), fr).startsWith("!!"))
    }

    @Test
    fun Scanner_FindsKeysInCallsHelpersAssignmentsConstructorsAndEnums() {
        val src = """
            class W : EditorWindow("window.a") {
                fun s(key: String) = strings.getString("component.X.${'$'}key")
                fun tip(key: String) { ImGui.setTooltip(strings.getString("tip.x.${'$'}key")) }
                fun field(key: String, v: Boolean) { ImGui.checkbox("${'$'}{s(key)}##${'$'}key", v); tip(key) }
                fun body() {
                    ImGui.text(stringManager.getString("lbl.b"))
                    strings.tipped(ImGui.button(strings.label("btn.c")), "btn.c")
                    field("speed", true)
                    val n = strings.getQuantityString("lbl.items", 3)
                    EditorError("error.d.title")
                    EmptyState.Action(labelKey = "btn.e")
                    ImGui.text(s.getString("${'$'}prefix.dynamic"))
                    if (strings.hasString("probe.only")) Unit
                }
            }
            enum class Cat(val labelKey: String) { ONE("cat.one"), TWO("cat.two"); }
        """.trimIndent()
        val keys = referencedKeys(src).map { it.second }.toSet()
        assertEquals(
            setOf(
                "window.a", "lbl.b", "btn.c", "component.X.speed", "tip.x.speed", "lbl.items#plural", "error.d.title",
                "btn.e", "cat.one", "cat.two",
            ),
            keys,
        )
        assertTrue(exists("lbl.items#plural", setOf("lbl.items.one", "lbl.items.other")))
        assertFalse(exists("lbl.items#plural", setOf("lbl.items")))
    }

    companion object {
        private const val KEY = """"([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+)""""
        private const val PLURAL = "#plural"

        /** StringManager calls (and key-typed constructors) whose first literal argument, or the literal after it, is a key. */
        private val DIRECT = Regex(
            """(?<![\w])(getString|label|tipped|tippedWith|getQuantityString|tr|EditorWindow|EditorError|ShellPreset|Action)\(\s*(?:[^()"]*?,\s*)?(?:\w+\s*=\s*)?$KEY""",
        )
        private val ASSIGNED = Regex("""\b\w*Key\s*(?::\s*String\s*)?=\s*$KEY""")
        private val HELPER = Regex("""fun (\w+)\(key: String[^)]*\)""")
        private val KEY_ENUM = Regex("""enum class \w+\(\s*(?:private\s+)?val \w*Key: String""")
        private val ENUM_ENTRY = Regex("""\b[A-Z][A-Z0-9_]*\(\s*$KEY""")

        fun exists(key: String, keys: Set<String>): Boolean =
            if (key.endsWith(PLURAL)) "${key.removeSuffix(PLURAL)}.other" in keys else key in keys

        /** (line, key) for every key [source] references; quantity keys end with `#plural` (they need `.other`). */
        fun referencedKeys(source: String): List<Pair<Int, String>> {
            val out = ArrayList<Pair<Int, String>>()
            fun add(at: Int, key: String) { out += (source.substring(0, at).count { it == '\n' } + 1) to key }

            DIRECT.findAll(source).forEach { m ->
                add(m.range.first, if (m.groupValues[1] == "getQuantityString") m.groupValues[2] + PLURAL else m.groupValues[2])
            }
            ASSIGNED.findAll(source).forEach { add(it.range.first, it.groupValues[1]) }
            KEY_ENUM.findAll(source).forEach { e ->
                val start = source.indexOf('{', e.range.last).takeIf { it >= 0 } ?: return@forEach
                val end = source.indexOf(';', start).takeIf { it >= 0 } ?: source.indexOf('}', start)
                ENUM_ENTRY.findAll(source.substring(start, end)).forEach { add(start + it.range.first, it.groupValues[1]) }
            }
            val prefixes = helperPrefixes(source)
            prefixes.forEach { (name, pre) ->
                Regex("""(?<![\w.])$name\(\s*"([A-Za-z0-9_.]+)"""").findAll(source).forEach { m ->
                    pre.forEach { add(m.range.first, it + m.groupValues[1]) }
                }
            }
            return out.distinct()
        }

        /**
         * Prefixes each file-local `fun name(key: String, ...)` puts in front of `key` before looking it up. A helper
         * that hands `key` to another helper inherits that helper's prefixes; one that does not look it up is skipped.
         */
        private fun helperPrefixes(source: String): Map<String, Set<String>> {
            val bodies = HELPER.findAll(source).associate { m ->
                val next = HELPER.find(source, m.range.last)?.range?.first ?: source.length
                m.groupValues[1] to source.substring(m.range.last + 1, minOf(next, m.range.last + 1 + 400))
            }
            val direct = bodies.mapValues { (_, body) ->
                Regex("""(?:getString|label)\(\s*"([^"$]*)\${'$'}key[."]""").findAll(body).map { it.groupValues[1] }.toSet() +
                    (if (Regex("""\b(?:getString|label|tr)\(key\b""").containsMatchIn(body)) setOf("") else emptySet())
            }.toMutableMap()
            repeat(bodies.size) {
                bodies.forEach { (name, body) ->
                    bodies.keys.filter { it != name && Regex("""(?<![\w.])$it\(key\b""").containsMatchIn(body) }
                        .forEach { callee -> direct[name] = direct.getValue(name) + direct.getValue(callee) }
                }
            }
            return direct.filterValues { it.isNotEmpty() }
        }
    }
}
