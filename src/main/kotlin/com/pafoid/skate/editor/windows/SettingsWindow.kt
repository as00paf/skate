package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.data.DisplaySettings
import com.pafoid.skate.editor.data.InputSettings
import com.pafoid.skate.editor.data.WindowMode
import com.pafoid.skate.editor.systems.DisplayService
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.VideoModeInfo
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
 * Window for configuring game settings including input, physics, and display options.
 *
 * Provides a tabbed interface for organizing settings by category:
 * - Input: Deadzones, sensitivities, thresholds
 * - Physics: Jump impulse, movement speeds, rotation
 * - Display: Resolution, fullscreen, vsync
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
    private var settingsTab = 0  // 0=Input, 1=Physics, 2=Display

    // Temporary storage for settings being edited
    private var tempInputSettings = InputSettings()
    private var tempDisplaySettings = DisplaySettings()
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
            // Tab selection using combo
            val tabNames = arrayOf("Input", "Physics", "Display")
            val tabSelector = ImInt(settingsTab)
            if (combo("##TabSelector", tabSelector, tabNames, tabNames.size)) {
                settingsTab = tabSelector.get()
                // Refresh hardware info when switching to display tab
                if (settingsTab == 2) {
                    refreshHardwareInfo()
                }
            }

            separator()

            // Render selected tab
            when (settingsTab) {
                0 -> renderInputSettingsTab()
                1 -> renderPhysicsSettingsTab()
                2 -> renderDisplaySettingsTab()
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
                isOpen = false
                if (hasUnsavedChanges) {
                    saveSettings()
                }
            }

            // Show unsaved changes indicator
            if (hasUnsavedChanges) {
                sameLine()
                ImGui.textColored(1f, 0.8f, 0f, 1f, "* Unsaved changes")
            }
        }
        end()
    }

    private fun refreshHardwareInfo() {
        availableMonitors = displayService.getAvailableMonitors()
        val monitorIdx =
            if (tempDisplaySettings.monitorIndex < availableMonitors.size) tempDisplaySettings.monitorIndex else 0
        availableVideoModes = displayService.getAvailableVideoModes(monitorIdx)
    }

    /**
     * Syncs temporary settings with current settings when window opens.
     */
    private fun syncTempSettings() {
        val s = settingsManager.settings
        tempInputSettings = InputSettings().apply {
            leftStickDeadzone = s.inputSettings.leftStickDeadzone
            rightStickDeadzone = s.inputSettings.rightStickDeadzone
            triggerThreshold = s.inputSettings.triggerThreshold
            mouseSensitivity = s.inputSettings.mouseSensitivity
            controllerSensitivity = s.inputSettings.controllerSensitivity
            movementThreshold = s.inputSettings.movementThreshold
            sprintThreshold = s.inputSettings.sprintThreshold
            jumpImpulse = s.inputSettings.jumpImpulse
            walkSpeed = s.inputSettings.walkSpeed
            runSpeed = s.inputSettings.runSpeed
            rotationSpeed = s.inputSettings.rotationSpeed
            takeOffTime = s.inputSettings.takeOffTime
            inputSmoothing = s.inputSettings.inputSmoothing
        }
        tempDisplaySettings = s.displaySettings.copy()
    }

    /**
     * Renders the input settings tab.
     */
    private fun renderInputSettingsTab() {
        // Ensure temp settings are synced on first render
        if (!hasUnsavedChanges) {
            syncTempSettings()
        }
        // ... (rest of input settings tab)

        text(stringManager.getString("lbl.settings.deadzones"))
        separator()

        // Left Stick Deadzone
        val leftDeadzone = floatArrayOf(tempInputSettings.leftStickDeadzone)
        if (dragFloat("##LeftDeadzone", leftDeadzone, 0.01f, 0f, 0.5f, "%.2f")) {
            tempInputSettings.leftStickDeadzone = leftDeadzone[0].coerceIn(0f, 0.5f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.left_stick_deadzone"))

        // Right Stick Deadzone
        val rightDeadzone = floatArrayOf(tempInputSettings.rightStickDeadzone)
        if (dragFloat("##RightDeadzone", rightDeadzone, 0.01f, 0f, 0.5f, "%.2f")) {
            tempInputSettings.rightStickDeadzone = rightDeadzone[0].coerceIn(0f, 0.5f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.right_stick_deadzone"))

        // Trigger Threshold
        val triggerThreshold = floatArrayOf(tempInputSettings.triggerThreshold)
        if (dragFloat("##TriggerThreshold", triggerThreshold, 0.01f, 0f, 1f, "%.2f")) {
            tempInputSettings.triggerThreshold = triggerThreshold[0].coerceIn(0f, 1f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.trigger_threshold"))

        separator()
        text(stringManager.getString("lbl.settings.sensitivities"))
        separator()

        // Mouse Sensitivity
        val mouseSensitivity = floatArrayOf(tempInputSettings.mouseSensitivity)
        if (sliderFloat("##MouseSensitivity", mouseSensitivity, 0.01f, 1f, "%.2f")) {
            tempInputSettings.mouseSensitivity = mouseSensitivity[0].coerceIn(0.01f, 1f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.mouse_sensitivity"))

        // Controller Sensitivity
        val controllerSensitivity = floatArrayOf(tempInputSettings.controllerSensitivity)
        if (sliderFloat("##ControllerSensitivity", controllerSensitivity, 0.1f, 10f, "%.2f")) {
            tempInputSettings.controllerSensitivity = controllerSensitivity[0].coerceIn(0.1f, 10f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.controller_sensitivity"))

        separator()
        text(stringManager.getString("lbl.settings.thresholds"))
        separator()

        // Movement Threshold
        val movementThreshold = floatArrayOf(tempInputSettings.movementThreshold)
        if (dragFloat("##MovementThreshold", movementThreshold, 0.01f, 0f, 0.5f, "%.2f")) {
            tempInputSettings.movementThreshold = movementThreshold[0].coerceIn(0f, 0.5f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.movement_threshold"))

        // Sprint Threshold
        val sprintThreshold = floatArrayOf(tempInputSettings.sprintThreshold)
        if (dragFloat("##SprintThreshold", sprintThreshold, 0.01f, 0.5f, 1f, "%.2f")) {
            tempInputSettings.sprintThreshold = sprintThreshold[0].coerceIn(0.5f, 1f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.sprint_threshold"))
    }

    /**
     * Renders the physics settings tab.
     */
    private fun renderPhysicsSettingsTab() {
        if (!hasUnsavedChanges) {
            syncTempSettings()
        }

        text(stringManager.getString("lbl.settings.jump_physics"))
        separator()

        // Jump Impulse
        val jumpImpulse = floatArrayOf(tempInputSettings.jumpImpulse)
        if (dragFloat("##JumpImpulse", jumpImpulse, 1f, 100f, 1000f, "%.0f")) {
            tempInputSettings.jumpImpulse = jumpImpulse[0].coerceIn(100f, 1000f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.jump_impulse"))

        // Take Off Time
        val takeOffTime = floatArrayOf(tempInputSettings.takeOffTime)
        if (dragFloat("##TakeOffTime", takeOffTime, 0.01f, 0.1f, 2f, "%.2f")) {
            tempInputSettings.takeOffTime = takeOffTime[0].coerceIn(0.1f, 2f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.take_off_time"))

        separator()
        text(stringManager.getString("lbl.settings.movement_physics"))
        separator()

        // Walk Speed
        val walkSpeed = floatArrayOf(tempInputSettings.walkSpeed)
        if (dragFloat("##WalkSpeed", walkSpeed, 0.1f, 1f, 5f, "%.1f")) {
            tempInputSettings.walkSpeed = walkSpeed[0].coerceIn(1f, 5f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.walk_speed"))

        // Run Speed
        val runSpeed = floatArrayOf(tempInputSettings.runSpeed)
        if (dragFloat("##RunSpeed", runSpeed, 0.1f, 5f, 15f, "%.1f")) {
            tempInputSettings.runSpeed = runSpeed[0].coerceIn(5f, 15f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.run_speed"))

        // Rotation Speed
        val rotationSpeed = floatArrayOf(tempInputSettings.rotationSpeed)
        if (dragFloat("##RotationSpeed", rotationSpeed, 0.5f, 1f, 30f, "%.1f")) {
            tempInputSettings.rotationSpeed = rotationSpeed[0].coerceIn(1f, 30f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.rotation_speed"))

        separator()
        text(stringManager.getString("lbl.settings.input_smoothing"))
        separator()

        // Input Smoothing
        val inputSmoothing = floatArrayOf(tempInputSettings.inputSmoothing)
        if (dragFloat("##InputSmoothing", inputSmoothing, 0.5f, 1f, 20f, "%.1f")) {
            tempInputSettings.inputSmoothing = inputSmoothing[0].coerceIn(1f, 20f)
            hasUnsavedChanges = true
        }
        sameLine()
        text(stringManager.getString("lbl.settings.input_smoothing_value"))
    }

    /**
     * Renders the display settings tab.
     */
    private fun renderDisplaySettingsTab() {
        if (!hasUnsavedChanges) {
            syncTempSettings()
            refreshHardwareInfo()
        }

        text(stringManager.getString("lbl.settings.display_settings"))
        separator()

        // Monitor Selection
        val monitorNames = availableMonitors.map { it.name }.toTypedArray()
        val currentMonitorIdx = ImInt(tempDisplaySettings.monitorIndex)
        if (combo(
                stringManager.getString("lbl.settings.monitor"),
                currentMonitorIdx,
                monitorNames,
                monitorNames.size
            )
        ) {
            tempDisplaySettings.monitorIndex = currentMonitorIdx.get()
            refreshHardwareInfo() // Refresh video modes for new monitor
            hasUnsavedChanges = true
        }

        // Window Mode
        val windowModes = WindowMode.entries.toTypedArray()
        val windowModeNames = windowModes.map {
            stringManager.getString("lbl.settings.window_mode.${it.name.lowercase()}")
        }.toTypedArray()
        val currentModeIdx = ImInt(tempDisplaySettings.windowMode.ordinal)
        if (combo(
                stringManager.getString("lbl.settings.window_mode"),
                currentModeIdx,
                windowModeNames,
                windowModeNames.size
            )
        ) {
            tempDisplaySettings.windowMode = windowModes[currentModeIdx.get()]
            hasUnsavedChanges = true
        }

        // Resolution & Refresh Rate (only for Fullscreen/Borderless or specific resolutions)
        val videoModeNames = availableVideoModes.map { "${it.width}x${it.height} @ ${it.refreshRate}Hz" }.toTypedArray()
        var currentModePos = availableVideoModes.indexOfFirst {
            it.width == tempDisplaySettings.width &&
                    it.height == tempDisplaySettings.height &&
                    it.refreshRate == tempDisplaySettings.refreshRate
        }
        if (currentModePos == -1) currentModePos = 0

        val currentVideoModeIdx = ImInt(currentModePos)
        if (combo(
                stringManager.getString("lbl.settings.resolution"),
                currentVideoModeIdx,
                videoModeNames,
                videoModeNames.size
            )
        ) {
            val selectedMode = availableVideoModes[currentVideoModeIdx.get()]
            tempDisplaySettings.width = selectedMode.width
            tempDisplaySettings.height = selectedMode.height
            tempDisplaySettings.refreshRate = selectedMode.refreshRate
            hasUnsavedChanges = true
        }

        separator()

        // MSAA Samples
        val msaaOptions = arrayOf("Off", "2x", "4x", "8x")
        val msaaValues = intArrayOf(0, 2, 4, 8)
        var currentMsaaPos = msaaValues.indexOf(tempDisplaySettings.msaaSamples)
        if (currentMsaaPos == -1) currentMsaaPos = 2 // Default 4x

        val currentMsaaIdx = ImInt(currentMsaaPos)
        if (combo(stringManager.getString("lbl.settings.msaa"), currentMsaaIdx, msaaOptions, msaaOptions.size)) {
            tempDisplaySettings.msaaSamples = msaaValues[currentMsaaIdx.get()]
            hasUnsavedChanges = true
        }

        // VSync
        val vsync = ImBoolean(tempDisplaySettings.vsync)
        if (checkbox(stringManager.getString("lbl.settings.vsync"), vsync)) {
            tempDisplaySettings.vsync = vsync.get()
            hasUnsavedChanges = true
        }

        separator()
        textColored(0.7f, 0.7f, 0.7f, 1f, stringManager.getString("lbl.settings.display_note"))
    }

    /**
     * Saves all settings to disk.
     */
    private fun saveSettings() {
        // Apply temp settings to main settings
        settingsManager.settings.inputSettings = tempInputSettings
        settingsManager.settings.displaySettings = tempDisplaySettings

        // Validate settings
        settingsManager.settings.inputSettings.validate()
        settingsManager.settings.displaySettings.validate()

        // Save to disk
        settingsManager.save()
        hasUnsavedChanges = false

        // Note: Full application of display settings would happen via a callback 
        // to the Window class, typically triggered here or on restart.
    }

    /**
     * Resets all settings to default values.
     */
    private fun resetToDefaults() {
        tempInputSettings = InputSettings()
        tempDisplaySettings = DisplaySettings()
        hasUnsavedChanges = true
    }
}
