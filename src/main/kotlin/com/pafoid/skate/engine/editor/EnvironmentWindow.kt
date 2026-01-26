package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.Scene
import imgui.ImGui
import imgui.type.ImFloat
import org.joml.Vector3f

class EnvironmentWindow {
    fun imgui(scene: Scene) {
        ImGui.begin("Environment")

        if (ImGui.collapsingHeader("Time of Day")) {
            val time = floatArrayOf(scene.timeOfDay)
            if (ImGui.sliderFloat("Time", time, 0f, 1f, "%.2f")) {
                scene.timeOfDay = time[0]
                updateEnvironment(scene)
            }
            ImGui.text("0.0 = Sunrise, 0.5 = Noon, 1.0 = Sunset/Night")
        }

        if (ImGui.collapsingHeader("Atmosphere")) {
            val skyColor = floatArrayOf(scene.skyColor.x, scene.skyColor.y, scene.skyColor.z)
            if (ImGui.colorEdit3("Sky Color", skyColor)) {
                scene.skyColor.set(skyColor[0], skyColor[1], skyColor[2])
            }
            
            ImGui.separator()
            ImGui.text("Sun (Directional Light)")
            
            val useSun = imgui.type.ImBoolean(scene.useSun)
            if (ImGui.checkbox("Use Sun", useSun)) {
                scene.useSun = useSun.get()
            }
            
            val sunDir = floatArrayOf(scene.sun.direction.x, scene.sun.direction.y, scene.sun.direction.z)
            if (ImGui.dragFloat3("Sun Direction", sunDir, 0.01f, -1f, 1f)) {
                scene.sun.direction.set(sunDir[0], sunDir[1], sunDir[2]).normalize()
            }
            
            val sunColor = floatArrayOf(scene.sun.color.x, scene.sun.color.y, scene.sun.color.z)
            if (ImGui.colorEdit3("Sun Color", sunColor)) {
                scene.sun.color.set(sunColor[0], sunColor[1], sunColor[2])
            }
            
            val sunIntensity = floatArrayOf(scene.sun.intensity)
            if (ImGui.dragFloat("Sun Intensity", sunIntensity, 0.1f, 0f, 10f)) {
                scene.sun.intensity = sunIntensity[0]
            }
        }

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
            val useAmbient = imgui.type.ImBoolean(scene.useAmbient)
            if (ImGui.checkbox("Use Ambient", useAmbient)) {
                scene.useAmbient = useAmbient.get()
            }

            val ambient = floatArrayOf(scene.ambientLight.x, scene.ambientLight.y, scene.ambientLight.z)
            if (ImGui.colorEdit3("Ambient Light", ambient)) {
                scene.ambientLight.set(ambient[0], ambient[1], ambient[2])
            }
        }

        ImGui.end()
    }

    private fun updateEnvironment(scene: Scene) {
        val t = scene.timeOfDay
        
        // 1. Sun Direction (Rotation)
        // Noon (0.5) is straight down (-1 on Y)
        // Sunrise (0.0) is from East, Sunset (1.0) is West
        val angle = (t - 0.5f) * Math.PI.toFloat()
        scene.sun.direction.set(
            Math.sin(angle.toDouble()).toFloat(),
            -Math.cos(angle.toDouble()).toFloat(),
            0.2f // Slight tilt
        ).normalize()

        // 2. Sun Intensity & Color
        // Sun is strongest at noon, fades at edges
        val intensityFactor = Math.cos(angle.toDouble()).toFloat().coerceIn(0f, 1f)
        scene.sun.intensity = intensityFactor * 1.5f
        
        // Color shifts to orange/red at sunrise/sunset
        val dayColor = Vector3f(1f, 1f, 0.9f)
        val sunsetColor = Vector3f(1f, 0.4f, 0.2f)
        scene.sun.color.set(dayColor).lerp(sunsetColor, 1f - intensityFactor)

        // 3. Sky Color
        val noonSky = Vector3f(0.6f, 0.7f, 0.9f)
        val sunsetSky = Vector3f(0.8f, 0.4f, 0.3f)
        val nightSky = Vector3f(0.02f, 0.02f, 0.05f)
        
        if (intensityFactor > 0.1f) {
            scene.skyColor.set(noonSky).lerp(sunsetSky, 1f - intensityFactor)
        } else {
            val nightFactor = (0.1f - intensityFactor) * 10f
            scene.skyColor.set(sunsetSky).lerp(nightSky, nightFactor.coerceIn(0f, 1f))
        }

        // 4. Fog
        scene.fogColor.set(scene.skyColor)
        scene.fogDensity = 0.002f + (1f - intensityFactor) * 0.01f
    }
}
