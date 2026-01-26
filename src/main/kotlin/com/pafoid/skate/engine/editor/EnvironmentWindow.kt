package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.Scene
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Vector3f
import kotlin.math.*

class EnvironmentWindow {
    fun imgui(scene: Scene) {
        ImGui.begin("Environment")

        if (ImGui.collapsingHeader("Time of Day")) {
            val time = floatArrayOf(scene.timeOfDay)
            val hours = time[0].toInt()
            val minutes = ((time[0] - hours) * 60).toInt()
            val timeString = String.format("%02d:%02d", hours, minutes)
            
            if (ImGui.sliderFloat("Time", time, 0f, 24f, timeString)) {
                scene.timeOfDay = time[0]
                updateEnvironment(scene)
            }
        }

        if (ImGui.collapsingHeader("Atmosphere")) {
            val skyColor = floatArrayOf(scene.skyColor.x, scene.skyColor.y, scene.skyColor.z)
            if (ImGui.colorEdit3("Sky Color (Clear)", skyColor)) {
                scene.skyColor.set(skyColor[0], skyColor[1], skyColor[2])
            }

            val skyTint = floatArrayOf(scene.skyTint.x, scene.skyTint.y, scene.skyTint.z)
            if (ImGui.colorEdit3("Sky Tint", skyTint)) {
                scene.skyTint.set(skyTint[0], skyTint[1], skyTint[2])
            }

            val exposure = floatArrayOf(scene.skyExposure)
            if (ImGui.dragFloat("Sky Exposure", exposure, 0.01f, 0f, 10f)) {
                scene.skyExposure = exposure[0]
            }

            val skyRot = floatArrayOf(scene.skyRotation)
            if (ImGui.dragFloat("Sky Rotation", skyRot, 0.1f, 0f, 360f)) {
                scene.skyRotation = skyRot[0]
            }
            
            ImGui.separator()
            ImGui.text("Sun (Directional Light)")
            
            val useSun = ImBoolean(scene.useSun)
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
            val useAmbient = ImBoolean(scene.useAmbient)
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
        val t = scene.timeOfDay / 24.0f // Map to 0..1
        
        // 1. Sun & Moon Direction
        val angle = (t - 0.5f) * 2.0f * PI.toFloat()
        
        // Sun position
        scene.sun.direction.set(
            sin(angle.toDouble()).toFloat(),
            -cos(angle.toDouble()).toFloat(),
            0.2f
        ).normalize()
        
        // Moon position (opposite to sun)
        scene.moon.direction.set(scene.sun.direction).negate()

        // 2. Intensities
        val sunCos = -scene.sun.direction.y
        
        val sunIntensity = sunCos.coerceIn(0f, 1f)
        val moonIntensity = (-sunCos).coerceIn(0f, 1f)
        
        scene.sun.intensity = sunIntensity * 1.5f
        scene.moon.intensity = moonIntensity * 0.5f // Moon is dimmer

        // 3. Colors
        val dayColor = Vector3f(1f, 1f, 0.9f)
        val sunsetColor = Vector3f(1f, 0.4f, 0.2f)
        val nightMoonColor = Vector3f(0.4f, 0.5f, 0.8f) // Bluish moon light
        
        scene.sun.color.set(dayColor).lerp(sunsetColor, 1f - sunIntensity)
        scene.moon.color.set(nightMoonColor)

        // 4. Sky Color
        val noonSky = Vector3f(0.5f, 0.7f, 1.0f)
        val sunsetSky = Vector3f(1.0f, 0.4f, 0.2f)
        val twilightSky = Vector3f(0.1f, 0.15f, 0.35f) // Deep twilight blue
        val nightSky = Vector3f(0.02f, 0.02f, 0.05f)
        
        if (sunCos > 0.2f) {
            val factor = ((sunCos - 0.2f) / 0.8f).coerceIn(0f, 1f)
            scene.skyColor.set(sunsetSky).lerp(noonSky, factor)
        } else if (sunCos > 0.0f) {
            val factor = (sunCos / 0.2f).coerceIn(0f, 1f)
            scene.skyColor.set(twilightSky).lerp(sunsetSky, factor)
        } else if (sunCos > -0.2f) {
            val factor = ((sunCos + 0.2f) / 0.2f).coerceIn(0f, 1f)
            scene.skyColor.set(nightSky).lerp(twilightSky, factor)
        } else {
            scene.skyColor.set(nightSky)
        }

        // 5. Fog
        scene.fogColor.set(scene.skyColor)
        scene.fogDensity = 0.0005f + (1f - sunIntensity.coerceAtLeast(0.5f)) * 0.0006f
        scene.fogGradient = 0.8f

        // 6. Dynamic Ambient
        val baseAmbient = Vector3f(0.05f, 0.05f, 0.1f)
        val dayAmbient = Vector3f(0.2f, 0.2f, 0.2f)
        scene.ambientLight.set(baseAmbient).lerp(dayAmbient, sunIntensity)
    }
}
