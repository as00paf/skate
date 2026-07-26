package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.systems.imgui
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.AnimationSystem
import com.pafoid.skate.engine.ecs.systems.AudioSystem
import com.pafoid.skate.engine.ecs.systems.DayNightCycleSystem
import com.pafoid.skate.engine.ecs.systems.DirectionalLightSystem
import com.pafoid.skate.engine.ecs.systems.EnvironmentSystem
import com.pafoid.skate.engine.ecs.systems.System
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.type.ImBoolean

class SystemsWindow(
    private val stringManager: StringManager,
    private val engine: Engine
) : IWindowWithScene {

    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.systems"))

        // ECS Systems (from Scene)
        if (ImGui.collapsingHeader(stringManager.getString("lbl.systems.gameplay_systems"))) {
            val systems = engine.systemManager.systems
            if (systems.isEmpty()) {
                ImGui.text(stringManager.getString("lbl.systems.no_systems"))
            } else {
                renderSystemsList(systems)
            }
        }

        ImGui.end()
    }

    private fun renderSystemsList(systems: List<System>) {
        systems.forEach { system ->
            val headerLabel = system.displayName
            val isDisabled = !system.enabled

            if (isDisabled) {
                ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
            }

            if (ImGui.collapsingHeader(headerLabel)) {
                val enabled = ImBoolean(system.enabled)
                if (ImGui.checkbox(stringManager.getString("lbl.systems.enabled"), enabled)) {
                    system.enabled = enabled.get()
                }

                ImGui.separator()
                when (system) {
                    is AnimationSystem -> system.imgui(stringManager)
                    is AudioSystem -> system.imgui(stringManager)
                    is DayNightCycleSystem -> system.imgui(stringManager)
                    is DirectionalLightSystem -> system.imgui(stringManager)
                    is EnvironmentSystem -> system.imgui(stringManager)
                }
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
}
