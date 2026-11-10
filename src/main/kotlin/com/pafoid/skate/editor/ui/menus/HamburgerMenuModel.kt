package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.data.EditorShortcut
import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.imgui.data.Icons

/**
 * The hamburger menu tree (EDT-51 §2.7), UI-free. The menu builders draw exactly these entries in this order and only
 * decide, per item [id], what it does and whether it is enabled or checked; so the order lives (and is tested) here.
 */
object HamburgerMenuModel {
    enum class Kind { ACTION, CHECK, RADIO }

    sealed interface Entry

    /** A clickable item. Its tooltip is [tooltipKey] (by default `<labelKey>.tooltip`). */
    data class Item(
        val id: String,
        val labelKey: String,
        val icon: String? = null,
        val shortcut: EditorShortcut? = null,
        val kind: Kind = Kind.ACTION,
        val tooltipKey: String = "$labelKey.tooltip",
    ) : Entry

    /** A submenu: fixed [entries], or (when empty) contents the builder fills at draw time (recent projects, windows). */
    data class Submenu(val id: String, val labelKey: String, val entries: List<Entry> = emptyList()) : Entry

    data object Separator : Entry

    data class Menu(val id: String, val labelKey: String, val entries: List<Entry>) {
        val items: List<Item> get() = entries.flatMap { flatten(it) }
    }

    private fun flatten(entry: Entry): List<Item> = when (entry) {
        is Item -> listOf(entry)
        is Submenu -> entry.entries.flatMap { flatten(it) }
        Separator -> emptyList()
    }

    val FILE = Menu(
        "file", "menu.file",
        listOf(
            Item("new_project", "menu.file.new_project", Icons.PLUS),
            Item("open_project", "menu.file.open_project_menu", Icons.FOLDER_OPEN, EditorShortcuts.OPEN_PROJECT),
            Submenu("recent", "menu.file.recent_projects"),
            Item("save_project", "menu.file.save_project", Icons.SAVE),
            Item("save_project_as", "menu.file.save_project_as", Icons.SAVE),
            Item("close_project", "menu.file.close_project", Icons.WINDOW_CLOSE),
            Separator,
            Item("new_scene", "menu.file.new_scene", Icons.PLUS, EditorShortcuts.NEW_SCENE),
            Item("save_scene", "menu.file.save_scene", Icons.SAVE, EditorShortcuts.SAVE_SCENE),
            Item("save_scene_as", "menu.file.save_scene_as", Icons.SAVE, EditorShortcuts.SAVE_SCENE_AS),
            Item("import_scene", "menu.file.import_scene", Icons.IMPORT_FILE),
            Separator,
            Item("export", "menu.file.export_project", Icons.EXPORT),
            Separator,
            Item("quit", "menu.file.quit", Icons.SIGN_OUT),
        ),
    )

    val EDIT = Menu(
        "edit", "menu.edit",
        listOf(
            Item("undo", "menu.edit.undo", Icons.UNDO, EditorShortcuts.UNDO),
            Item("redo", "menu.edit.redo", Icons.REDO, EditorShortcuts.REDO),
            Separator,
            Item("cut", "menu.edit.cut", Icons.CUT, EditorShortcuts.CUT),
            Item("copy", "menu.edit.copy", Icons.COPY, EditorShortcuts.COPY),
            Item("paste", "menu.edit.paste", Icons.PASTE, EditorShortcuts.PASTE),
            Item("duplicate", "menu.edit.duplicate", Icons.CLONE, EditorShortcuts.DUPLICATE),
            Item("delete", "menu.edit.delete", Icons.TRASH, EditorShortcuts.DELETE),
            Separator,
            Item("search", "menu.edit.search", Icons.SEARCH, EditorShortcuts.SEARCH),
        ),
    )

    val VIEW = Menu(
        "view", "menu.view",
        listOf(
            Submenu("windows", "menu.view.windows"),
            Item("open_all", "menu.view.windows.open_all", Icons.WINDOW_RESTORE),
            Item("close_all", "menu.view.windows.close_all", Icons.WINDOW_CLOSE),
            Item("reset_layout", "menu.view.reset_layout", Icons.ARROW_ROTATE),
        ),
    )

    val PLAY = Menu(
        "play", "menu.play",
        listOf(
            Item("play_pause", "menu.play.play_pause", Icons.PLAY, EditorShortcuts.PLAY_TOGGLE),
            Item("stop", "menu.play.stop", Icons.STOP),
            Item("reset", "menu.play.reset", Icons.ARROW_ROTATE),
        ),
    )

    const val LANGUAGE_PREFIX = "language_"
    const val UNITS_PREFIX = "units_"

    /** Editor languages, in menu order. */
    val LANGUAGES = listOf("en" to "settings.editor.language.english", "fr" to "settings.editor.language.french")

    val SETTINGS = Menu(
        "settings", "menu.settings",
        listOf(
            Submenu(
                "language", "menu.settings.language",
                LANGUAGES.map { (code, key) -> Item(LANGUAGE_PREFIX + code, key, kind = Kind.RADIO, tooltipKey = "menu.settings.language.tooltip") },
            ),
            Submenu(
                "units", "menu.settings.units",
                listOf(
                    Item(UNITS_PREFIX + "metric", "settings.unit.metric", kind = Kind.RADIO, tooltipKey = "menu.settings.units.tooltip"),
                    Item(UNITS_PREFIX + "imperial", "settings.unit.imperial", kind = Kind.RADIO, tooltipKey = "menu.settings.units.tooltip"),
                ),
            ),
            Item("show_tooltips", "menu.settings.show_tooltips", kind = Kind.CHECK),
            Item("show_gamepad_overlay", "menu.settings.show_gamepad_overlay", kind = Kind.CHECK),
            Separator,
            Item("editor_settings", "menu.settings.editor_settings", Icons.GEAR),
            Item("project_settings", "menu.settings.project_settings", Icons.SLIDERS),
            Item("keybindings", "menu.settings.keybindings", Icons.KEYBOARD),
        ),
    )

    val TOOLS = Menu(
        "tools", "menu.tools",
        listOf(
            Item("test_input", "menu.tools.test_input", Icons.MOUSE_POINTER, EditorShortcuts.INPUT_TESTING),
            Item("shell", "menu.tools.shell", Icons.TERMINAL),
        ),
    )

    val HELP = Menu(
        "help", "menu.help",
        listOf(
            Item("getting_started", "menu.help.getting_started", Icons.QUESTION_CIRCLE),
            Item("shortcuts", "menu.help.shortcuts", Icons.KEYBOARD),
            Item("about", "menu.help.about", Icons.INFO),
        ),
    )

    /** The hamburger menu's top-level menus, in order. */
    val MENUS = listOf(FILE, EDIT, VIEW, PLAY, SETTINGS, TOOLS, HELP)
}
