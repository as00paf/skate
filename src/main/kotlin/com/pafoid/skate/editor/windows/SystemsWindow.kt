package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.ExecutionPriority
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.ecs.systems.SystemManager
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Centralized window for displaying and interacting with all system ImGui interfaces.
 *
 * This window auto-discovers all systems in the current scene and displays their
 * custom [System.imgui()] implementations in a collapsible header format.
 *
 * ## Features
 *
 * - Auto-discovers all systems via [SystemManager.systems]
 * - Displays system enabled status with visual indicator
 * - Shows system execution priority
 * - Calls each system's [imgui()][System.imgui] method inside collapsing headers
 * - Allows enabling/disabling systems at runtime
 *
 * ## Usage
 *
 * ```kotlin
 * val systemsWindow = SystemsWindow()
 * systemsWindow.imgui(currentScene)
 * ```
 *
 * @see System
 * @see SystemManager
 */
class SystemsWindow : KoinComponent {
    private val stringManager: StringManager by inject()

    /**
     * Renders the systems window.
     *
     * @param currentScene The current scene to get systems from
     */
    fun imgui(currentScene: Scene) {
        ImGui.begin(stringManager.getString("window.systems"))

        val systemManager = currentScene.systemManager
        val systems = systemManager.systems

        if (systems.isEmpty()) {
            ImGui.text(stringManager.getString("lbl.systems.no_systems"))
        } else {
            ImGui.text(stringManager.getString("lbl.systems.count", systems.size))
            ImGui.separator()

            systems.forEach { system ->
                // Build header label with system name and priority
                val systemName = system.displayName
                val priorityLabel = when (system.priority) {
                    ExecutionPriority.EARLY -> "[EARLY]"
                    ExecutionPriority.DEFAULT -> "[DEFAULT]"
                    ExecutionPriority.LATE -> "[LATE]"
                }
                val headerLabel = "$systemName $priorityLabel"

                // Color code based on enabled status
                if (!system.enabled) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
                }

                if (ImGui.collapsingHeader(headerLabel)) {
                    // Enabled toggle checkbox
                    val enabled = ImBoolean(system.enabled)
                    if (ImGui.checkbox(stringManager.getString("lbl.systems.enabled"), enabled)) {
                        system.enabled = enabled.get()
                    }

                    ImGui.separator()

                    // Call the system's imgui implementation
                    system.imgui()
                }

                if (!system.enabled) {
                    ImGui.popStyleColor()
                }

                // Context menu for enabling/disabling system
                if (ImGui.beginPopupContextItem("${systemName}_context")) {
                    val contextEnabled = ImBoolean(system.enabled)
                    if (ImGui.checkbox(stringManager.getString("lbl.systems.toggle_enabled"), contextEnabled)) {
                        system.enabled = contextEnabled.get()
                    }
                    ImGui.endPopup()
                }
            }
        }

        ImGui.end()
    }
}
