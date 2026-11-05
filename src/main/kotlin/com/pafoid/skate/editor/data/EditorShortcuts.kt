package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.InputBinding
import org.lwjgl.glfw.GLFW
import kotlin.reflect.KMutableProperty1

/** A key with its modifiers. Modifiers must match exactly: Ctrl+Shift+S is not Ctrl+S. */
data class KeyCombo(val key: Int, val ctrl: Boolean = false, val shift: Boolean = false) {
    fun display(): String = buildString {
        if (ctrl) append("Ctrl+")
        if (shift) append("Shift+")
        append(KeyNames.name(key))
    }
}

/** Where a shortcut fires. Global and camera keys compete with each other; hierarchy keys only fire there. */
enum class ShortcutScope { GLOBAL, CAMERA, HIERARCHY }

/** Tab of the Key Bindings window. */
enum class ShortcutGroup(val labelKey: String) {
    EDITOR("tab.keybindings.editor"),
    CAMERA("tab.keybindings.camera"),
    HIERARCHY("tab.keybindings.hierarchy"),
}

/**
 * One editor shortcut (EDT-10). A rebindable one reads its key from [binding] in [EditorInputMappings]; its
 * modifiers ([ctrl], [shift]) are fixed. A fixed one always uses [fixedKey].
 */
class EditorShortcut internal constructor(
    val id: String,
    val labelKey: String,
    val group: ShortcutGroup,
    val scope: ShortcutScope = ShortcutScope.GLOBAL,
    val binding: KMutableProperty1<EditorInputMappings, InputBinding>? = null,
    private val fixedKey: Int = GLFW.GLFW_KEY_UNKNOWN,
    val ctrl: Boolean = false,
    val shift: Boolean = false,
) {
    val rebindable: Boolean get() = binding != null

    fun combo(mappings: EditorInputMappings = EditorShortcuts.mappings()): KeyCombo =
        KeyCombo(binding?.get(mappings)?.keyboardKey ?: fixedKey, ctrl, shift)

    /** Text for menus and tooltips, e.g. `Ctrl+D`. */
    fun display(mappings: EditorInputMappings = EditorShortcuts.mappings()): String = combo(mappings).display()

    override fun toString() = id
}

/**
 * Every editor shortcut, in one place (EDT-10). The input handler, menus, tooltips and the Key Bindings window read
 * combos from here, so a rebinding shows up everywhere and conflicts can be found.
 */
object EditorShortcuts {
    /** Where the live mappings come from; the editor points this at its settings on start. */
    @Volatile
    var mappings: () -> EditorInputMappings = { EditorInputMappings() }

    private fun key(id: String, label: String, group: ShortcutGroup, binding: KMutableProperty1<EditorInputMappings, InputBinding>, ctrl: Boolean = false, scope: ShortcutScope = ShortcutScope.GLOBAL) =
        EditorShortcut(id, label, group, scope, binding, ctrl = ctrl)

    private fun fixed(id: String, label: String, key: Int, ctrl: Boolean = true, shift: Boolean = false) =
        EditorShortcut(id, label, ShortcutGroup.EDITOR, fixedKey = key, ctrl = ctrl, shift = shift)

    // Fixed (standard) shortcuts.
    val UNDO = fixed("undo", "menu.edit.undo", GLFW.GLFW_KEY_Z)
    val REDO = fixed("redo", "menu.edit.redo", GLFW.GLFW_KEY_Y)
    val REDO_ALT = fixed("redo_alt", "menu.edit.redo", GLFW.GLFW_KEY_Z, shift = true)
    val CUT = fixed("cut", "menu.edit.cut", GLFW.GLFW_KEY_X)
    val COPY = fixed("copy", "menu.edit.copy", GLFW.GLFW_KEY_C)
    val PASTE = fixed("paste", "menu.edit.paste", GLFW.GLFW_KEY_V)
    val NEW_SCENE = fixed("new_scene", "menu.file.new_scene", GLFW.GLFW_KEY_N)
    val SAVE_SCENE = fixed("save_scene", "menu.file.save_scene", GLFW.GLFW_KEY_S)
    val SAVE_SCENE_AS = fixed("save_scene_as", "menu.file.save_scene_as", GLFW.GLFW_KEY_S, shift = true)
    val OPEN_PROJECT = fixed("open_project", "menu.file.open_project_menu", GLFW.GLFW_KEY_O)
    val INPUT_TESTING = fixed("input_testing", "menu.input.testing", GLFW.GLFW_KEY_I, shift = true)

    // Rebindable shortcuts.
    val SEARCH = key("search", "lbl.keybindings.search", ShortcutGroup.EDITOR, EditorInputMappings::openSearchWindow, ctrl = true)
    val DUPLICATE = key("duplicate", "lbl.hierarchy.duplicate", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyDuplicate, ctrl = true)
    val PLAY_TOGGLE = key("play_toggle", "lbl.keybindings.play_toggle", ShortcutGroup.EDITOR, EditorInputMappings::playToggle)
    val FULLSCREEN = key("fullscreen", "lbl.keybindings.fullscreen", ShortcutGroup.EDITOR, EditorInputMappings::toggleFullScreen)
    val GIZMO_SELECT = key("gizmo_select", "lbl.keybindings.select", ShortcutGroup.EDITOR, EditorInputMappings::gizmoSelect)
    val GIZMO_TRANSLATE = key("gizmo_translate", "lbl.keybindings.translate", ShortcutGroup.EDITOR, EditorInputMappings::gizmoTranslate)
    val GIZMO_ROTATE = key("gizmo_rotate", "lbl.keybindings.rotate", ShortcutGroup.EDITOR, EditorInputMappings::gizmoRotate)
    val GIZMO_SCALE = key("gizmo_scale", "lbl.keybindings.scale", ShortcutGroup.EDITOR, EditorInputMappings::gizmoScale)
    val MEASURE = key("measure", "lbl.keybindings.measure", ShortcutGroup.EDITOR, EditorInputMappings::measureTool)
    val DESELECT = key("deselect", "lbl.keybindings.deselect", ShortcutGroup.EDITOR, EditorInputMappings::deselectAll)
    val FOCUS = key("focus", "lbl.keybindings.focus", ShortcutGroup.EDITOR, EditorInputMappings::focusSelected)

    val CAMERA_FORWARD = key("camera_forward", "lbl.keybindings.move_forward", ShortcutGroup.CAMERA, EditorInputMappings::moveForward, scope = ShortcutScope.CAMERA)
    val CAMERA_BACKWARD = key("camera_backward", "lbl.keybindings.move_backward", ShortcutGroup.CAMERA, EditorInputMappings::moveBackward, scope = ShortcutScope.CAMERA)
    val CAMERA_LEFT = key("camera_left", "lbl.keybindings.move_left", ShortcutGroup.CAMERA, EditorInputMappings::moveLeft, scope = ShortcutScope.CAMERA)
    val CAMERA_RIGHT = key("camera_right", "lbl.keybindings.move_right", ShortcutGroup.CAMERA, EditorInputMappings::moveRight, scope = ShortcutScope.CAMERA)
    val CAMERA_UP = key("camera_up", "lbl.keybindings.move_up", ShortcutGroup.CAMERA, EditorInputMappings::moveUp, scope = ShortcutScope.CAMERA)
    val CAMERA_DOWN = key("camera_down", "lbl.keybindings.move_down", ShortcutGroup.CAMERA, EditorInputMappings::moveDown, scope = ShortcutScope.CAMERA)
    val CAMERA_RESET = key("camera_reset", "lbl.keybindings.camera_reset", ShortcutGroup.CAMERA, EditorInputMappings::cameraReset)

    val CREATE_EMPTY = key("create_empty", "lbl.hierarchy.create_new", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyCreateNew)
    val DELETE = key("delete", "lbl.hierarchy.delete", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyDelete)
    val RENAME = key("rename", "lbl.hierarchy.rename", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyRename)
    val TOGGLE_VISIBILITY = key("toggle_visibility", "lbl.hierarchy.toggle_visibility", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyToggleVisibility)
    val TOGGLE_LOCK = key("toggle_lock", "lbl.hierarchy.toggle_lock", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyToggleLock)
    val NAVIGATE_UP = key("navigate_up", "lbl.hierarchy.navigate_up", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyNavigateUp, scope = ShortcutScope.HIERARCHY)
    val NAVIGATE_DOWN = key("navigate_down", "lbl.hierarchy.navigate_down", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchyNavigateDown, scope = ShortcutScope.HIERARCHY)
    val SELECT_FIRST = key("select_first", "lbl.hierarchy.select_first", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchySelectFirst, scope = ShortcutScope.HIERARCHY)
    val SELECT_LAST = key("select_last", "lbl.hierarchy.select_last", ShortcutGroup.HIERARCHY, EditorInputMappings::hierarchySelectLast, scope = ShortcutScope.HIERARCHY)

    val fixedShortcuts: List<EditorShortcut> = listOf(UNDO, REDO, REDO_ALT, CUT, COPY, PASTE, NEW_SCENE, SAVE_SCENE, SAVE_SCENE_AS, OPEN_PROJECT, INPUT_TESTING)

    val rebindable: List<EditorShortcut> = listOf(
        SEARCH, PLAY_TOGGLE, FULLSCREEN, GIZMO_SELECT, GIZMO_TRANSLATE, GIZMO_ROTATE, GIZMO_SCALE, MEASURE, DESELECT, FOCUS,
        CAMERA_FORWARD, CAMERA_BACKWARD, CAMERA_LEFT, CAMERA_RIGHT, CAMERA_UP, CAMERA_DOWN, CAMERA_RESET,
        CREATE_EMPTY, DELETE, DUPLICATE, RENAME, TOGGLE_VISIBILITY, TOGGLE_LOCK, NAVIGATE_UP, NAVIGATE_DOWN, SELECT_FIRST, SELECT_LAST,
    )

    val all: List<EditorShortcut> = fixedShortcuts + rebindable

    /**
     * Pairs of shortcuts with the same combo that can fire together. Global and camera keys all compete; hierarchy
     * keys compete only with each other, because the focused hierarchy claims its keys ([EditorFocus]).
     */
    fun conflicts(mappings: EditorInputMappings = mappings()): List<Pair<EditorShortcut, EditorShortcut>> {
        fun bucket(s: EditorShortcut) = if (s.scope == ShortcutScope.HIERARCHY) 1 else 0
        val result = mutableListOf<Pair<EditorShortcut, EditorShortcut>>()
        for (i in all.indices) for (j in i + 1 until all.size) {
            val a = all[i]
            val b = all[j]
            if (bucket(a) == bucket(b) && a.combo(mappings) == b.combo(mappings)) result += a to b
        }
        return result
    }

    /** The shortcuts [shortcut] conflicts with. */
    fun conflictsOf(shortcut: EditorShortcut, mappings: EditorInputMappings = mappings()): List<EditorShortcut> =
        conflicts(mappings).mapNotNull { (a, b) -> if (a === shortcut) b else if (b === shortcut) a else null }
}

/**
 * Key combos claimed by the focused window for one frame (EDT-10). A window that handles a key itself (Ctrl+C in the
 * console, Ctrl+S in the script editor, Home in the hierarchy) claims it while focused, and the global shortcut
 * handler skips that combo, so one key press does one thing. Windows draw after the handler runs, so a claim applies
 * from the next frame.
 */
object EditorFocus {
    private var current: Set<KeyCombo> = emptySet()
    private val next = mutableSetOf<KeyCombo>()

    fun claim(vararg combos: KeyCombo) {
        next.addAll(combos)
    }

    fun isClaimed(combo: KeyCombo): Boolean = combo in current

    /** Called once per frame after every window drew. */
    fun endFrame() {
        current = next.toSet()
        next.clear()
    }
}

/** Display names of GLFW keys, independent of the keyboard layout and usable without a window. */
object KeyNames {
    private val special = mapOf(
        GLFW.GLFW_KEY_ESCAPE to "Esc", GLFW.GLFW_KEY_ENTER to "Enter", GLFW.GLFW_KEY_TAB to "Tab",
        GLFW.GLFW_KEY_BACKSPACE to "Backspace", GLFW.GLFW_KEY_INSERT to "Insert", GLFW.GLFW_KEY_DELETE to "Del",
        GLFW.GLFW_KEY_RIGHT to "Right", GLFW.GLFW_KEY_LEFT to "Left", GLFW.GLFW_KEY_DOWN to "Down", GLFW.GLFW_KEY_UP to "Up",
        GLFW.GLFW_KEY_PAGE_UP to "PgUp", GLFW.GLFW_KEY_PAGE_DOWN to "PgDn", GLFW.GLFW_KEY_HOME to "Home", GLFW.GLFW_KEY_END to "End",
        GLFW.GLFW_KEY_SPACE to "Space", GLFW.GLFW_KEY_LEFT_SHIFT to "Left Shift", GLFW.GLFW_KEY_RIGHT_SHIFT to "Right Shift",
        GLFW.GLFW_KEY_LEFT_CONTROL to "Left Ctrl", GLFW.GLFW_KEY_RIGHT_CONTROL to "Right Ctrl",
        GLFW.GLFW_KEY_LEFT_ALT to "Left Alt", GLFW.GLFW_KEY_RIGHT_ALT to "Right Alt",
        GLFW.GLFW_KEY_MINUS to "-", GLFW.GLFW_KEY_EQUAL to "=", GLFW.GLFW_KEY_COMMA to ",", GLFW.GLFW_KEY_PERIOD to ".",
        GLFW.GLFW_KEY_SLASH to "/", GLFW.GLFW_KEY_SEMICOLON to ";", GLFW.GLFW_KEY_APOSTROPHE to "'",
        GLFW.GLFW_KEY_LEFT_BRACKET to "[", GLFW.GLFW_KEY_RIGHT_BRACKET to "]", GLFW.GLFW_KEY_BACKSLASH to "\\",
        GLFW.GLFW_KEY_GRAVE_ACCENT to "`",
    )

    /** Modifier keys alone are not shortcuts (they are held for camera moves or combos). */
    val modifiers = setOf(
        GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT,
        GLFW.GLFW_KEY_LEFT_SUPER, GLFW.GLFW_KEY_RIGHT_SUPER,
    )

    fun name(key: Int): String = special[key] ?: when (key) {
        in GLFW.GLFW_KEY_A..GLFW.GLFW_KEY_Z, in GLFW.GLFW_KEY_0..GLFW.GLFW_KEY_9 -> key.toChar().toString()
        in GLFW.GLFW_KEY_F1..GLFW.GLFW_KEY_F25 -> "F${key - GLFW.GLFW_KEY_F1 + 1}"
        in GLFW.GLFW_KEY_KP_0..GLFW.GLFW_KEY_KP_9 -> "Num ${key - GLFW.GLFW_KEY_KP_0}"
        else -> "Key $key"
    }
}
