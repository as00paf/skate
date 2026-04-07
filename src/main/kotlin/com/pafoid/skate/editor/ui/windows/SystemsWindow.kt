package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
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
class SystemsWindow : IWindowWithScene, KoinComponent {
    private val stringManager: StringManager by inject()

    /**
     * Renders the systems window.
     *
     * @param currentScene The current scene to get systems from
     */
    override fun imgui(currentScene: Scene) {
        ImGui.begin(stringManager.getString("window.systems"))

        val systemManager = currentScene.systemManager
        val systems = systemManager.systems

        if (systems.isEmpty()) {
            ImGui.text(stringManager.getString("lbl.systems.no_systems"))
        } else {
            ImGui.text(stringManager.getString("lbl.systems.count", systems.size))
            ImGui.separator()

            systems.forEach { system ->
                val headerLabel = system.displayName
                val isDisabled = !system.enabled  // Capture state before checkbox can change it

                // Color code based on enabled status
                if (isDisabled) {
                    ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
                }

                if (ImGui.collapsingHeader(headerLabel)) {
                    // Enabled toggle checkbox
                    val enabled = ImBoolean(system.enabled)
                    if (ImGui.checkbox(stringManager.getString("lbl.systems.enabled"), enabled)) {
                        system.enabled = enabled.get()
                    }

                    ImGui.separator()

                    system.imgui()
                }

                if (isDisabled) {
                    ImGui.popStyleColor()
                }

                if (ImGui.beginPopupContextItem("${system.displayName}_context")) {
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
