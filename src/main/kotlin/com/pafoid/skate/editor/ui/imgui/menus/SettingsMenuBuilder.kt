package com.pafoid.skate.editor.ui.imgui.menus

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.windows.KeyBindingsWindow
import com.pafoid.skate.editor.windows.SettingsWindow
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.sliderFloat
import imgui.type.ImBoolean
import imgui.type.ImInt

/**
 * Builds the Settings menu with editor configuration options.
 * 
 * This component handles:
 * - Gamepad overlay settings
 * - Unit system selection
 * - Language selection
 * - Key bindings window
 * - Settings window
 * 
 * @param stringManager For localized menu strings
 * @param settingsManager For accessing and saving settings
 * @param keyBindingsWindow To open on menu click
 * @param settingsWindow To open on menu click
 */
class SettingsMenuBuilder(
    private val stringManager: StringManager,
    private val settingsManager: SettingsManager,
    private val keyBindingsWindow: KeyBindingsWindow,
    private val settingsWindow: SettingsWindow
) {
    
    /**
     * Renders the Settings menu.
     */
    fun render() {
        if (beginMenu(stringManager.getString("menu.settings"))) {
            renderGamepadSettings()
            ImGui.separator()
            renderUnitSystemSetting()
            renderLanguageSetting()
            ImGui.separator()
            renderWindowItems()
            endMenu()
        }
    }
    
    private fun renderGamepadSettings() {
        val engineSettings = settingsManager.engine
        val editorSettings = engineSettings.editor
        
        val overlaySize = floatArrayOf(editorSettings.gamepadOverlaySize)
        if (sliderFloat(
                stringManager.getString("menu.settings.gamepad_overlay_size"),
                overlaySize,
                0.05f,
                0.5f
            )
        ) {
            editorSettings.gamepadOverlaySize = overlaySize[0]
            settingsManager.saveEngine()
        }
        
        val showOverlay = ImBoolean(editorSettings.showGamepadOverlay)
        if (checkbox(stringManager.getString("menu.settings.show_gamepad_overlay"), showOverlay)) {
            editorSettings.showGamepadOverlay = showOverlay.get()
            settingsManager.saveEngine()
        }
    }
    
    private fun renderUnitSystemSetting() {
        val editorSettings = settingsManager.engine.editor
        val unitSystems = UnitSystem.entries.toTypedArray()
        val currentUnitIdx = ImInt(editorSettings.unitSystem.ordinal)
        if (combo(
                stringManager.getString("menu.settings.unit_system"),
                currentUnitIdx,
                unitSystems.map { it.name }.toTypedArray()
            )
        ) {
            editorSettings.unitSystem = unitSystems[currentUnitIdx.get()]
            settingsManager.saveEngine()
        }
    }
    
    private fun renderLanguageSetting() {
        val editorSettings = settingsManager.engine.editor
        val languages = arrayOf("en", "fr")
        val currentLangIdx = ImInt(languages.indexOf(editorSettings.language))
        if (combo(
                stringManager.getString("menu.settings.language"),
                currentLangIdx,
                languages,
                languages.size
            )
        ) {
            val newLang = languages[currentLangIdx.get()]
            editorSettings.language = newLang
            settingsManager.setLocale(newLang)
            settingsManager.saveEngine()
        }
    }
    
    private fun renderWindowItems() {
        if (menuItem(stringManager.getString("menu.settings.keybindings"))) {
            keyBindingsWindow.isOpen = true
        }
        if (menuItem(stringManager.getString("menu.settings.settings"))) {
            settingsWindow.isOpen = true
        }
    }
}
