package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.WindowRegistry
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.internal.ImGui.beginMenu
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.endMenu
import imgui.internal.ImGui.menuItem
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.sliderFloat
import imgui.type.ImBoolean
import imgui.type.ImInt

class SettingsMenuBuilder(
    private val stringManager: StringManager,
    private val settingsManager: SettingsManager,
    private val windowRegistry: WindowRegistry,
) {
    private val keyBindingsShowFlag: ImBoolean
        get() = windowRegistry.windows.find { it.nameKey == "window.keybindings" }?.showFlag ?: ImBoolean(false)
    private val settingsShowFlag: ImBoolean
        get() = windowRegistry.windows.find { it.nameKey == "window.editor_settings" }?.showFlag ?: ImBoolean(false)

    fun render() {
        if (beginMenu(stringManager.getString("menu.settings"))) {
            renderGamepadSettings()
            separator()
            renderUnitSystemSetting()
            renderLanguageSetting()
            separator()
            renderWindowItems()
            endMenu()
        }
    }
    
    private fun renderGamepadSettings() {
        val editorSettings = settingsManager.engine.editor

        val overlaySize = floatArrayOf(editorSettings.gamepadOverlaySize)
        if (sliderFloat(
                stringManager.getString("menu.settings.gamepad_overlay_size"),
                overlaySize,
                0.05f,
                0.5f
            )
        ) {
            settingsManager.updateEditorSettings(gamepadOverlaySize = overlaySize[0])
        }

        val showOverlay = ImBoolean(editorSettings.showGamepadOverlay)
        if (checkbox(stringManager.getString("menu.settings.show_gamepad_overlay"), showOverlay)) {
            settingsManager.updateEditorSettings(showGamepadOverlay = showOverlay.get())
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
            settingsManager.updateEditorSettings(unitSystem = unitSystems[currentUnitIdx.get()])
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
            settingsManager.setLocale(newLang)
        }
    }
    
    private fun renderWindowItems() {
        if (menuItem(stringManager.getString("menu.settings.keybindings"))) {
            keyBindingsShowFlag.set(true)
        }
        if (menuItem(stringManager.getString("menu.settings.settings"))) {
            settingsShowFlag.set(true)
        }
    }
}
