package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.events.WindowAction
import com.pafoid.skate.editor.imgui.HamburgerMenuRenderer
import com.pafoid.skate.editor.imgui.MenuAction
import com.pafoid.skate.editor.systems.EditorSettingsManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.utils.UnitSystem

/**
 * Settings, as reviewed in EDT-51 §2.7: Language ▸ and Units ▸ as radio items (a combo would close the menu), the
 * Show tooltips and Show gamepad overlay toggles, then the three settings dialogs. The gamepad overlay size lives in
 * Editor Settings only.
 */
class SettingsMenuBuilder(
    private val stringManager: StringManager,
    private val settingsManager: EditorSettingsManager,
    private val eventSystem: EventSystem,
) {
    fun render() = HamburgerMenuRenderer.menu(stringManager, HamburgerMenuModel.SETTINGS, ::action)

    private fun action(id: String): MenuAction? {
        val settings = settingsManager.editorSettings
        if (id.startsWith(HamburgerMenuModel.LANGUAGE_PREFIX)) {
            val code = id.removePrefix(HamburgerMenuModel.LANGUAGE_PREFIX)
            return MenuAction(checked = settings.language == code) { settingsManager.setLocale(code) }
        }
        if (id.startsWith(HamburgerMenuModel.UNITS_PREFIX)) {
            val units = UnitSystem.valueOf(id.removePrefix(HamburgerMenuModel.UNITS_PREFIX).uppercase())
            return MenuAction(checked = settings.unitSystem == units) { settingsManager.updateEditorSettings(unitSystem = units) }
        }
        return when (id) {
            "show_tooltips" -> MenuAction(checked = showTooltips) { showTooltips = !showTooltips }
            "show_gamepad_overlay" -> MenuAction(checked = settings.showGamepadOverlay) {
                settingsManager.updateEditorSettings(showGamepadOverlay = !settings.showGamepadOverlay)
            }
            "editor_settings" -> MenuAction { eventSystem.publish(WindowAction.Show("window.editor_settings")) }
            "project_settings" -> MenuAction { eventSystem.publish(WindowAction.Show("window.project_settings")) }
            "keybindings" -> MenuAction { eventSystem.publish(WindowAction.Show("window.keybindings")) }
            else -> null
        }
    }

    companion object {
        /**
         * Placeholder for the "Show tooltips" editor setting (EDT-57 adds it to `EditorSettings`, persisted and honoured
         * by every tooltip). Until then the checkbox only flips this flag for the session.
         */
        var showTooltips = true
    }
}
