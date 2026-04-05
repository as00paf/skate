package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.settings.EditorSettings
import com.pafoid.skate.engine.settings.GameplaySettings
import com.pafoid.skate.engine.settings.HardwareSettings
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.flag.ImGuiWindowFlags
import imgui.internal.ImGui.begin
import imgui.internal.ImGui.button
import imgui.internal.ImGui.checkbox
import imgui.internal.ImGui.combo
import imgui.internal.ImGui.dragFloat
import imgui.internal.ImGui.end
import imgui.internal.ImGui.sameLine
import imgui.internal.ImGui.separator
import imgui.internal.ImGui.sliderFloat
import imgui.internal.ImGui.text
import imgui.internal.ImGui.textColored
import imgui.type.ImBoolean
import imgui.type.ImInt

/**
 * Window for configuring engine and project settings.
 *
 * Separates settings into two main categories:
 * - Engine: Hardware calibration and editor preferences.
 * - Project: Gameplay constants, physics, and project metadata.
 *
 * @param settingsManager Settings manager for loading/saving settings
 * @param stringManager String manager for localization
 */
class SettingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager,
) : IWindow {

    private var settingsCategory = 0 // 0=Engine, 1=Project
    private var subTab = 0 // Engine: 0=Hardware, 1=Editor | Project: 0=Gameplay, 1=Physics

    // Temporary storage for settings being edited
    private var tempHardware = HardwareSettings()
    private var tempEditor = EditorSettings()
    private var tempGameplay = GameplaySettings()
    private var hasUnsavedChanges = false

    /**
     * Renders the settings window.
     */
    override fun imgui(pOpen: imgui.type.ImBoolean?) {
        if (pOpen?.get() == false) return

        if (begin(stringManager.getString("window.settings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            // Main category selection
            val categories = arrayOf(
                stringManager.getString("tab.settings.engine"),
                stringManager.getString("tab.settings.project")
            )
            val catSelector = ImInt(settingsCategory)
            if (combo("##CategorySelector", catSelector, categories, categories.size)) {
                settingsCategory = catSelector.get()
                subTab = 0 // Reset subtab when category changes
            }

            separator()

            // Sub-tab selection
            when (settingsCategory) {
                0 -> renderEngineSubTabs()
                1 -> renderProjectSubTabs()
            }

            separator()

            // Action buttons
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

    private fun renderEngineSubTabs() {
        val tabs = arrayOf(
            stringManager.getString("tab.settings.hardware"),
            stringManager.getString("tab.settings.editor")
        )
        val tabSelector = ImInt(subTab)
        if (combo("##EngineSubTab", tabSelector, tabs, tabs.size)) {
            subTab = tabSelector.get()
        }
        separator()

        when (subTab) {
            0 -> renderHardwareSettings()
            1 -> renderEditorSettings()
        }
    }

    private fun renderProjectSubTabs() {
        val tabs = arrayOf(
            stringManager.getString("tab.settings.gameplay"),
            stringManager.getString("tab.settings.physics")
        )
        val tabSelector = ImInt(subTab)
        if (combo("##ProjectSubTab", tabSelector, tabs, tabs.size)) {
            subTab = tabSelector.get()
        }
        separator()

        when (subTab) {
            0 -> renderGameplaySettings()
            1 -> renderPhysicsSettings()
        }
    }

    private fun renderHardwareSettings() {
        // TODO Phase 5: Update to use new immutable settings structure
        text(stringManager.getString("lbl.settings.hardware_title"))
        separator()
        MImGui.textDisabled("Hardware settings configuration will be available after Phase 5 completion")
        /*
        if (!hasUnsavedChanges) syncTempSettings()

        text("Input Calibration")
        separator()

        val leftDZ = floatArrayOf(tempHardware.leftStickDeadzone)
        if (dragFloat("Left Stick Deadzone", leftDZ, 0.01f, 0f, 0.5f)) {
            tempHardware.leftStickDeadzone = leftDZ[0]
            hasUnsavedChanges = true
        }

        val rightDZ = floatArrayOf(tempHardware.rightStickDeadzone)
        if (dragFloat("Right Stick Deadzone", rightDZ, 0.01f, 0f, 0.5f)) {
            tempHardware.rightStickDeadzone = rightDZ[0]
            hasUnsavedChanges = true
        }

        val triggerT = floatArrayOf(tempHardware.triggerThreshold)
        if (dragFloat("Trigger Threshold", triggerT, 0.01f, 0f, 1f)) {
            tempHardware.triggerThreshold = triggerT[0]
            hasUnsavedChanges = true
        }

        val mouseS = floatArrayOf(tempHardware.mouseSensitivity)
        if (sliderFloat("Mouse Sensitivity", mouseS, 0.01f, 1f)) {
            tempHardware.mouseSensitivity = mouseS[0]
            hasUnsavedChanges = true
        }

        val controllerS = floatArrayOf(tempHardware.controllerSensitivity)
        if (sliderFloat("Controller Sensitivity", controllerS, 0.1f, 10f)) {
            tempHardware.controllerSensitivity = controllerS[0]
            hasUnsavedChanges = true
        }
        */
    }

    private fun renderEditorSettings() {
        // TODO Phase 5: Update to use new immutable settings structure
        text(stringManager.getString("lbl.settings.editor_prefs"))
        separator()
        MImGui.textDisabled("Editor settings configuration will be available after Phase 5 completion")
        /*
        if (!hasUnsavedChanges) syncTempSettings()

        val languages = arrayOf("en", "fr")
        val currentLangIdx = ImInt(languages.indexOf(tempEditor.language).coerceAtLeast(0))
        if (combo(stringManager.getString("menu.settings.language"), currentLangIdx, languages, languages.size)) {
            tempEditor.language = languages[currentLangIdx.get()]
            hasUnsavedChanges = true
        }

        val units = UnitSystem.entries.toTypedArray()
        val currentUnitIdx = ImInt(tempEditor.unitSystem.ordinal)
        if (combo(stringManager.getString("menu.settings.unit_system"), currentUnitIdx, units.map { it.name }.toTypedArray())) {
            tempEditor.unitSystem = units[currentUnitIdx.get()]
            hasUnsavedChanges = true
        }

        val showOverlay = ImBoolean(tempEditor.showGamepadOverlay)
        if (checkbox(stringManager.getString("menu.settings.show_gamepad_overlay"), showOverlay)) {
            tempEditor.showGamepadOverlay = showOverlay.get()
            hasUnsavedChanges = true
        }

        val overlaySize = floatArrayOf(tempEditor.gamepadOverlaySize)
        if (sliderFloat(stringManager.getString("menu.settings.gamepad_overlay_size"), overlaySize, 0.05f, 0.5f)) {
            tempEditor.gamepadOverlaySize = overlaySize[0]
            hasUnsavedChanges = true
        }
        */
    }

    private fun renderGameplaySettings() {
        // TODO Phase 5: Update to use new immutable settings structure
        text(stringManager.getString("lbl.settings.gameplay_constants"))
        separator()
        MImGui.textDisabled("Gameplay settings configuration will be available after Phase 5 completion")
        /*
        if (!hasUnsavedChanges) syncTempSettings()

        text("Gameplay Constants")
        separator()

        val moveT = floatArrayOf(tempGameplay.movementThreshold)
        if (dragFloat("Movement Threshold", moveT, 0.01f, 0f, 0.5f)) {
            tempGameplay.movementThreshold = moveT[0]
            hasUnsavedChanges = true
        }

        val sprintT = floatArrayOf(tempGameplay.sprintThreshold)
        if (dragFloat("Sprint Threshold", sprintT, 0.01f, 0.5f, 1f)) {
            tempGameplay.sprintThreshold = sprintT[0]
            hasUnsavedChanges = true
        }

        val jumpI = floatArrayOf(tempGameplay.jumpImpulse)
        if (dragFloat("Jump Impulse", jumpI, 1f, 100f, 1000f)) {
            tempGameplay.jumpImpulse = jumpI[0]
            hasUnsavedChanges = true
        }

        val walkS = floatArrayOf(tempGameplay.walkSpeed)
        if (dragFloat("Walk Speed", walkS, 0.1f, 1f, 5f)) {
            tempGameplay.walkSpeed = walkS[0]
            hasUnsavedChanges = true
        }

        val runS = floatArrayOf(tempGameplay.runSpeed)
        if (dragFloat("Run Speed", runS, 0.1f, 5f, 15f)) {
            tempGameplay.runSpeed = runS[0]
            hasUnsavedChanges = true
        }

        val rotS = floatArrayOf(tempGameplay.rotationSpeed)
        if (dragFloat("Rotation Speed", rotS, 0.5f, 1f, 30f)) {
            tempGameplay.rotationSpeed = rotS[0]
            hasUnsavedChanges = true
        }

        val inputSm = floatArrayOf(tempGameplay.inputSmoothing)
        if (dragFloat("Input Smoothing", inputSm, 0.5f, 1f, 20f)) {
            tempGameplay.inputSmoothing = inputSm[0]
            hasUnsavedChanges = true
        }
        */
    }

    private fun renderPhysicsSettings() {
        text(stringManager.getString("lbl.settings.physics_constants"))
        separator()
        MImGui.textDisabled("Project physics settings configuration will be available after Phase 5 completion")
    }

    private fun syncTempSettings() {
        // TODO Phase 5: Update to use new immutable settings structure
        // tempHardware = settingsManager.engine.hardware.copy()
        // tempEditor = settingsManager.engine.editor.copy()
        // tempGameplay = settingsManager.project.gameplay.copy()
    }

    private fun saveSettings() {
        // TODO Phase 5: Update to use new immutable settings structure with updateEditorSettings
        /*
        tempHardware.validate()
        tempGameplay.validate()

        settingsManager.engine.hardware = tempHardware
        settingsManager.engine.editor = tempEditor
        settingsManager.project.gameplay = tempGameplay

        settingsManager.saveEngine()
        settingsManager.saveProject()

        if (tempEditor.language != settingsManager.engine.editor.language) {
            settingsManager.setLocale(tempEditor.language)
        }
        */
        hasUnsavedChanges = false
    }

    private fun resetToDefaults() {
        tempHardware = HardwareSettings()
        tempEditor = EditorSettings()
        tempGameplay = GameplaySettings()
        hasUnsavedChanges = true
    }
}
