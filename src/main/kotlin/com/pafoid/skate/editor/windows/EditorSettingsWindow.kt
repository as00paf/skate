package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.settings.EditorSettings
import com.pafoid.skate.engine.settings.HardwareSettings
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.button
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.end
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.text
import imgui.type.ImBoolean
import imgui.type.ImInt

class EditorSettingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager
) : IWindow {

    private var subTab = 0
    private var tempHardware = HardwareSettings()
    private var tempEditor = EditorSettings()
    private var hasUnsavedChanges = false

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen?.get() == false) return

        if (!hasUnsavedChanges) syncTempSettings()

        if (begin(stringManager.getString("window.editor_settings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            renderSubTabs()
            separator()

            if (button(stringManager.getString("btn.save"))) {
                saveSettings()
            }
            sameLine()
            if (button(stringManager.getString("btn.reset_to_defaults"))) {
                resetToDefaults()
            }
            sameLine()
            if (button(stringManager.getString("btn.close"))) {
                if (hasUnsavedChanges) saveSettings()
                pOpen?.set(false)
            }

            if (hasUnsavedChanges) {
                sameLine()
                MImGui.warningText("* Unsaved changes")
            }
        }
        end()
    }

    private fun renderSubTabs() {
        val tabs = arrayOf(
            stringManager.getString("tab.settings.hardware"),
            stringManager.getString("tab.settings.editor")
        )
        val tabSelector = ImInt(subTab)
        if (combo("##EditorSettingsSubTab", tabSelector, tabs, tabs.size)) {
            subTab = tabSelector.get()
        }
        separator()

        when (subTab) {
            0 -> renderHardwareSettings()
            1 -> renderEditorSettings()
        }
    }

    private fun renderHardwareSettings() {
        text(stringManager.getString("lbl.settings.hardware_title"))
        separator()
        MImGui.textDisabled("Hardware settings configuration will be available after Phase 5 completion")
    }

    private fun renderEditorSettings() {
        text(stringManager.getString("lbl.settings.editor_prefs"))
        separator()
        MImGui.textDisabled("Editor settings configuration will be available after Phase 5 completion")
    }

    private fun syncTempSettings() {
        tempHardware = HardwareSettings()
        tempEditor = settingsManager.engine.editor
    }

    private fun saveSettings() {
        hasUnsavedChanges = false
    }

    private fun resetToDefaults() {
        tempHardware = HardwareSettings()
        tempEditor = EditorSettings()
        hasUnsavedChanges = true
    }
}
