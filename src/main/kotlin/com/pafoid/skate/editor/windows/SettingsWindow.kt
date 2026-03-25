package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.systems.DisplayService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.VideoModeInfo
import com.pafoid.skate.engine.settings.DisplaySettings
import com.pafoid.skate.engine.settings.EditorSettings
import com.pafoid.skate.engine.settings.GameplaySettings
import com.pafoid.skate.engine.settings.HardwareSettings
import com.pafoid.skate.engine.settings.WindowMode
import com.pafoid.skate.engine.utils.UnitSystem
import imgui.ImGui
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
import org.koin.core.component.KoinComponent

/**
 * Window for configuring engine and project settings.
 *
 * Separates settings into two main categories:
 * - Engine: Hardware calibration, display settings, and editor preferences.
 * - Project: Gameplay constants, physics, and project metadata.
 *
 * @param settingsManager Settings manager for loading/saving settings
 * @param stringManager String manager for localization
 * @param displayService Helper for hardware discovery
 */
class SettingsWindow(
    private val settingsManager: SettingsManager,
    private val stringManager: StringManager,
    private val displayService: DisplayService
) : KoinComponent {

    var isOpen = false
    private var settingsCategory = 0 // 0=Engine, 1=Project
    private var subTab = 0 // Engine: 0=Hardware, 1=Display, 2=Editor | Project: 0=Gameplay, 1=Physics

    // Temporary storage for settings being edited
    private var tempHardware = HardwareSettings()
    private var tempDisplay = DisplaySettings()
    private var tempEditor = EditorSettings()
    private var tempGameplay = GameplaySettings()
    private var hasUnsavedChanges = false

    // Hardware discovery cache
    private var availableMonitors = emptyList<com.pafoid.skate.editor.systems.MonitorInfo>()
    private var availableVideoModes = emptyList<VideoModeInfo>()

    /**
     * Renders the settings window.
     */
    fun render() {
        if (!isOpen) return

        if (begin(stringManager.getString("window.settings"), ImGuiWindowFlags.AlwaysAutoResize)) {
            // Main category selection
            val categories = arrayOf("Engine Settings", "Project Settings")
            val catSelector = ImInt(settingsCategory)
            if (combo("##CategorySelector", catSelector, categories, categories.size)) {
                settingsCategory = catSelector.get()
                subTab = 0 // Reset subtab when category changes
                if (settingsCategory == 0 && subTab == 1) refreshHardwareInfo()
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
                isOpen = false
            }

            if (hasUnsavedChanges) {
                sameLine()
                textColored(1f, 0.8f, 0f, 1f, "* Unsaved changes")
            }
        }
        end()
    }

    private fun renderEngineSubTabs() {
        val tabs = arrayOf("Hardware", "Display", "Editor")
        val tabSelector = ImInt(subTab)
        if (combo("##EngineSubTab", tabSelector, tabs, tabs.size)) {
            subTab = tabSelector.get()
            if (subTab == 1) refreshHardwareInfo()
        }
        separator()

        when (subTab) {
            0 -> renderHardwareSettings()
            1 -> renderDisplaySettings()
            2 -> renderEditorSettings()
        }
    }

    private fun renderProjectSubTabs() {
        val tabs = arrayOf("Gameplay", "Physics")
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
    }

    private fun renderDisplaySettings() {
        if (!hasUnsavedChanges) {
            syncTempSettings()
            refreshHardwareInfo()
        }

        text(stringManager.getString("lbl.settings.display_settings"))
        separator()

        // Monitor Selection
        val monitorNames = availableMonitors.map { it.name }.toTypedArray()
        val currentMonitorIdx = ImInt(tempDisplay.monitorIndex)
        if (combo(stringManager.getString("lbl.settings.monitor"), currentMonitorIdx, monitorNames, monitorNames.size)) {
            tempDisplay.monitorIndex = currentMonitorIdx.get()
            refreshHardwareInfo()
            hasUnsavedChanges = true
        }

        // Window Mode
        val modes = com.pafoid.skate.engine.settings.WindowMode.entries.toTypedArray()
        val modeNames = modes.map { stringManager.getString("lbl.settings.window_mode.${it.name.lowercase()}") }.toTypedArray()
        val currentModeIdx = ImInt(tempDisplay.windowMode.ordinal)
        if (combo(stringManager.getString("lbl.settings.window_mode"), currentModeIdx, modeNames, modeNames.size)) {
            tempDisplay.windowMode = modes[currentModeIdx.get()]
            hasUnsavedChanges = true
        }

        // Resolution
        val videoModeNames = availableVideoModes.map { "${it.width}x${it.height} @ ${it.refreshRate}Hz" }.toTypedArray()
        var modePos = availableVideoModes.indexOfFirst { 
            it.width == tempDisplay.width && it.height == tempDisplay.height && it.refreshRate == tempDisplay.refreshRate 
        }.coerceAtLeast(0)
        
        val currentVideoModeIdx = ImInt(modePos)
        if (combo(stringManager.getString("lbl.settings.resolution"), currentVideoModeIdx, videoModeNames, videoModeNames.size)) {
            val selected = availableVideoModes[currentVideoModeIdx.get()]
            tempDisplay.width = selected.width
            tempDisplay.height = selected.height
            tempDisplay.refreshRate = selected.refreshRate
            hasUnsavedChanges = true
        }

        // MSAA
        val msaaOptions = arrayOf("Off", "2x", "4x", "8x")
        val msaaValues = intArrayOf(0, 2, 4, 8)
        var msaaPos = msaaValues.indexOf(tempDisplay.msaaSamples).coerceAtLeast(2)
        val currentMsaaIdx = ImInt(msaaPos)
        if (combo(stringManager.getString("lbl.settings.msaa"), currentMsaaIdx, msaaOptions, msaaOptions.size)) {
            tempDisplay.msaaSamples = msaaValues[currentMsaaIdx.get()]
            hasUnsavedChanges = true
        }

        val vsync = ImBoolean(tempDisplay.vsync)
        if (checkbox(stringManager.getString("lbl.settings.vsync"), vsync)) {
            tempDisplay.vsync = vsync.get()
            hasUnsavedChanges = true
        }
    }

    private fun renderEditorSettings() {
        if (!hasUnsavedChanges) syncTempSettings()

        text("Editor Preferences")
        separator()

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
    }

    private fun renderGameplaySettings() {
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
    }

    private fun renderPhysicsSettings() {
        text("Physics Engine Constants")
        separator()
        textColored(0.5f, 0.5f, 0.5f, 1f, "Project physics settings (Gravity, Timestep) go here.")
    }

    private fun refreshHardwareInfo() {
        availableMonitors = displayService.getAvailableMonitors()
        val mIdx = tempDisplay.monitorIndex.coerceIn(0, availableMonitors.size - 1)
        availableVideoModes = displayService.getAvailableVideoModes(mIdx)
    }

    private fun syncTempSettings() {
        tempHardware = settingsManager.engine.hardware.copy()
        tempDisplay = settingsManager.engine.display.copy()
        tempEditor = settingsManager.engine.editor.copy()
        tempGameplay = settingsManager.project.gameplay.copy()
    }

    private fun saveSettings() {
        tempHardware.validate()
        tempGameplay.validate()

        settingsManager.engine.hardware = tempHardware
        settingsManager.engine.display = tempDisplay
        settingsManager.engine.editor = tempEditor
        settingsManager.project.gameplay = tempGameplay

        settingsManager.saveEngine()
        settingsManager.saveProject()
        
        if (tempEditor.language != settingsManager.engine.editor.language) {
            settingsManager.setLocale(tempEditor.language)
        }
        
        hasUnsavedChanges = false
    }

    private fun resetToDefaults() {
        tempHardware = HardwareSettings()
        tempDisplay = DisplaySettings()
        tempEditor = EditorSettings()
        tempGameplay = GameplaySettings()
        hasUnsavedChanges = true
    }
}
