package com.pafoid.skate.architecture

import com.pafoid.skate.editor.imgui.data.ActionIcons
import com.pafoid.skate.editor.imgui.data.Icons
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-10: a control for a common action (delete, duplicate, save, ...) uses that action's icon everywhere. */
class ActionIconsGuardTest {
    private val editor = File("src/main/kotlin/com/pafoid/skate/editor")
    private val iconNames: Map<String, String> =
        Icons::class.java.declaredFields.filter { it.type == String::class.java }.associate { it.name to (it.get(null) as String) }

    private val iconOnLine = Regex("""Icons\.(\w+)""")
    private val keyOnLine = Regex(""""((?:menu|context|btn|search\.action|shell|lbl|window)\.[\w.]+)"""")

    /** (location, key, icon name) for each control that names a key and exactly one icon. */
    private fun controls(): List<Triple<String, String, String>> = editor.walkTopDown()
        .filter { it.isFile && it.extension == "kt" && it.name != "ActionIcons.kt" }
        .flatMap { file ->
            val lines = file.readLines()
            lines.indices.mapNotNull { i ->
                val line = lines[i]
                // Search actions put the key and the icon on nearby lines of one entry.
                val window = if ("displayNameKey" in line) lines.subList(i, minOf(lines.size, i + 5)).joinToString(" ") else line
                val icons = iconOnLine.findAll(window).map { it.groupValues[1] }.toSet()
                val keys = keyOnLine.findAll(window).map { it.groupValues[1] }.filter { !it.endsWith(".tooltip") && !it.endsWith(".desc") }.toList()
                if (icons.size != 1 || keys.isEmpty()) null
                else Triple("${file.relativeTo(editor).invariantSeparatorsPath}:${i + 1}", keys.first(), icons.single())
            }
        }.toList()

    @Test
    fun ActionControls_UseTheActionsIcon() {
        val wrong = controls().mapNotNull { (where, key, icon) ->
            val verb = ActionIcons.verbOf(key) ?: return@mapNotNull null
            val expected = ActionIcons.byVerb.getValue(verb)
            if (iconNames[icon] == expected) null
            else "$where: $key uses Icons.$icon; '$verb' uses Icons.${iconNames.entries.first { it.value == expected }.key}"
        }
        assertTrue(wrong.isEmpty(), wrong.joinToString("\n"))
    }

    @Test
    fun Guard_FindsTheKnownControls() {
        val found = controls().map { it.second }
        assertTrue("menu.file.quit" in found && "context.hierarchy.duplicate" in found, "scanner lost track of menu items")
        assertEquals("duplicate", ActionIcons.verbOf("context.viewport.duplicate"))
        assertEquals("close", ActionIcons.verbOf("context.scene_tab.close_all"))
    }
}
