package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.utils.Icons
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Vector3f
import kotlin.math.*
import com.pafoid.skate.engine.utils.StringManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class EnvironmentWindow : KoinComponent {
    private val stringManager: StringManager by inject()

    fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.environment"))

        if (ImGui.collapsingHeader("${Icons.GEAR} ${stringManager.getString("lbl.environment.time_of_day")}")) {
            val time = floatArrayOf(scene.timeOfDay)
            val hours = time[0].toInt()
            val minutes = ((time[0] - hours) * 60).toInt()
            val timeString = String.format("%02d:%02d", hours, minutes)
            
            if (ImGui.sliderFloat(stringManager.getString("lbl.environment.time"), time, 0f, 24f, timeString)) {
                scene.timeOfDay = time[0]
                updateEnvironment(scene)
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.atmosphere")}")) {
            val skyColor = floatArrayOf(scene.skyColor.x, scene.skyColor.y, scene.skyColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sky_color"), skyColor)) {
                scene.skyColor.set(skyColor[0], skyColor[1], skyColor[2])
            }

            val skyTint = floatArrayOf(scene.skyTint.x, scene.skyTint.y, scene.skyTint.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sky_tint"), skyTint)) {
                scene.skyTint.set(skyTint[0], skyTint[1], skyTint[2])
            }

            val exposure = floatArrayOf(scene.skyExposure)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sky_exposure"), exposure, 0.01f, 0f, 10f)) {
                scene.skyExposure = exposure[0]
            }

            val skyRot = floatArrayOf(scene.skyRotation)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sky_rotation"), skyRot, 0.1f, 0f, 360f)) {
                scene.skyRotation = skyRot[0]
            }
            
            ImGui.separator()
            ImGui.text("${Icons.SUN} ${stringManager.getString("lbl.environment.sun")}")
            
            val useSun = ImBoolean(scene.useSun)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.use_sun"), useSun)) {
                scene.useSun = useSun.get()
            }
            
            val sunDir = floatArrayOf(scene.sun.direction.x, scene.sun.direction.y, scene.sun.direction.z)
            if (ImGui.dragFloat3(stringManager.getString("lbl.environment.sun_direction"), sunDir, 0.01f, -1f, 1f)) {
                scene.sun.direction.set(sunDir[0], sunDir[1], sunDir[2]).normalize()
            }
            
            val sunColor = floatArrayOf(scene.sun.color.x, scene.sun.color.y, scene.sun.color.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sun_color"), sunColor)) {
                scene.sun.color.set(sunColor[0], sunColor[1], sunColor[2])
            }
            
            val sunIntensity = floatArrayOf(scene.sun.intensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sun_intensity"), sunIntensity, 0.1f, 0f, 10f)) {
                scene.sun.intensity = sunIntensity[0]
            }
        }

        if (ImGui.collapsingHeader("${Icons.CLOUD} ${stringManager.getString("lbl.environment.fog")}")) {
            val fogColor = floatArrayOf(scene.fogColor.x, scene.fogColor.y, scene.fogColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.fog_color"), fogColor)) {
                scene.fogColor.set(fogColor[0], fogColor[1], fogColor[2])
            }

            val fogDensity = floatArrayOf(scene.fogDensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.fog_density"), fogDensity, 0.0001f, 0f, 0.1f, "%.4f")) {
                scene.fogDensity = fogDensity[0]
            }

            val fogGradient = floatArrayOf(scene.fogGradient)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.fog_gradient"), fogGradient, 0.1f, 0.1f, 10f)) {
                scene.fogGradient = fogGradient[0]
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.lighting")}")) {
            val useAmbient = ImBoolean(scene.useAmbient)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.use_ambient"), useAmbient)) {
                scene.useAmbient = useAmbient.get()
            }

            val ambient = floatArrayOf(scene.ambientLight.x, scene.ambientLight.y, scene.ambientLight.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.ambient_light"), ambient)) {
                scene.ambientLight.set(ambient[0], ambient[1], ambient[2])
            }
        }

        ImGui.end()
    }

    private fun updateEnvironment(scene: Scene) {
        val t = scene.timeOfDay / 24.0f // Map to 0..1
        
        // 1. Sun & Moon Direction
        // The dome rotates by -angle + skyRotation
        val angle = (t - 0.5f) * 2.0f * PI.toFloat()
        val totalRotation = -angle + Math.toRadians(scene.skyRotation.toDouble()).toFloat()
        
        // Sun position (starts at -Z in local texture space usually, but let's assume it matches the dome rotation)
        // We'll calculate it so it matches the SkyDomeRenderer's modelMatrix rotation
        scene.sun.direction.set(0f, 0f, -1f) // Base direction (forward)
        // Rotate it to match the dome
        val rotMatrix = org.joml.Matrix4f().rotateY(totalRotation).rotateX(Math.toRadians(15.0).toFloat()) // Slight incline
        val dir4 = org.joml.Vector4f(scene.sun.direction, 0f)
        rotMatrix.transform(dir4)
        scene.sun.direction.set(dir4.x, dir4.y, dir4.z).normalize()
        
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
