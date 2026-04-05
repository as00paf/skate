package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.settings.GameplaySettings
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

class ProjectSettingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager
) : IWindow {

    private var subTab = 0
    private var tempGameplay = GameplaySettings()
    private var hasUnsavedChanges = false

    override fun imgui(pOpen: ImBoolean?) {
        if (pOpen?.get() == false) return

        if (!hasUnsavedChanges) syncTempSettings()

        if (begin(stringManager.getString("window.project_settings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            if (!settingsManager.hasProject()) {
                MImGui.warningText(stringManager.getString("lbl.project_settings.no_project"))
            } else {
                renderSubTabs()
            }

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
                if (hasUnsavedChanges && settingsManager.hasProject()) saveSettings()
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
            stringManager.getString("tab.settings.gameplay"),
            stringManager.getString("tab.settings.physics")
        )
        val tabSelector = ImInt(subTab)
        if (combo("##ProjectSettingsSubTab", tabSelector, tabs, tabs.size)) {
            subTab = tabSelector.get()
        }
        separator()

        when (subTab) {
            0 -> renderGameplaySettings()
            1 -> renderPhysicsSettings()
        }
    }

    private fun renderGameplaySettings() {
        text(stringManager.getString("lbl.settings.gameplay_constants"))
        separator()
        MImGui.textDisabled("Gameplay settings configuration will be available after Phase 5 completion")
    }

    private fun renderPhysicsSettings() {
        text(stringManager.getString("lbl.settings.physics_constants"))
        separator()
        MImGui.textDisabled("Project physics settings configuration will be available after Phase 5 completion")
    }

    private fun syncTempSettings() {
        tempGameplay = settingsManager.project?.gameplaySettings ?: GameplaySettings()
    }

    private fun saveSettings() {
        hasUnsavedChanges = false
    }

    private fun resetToDefaults() {
        tempGameplay = GameplaySettings()
        hasUnsavedChanges = true
    }
}
