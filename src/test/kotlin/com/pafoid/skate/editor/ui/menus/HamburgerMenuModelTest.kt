package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.ui.menus.HamburgerMenuModel.Item
import com.pafoid.skate.editor.ui.menus.HamburgerMenuModel.Separator
import com.pafoid.skate.editor.ui.menus.HamburgerMenuModel.Submenu
import com.pafoid.skate.engine.assets.Assets
import com.pafoid.skate.engine.core.StringTables
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/** EDT-52 G: the hamburger menu follows the approved tree of docs/design/EDT-51.md §2.7. */
class HamburgerMenuModelTest {
    /** A menu as ids, `-` for separators and `id>` for submenus (with their fixed children in brackets). */
    private fun shape(entries: List<HamburgerMenuModel.Entry>): List<String> = entries.map { e ->
        when (e) {
            is Item -> e.id
            Separator -> "-"
            is Submenu -> e.id + ">" + if (e.entries.isEmpty()) "" else shape(e.entries).toString()
        }
    }

    @Test
    fun TopLevelMenus_InApprovedOrder() {
        assertEquals(listOf("file", "edit", "view", "play", "settings", "tools", "help"), HamburgerMenuModel.MENUS.map { it.id })
    }

    @Test
    fun File_ProjectThenSceneThenExportThenQuit() {
        assertEquals(
            listOf(
                "new_project", "open_project", "recent>", "save_project", "save_project_as", "close_project", "-",
                "new_scene", "save_scene", "save_scene_as", "import_scene", "-", "export", "-", "quit",
            ),
            shape(HamburgerMenuModel.FILE.entries),
        )
    }

    @Test
    fun Edit_View_Play_Tools_Help_InApprovedOrder() {
        assertEquals(
            listOf("undo", "redo", "-", "cut", "copy", "paste", "duplicate", "delete", "-", "search"),
            shape(HamburgerMenuModel.EDIT.entries),
        )
        assertEquals(listOf("windows>", "open_all", "close_all", "reset_layout"), shape(HamburgerMenuModel.VIEW.entries))
        assertEquals(listOf("play_pause", "stop", "reset"), shape(HamburgerMenuModel.PLAY.entries))
        assertEquals(listOf("test_input", "shell"), shape(HamburgerMenuModel.TOOLS.entries))
        assertEquals(listOf("getting_started", "shortcuts", "about"), shape(HamburgerMenuModel.HELP.entries))
    }

    @Test
    fun Settings_AsReviewed_RadioSubmenusTogglesThenDialogs() {
        assertEquals(
            listOf(
                "language>[language_en, language_fr]", "units>[units_metric, units_imperial]",
                "show_tooltips", "show_gamepad_overlay", "-", "editor_settings", "project_settings", "keybindings",
            ),
            shape(HamburgerMenuModel.SETTINGS.entries),
        )
        val items = HamburgerMenuModel.SETTINGS.items.associateBy { it.id }
        assertTrue(items.filterKeys { it.startsWith("language_") || it.startsWith("units_") }.values.all { it.kind == HamburgerMenuModel.Kind.RADIO })
        assertEquals(HamburgerMenuModel.Kind.CHECK, items.getValue("show_tooltips").kind)
        assertEquals(HamburgerMenuModel.Kind.CHECK, items.getValue("show_gamepad_overlay").kind)
        // The overlay size slider moved to Editor Settings only.
        assertTrue(HamburgerMenuModel.MENUS.flatMap { it.items }.none { "size" in it.id })
    }

    @Test
    fun Ids_AreUniqueAcrossTheMenu() {
        val ids = HamburgerMenuModel.MENUS.flatMap { it.items }.map { it.id }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun EveryLabelAndTooltip_ExistsInEnglishAndFrench() {
        val dir = File(Assets.Strings.EDITOR_STRINGS)
        val english = StringTables.merged(dir)
        val french = StringTables.baseTables(dir).map { StringTables.localized(it, "fr") }.filter { it.exists() }.map(StringTables::read)
        val keys = HamburgerMenuModel.MENUS.flatMap { menu ->
            listOf(menu.labelKey) + menu.entries.filterIsInstance<Submenu>().map { it.labelKey } +
                menu.items.flatMap { listOf(it.labelKey, it.tooltipKey) }
        }.distinct()
        val missing = keys.filter { !english.containsKey(it) } .map { "$it (en)" } +
            keys.filter { key -> french.none { it.containsKey(key) } }.map { "$it (fr)" }
        assertTrue(missing.isEmpty(), "Missing menu strings: $missing")
    }
}
