package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.InputBinding
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.lwjgl.glfw.GLFW
import java.io.File
import java.util.Properties
import kotlin.reflect.full.memberProperties

/** EDT-10: one shortcut catalog for the handler, menus, tooltips and the Key Bindings window. */
class EditorShortcutsTest {

    @Test
    fun Defaults_HaveNoConflicts() {
        assertEquals(emptyList<Pair<EditorShortcut, EditorShortcut>>(), EditorShortcuts.conflicts(EditorInputMappings()))
    }

    @Test
    fun SameKeyOnTwoGlobalShortcuts_IsAConflict() {
        val mappings = EditorInputMappings(focusSelected = InputBinding(keyboardKey = GLFW.GLFW_KEY_DELETE))
        assertEquals(listOf(EditorShortcuts.DELETE), EditorShortcuts.conflictsOf(EditorShortcuts.FOCUS, mappings))
    }

    @Test
    fun HierarchyKeys_DoNotConflictWithGlobalKeys() {
        // Home selects the first object in the focused hierarchy and resets the camera elsewhere.
        val m = EditorInputMappings()
        assertEquals(EditorShortcuts.SELECT_FIRST.combo(m), EditorShortcuts.CAMERA_RESET.combo(m))
        assertTrue(EditorShortcuts.conflictsOf(EditorShortcuts.CAMERA_RESET, m).isEmpty())
    }

    @Test
    fun ModifiersAreFixed_AndPartOfTheCombo() {
        val m = EditorInputMappings()
        assertEquals("Ctrl+D", EditorShortcuts.DUPLICATE.display(m))
        assertEquals("D", EditorShortcuts.CAMERA_RIGHT.display(m))
        assertEquals("Ctrl+Shift+Z", EditorShortcuts.REDO_ALT.display(m))
        assertFalse(EditorShortcuts.SAVE_SCENE.combo(m) == EditorShortcuts.SAVE_SCENE_AS.combo(m))
    }

    @Test
    fun Rebinding_ChangesTheDisplayedCombo() {
        val m = EditorInputMappings()
        EditorShortcuts.FOCUS.binding!!.set(m, InputBinding(keyboardKey = GLFW.GLFW_KEY_F3))
        assertEquals("F3", EditorShortcuts.FOCUS.display(m))
    }

    @Test
    fun EveryBinding_IsInTheCatalog_AndResetRestoresAll() {
        val bindings = EditorInputMappings::class.memberProperties.filter { it.returnType.classifier == InputBinding::class }.map { it.name }.toSet()
        val catalogued = EditorShortcuts.rebindable.mapNotNull { it.binding?.name }.toSet()
        assertEquals(bindings, catalogued, "every key in EditorInputMappings needs a catalog entry (label, scope) and vice versa")

        val m = EditorInputMappings()
        EditorShortcuts.rebindable.forEach { it.binding!!.set(m, InputBinding(keyboardKey = GLFW.GLFW_KEY_F9)) }
        m.resetToDefault()
        assertEquals(EditorInputMappings(), m)
    }

    @Test
    fun EveryShortcut_HasALabelInBothLanguages() {
        val files = File("assets/strings/editor").listFiles { f -> f.extension == "properties" }!!.toList()
        files.partition { it.name.endsWith("_fr.properties") }.toList().forEach { group ->
            val props = Properties().apply { group.forEach { f -> f.reader(Charsets.UTF_8).use { load(it) } } }
            val missing = EditorShortcuts.all.map { it.labelKey }.filter { props.getProperty(it) == null }
            assertTrue(missing.isEmpty(), "${group.first().name}...: missing $missing")
        }
    }

    @Test
    fun KeyNames_AreReadable() {
        assertEquals("Del", KeyNames.name(GLFW.GLFW_KEY_DELETE))
        assertEquals("F5", KeyNames.name(GLFW.GLFW_KEY_F5))
        assertEquals("7", KeyNames.name(GLFW.GLFW_KEY_7))
        assertEquals("Q", KeyNames.name(GLFW.GLFW_KEY_Q))
    }

    @Test
    fun FocusClaims_LastOneFrame() {
        val combo = EditorShortcuts.COPY.combo(EditorInputMappings())
        EditorFocus.claim(combo)
        assertFalse(EditorFocus.isClaimed(combo), "a claim applies from the next frame")
        EditorFocus.endFrame()
        assertTrue(EditorFocus.isClaimed(combo))
        EditorFocus.endFrame()
        assertFalse(EditorFocus.isClaimed(combo))
    }
}
