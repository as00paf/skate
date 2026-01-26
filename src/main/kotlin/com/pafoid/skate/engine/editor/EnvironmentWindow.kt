package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.Scene
import imgui.ImGui
import imgui.type.ImFloat
import org.joml.Vector3f

class EnvironmentWindow {
    fun imgui(scene: Scene) {
        ImGui.begin("Environment")

        if (ImGui.collapsingHeader("Fog")) {
            val fogColor = floatArrayOf(scene.fogColor.x, scene.fogColor.y, scene.fogColor.z)
            if (ImGui.colorEdit3("Fog Color", fogColor)) {
                scene.fogColor.set(fogColor[0], fogColor[1], fogColor[2])
            }

            val fogDensity = floatArrayOf(scene.fogDensity)
            if (ImGui.dragFloat("Fog Density", fogDensity, 0.0001f, 0f, 0.1f, "%.4f")) {
                scene.fogDensity = fogDensity[0]
            }

            val fogGradient = floatArrayOf(scene.fogGradient)
            if (ImGui.dragFloat("Fog Gradient", fogGradient, 0.1f, 0.1f, 10f)) {
                scene.fogGradient = fogGradient[0]
            }
        }

        if (ImGui.collapsingHeader("Lighting")) {
            val ambient = floatArrayOf(scene.ambientLight.x, scene.ambientLight.y, scene.ambientLight.z)
            if (ImGui.colorEdit3("Ambient Light", ambient)) {
                scene.ambientLight.set(ambient[0], ambient[1], ambient[2])
            }
        }

        ImGui.end()
    }
}
