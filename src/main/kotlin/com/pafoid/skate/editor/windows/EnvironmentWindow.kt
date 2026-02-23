package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import imgui.ImGui
import imgui.type.ImBoolean
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlin.math.PI

class EnvironmentWindow : KoinComponent {
    private val stringManager: StringManager by inject()

    fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.environment"))

        if (ImGui.collapsingHeader("${Icons.GEAR} ${stringManager.getString("lbl.environment.time_of_day")}")) {
            val time = floatArrayOf(scene.sceneData.timeOfDay)
            val hours = time[0].toInt()
            val minutes = ((time[0] - hours) * 60).toInt()
            val timeString = String.format("%02d:%02d", hours, minutes)

            if (ImGui.sliderFloat(stringManager.getString("lbl.environment.time"), time, 0f, 24f, timeString)) {
                scene.sceneData.timeOfDay = time[0]
                updateEnvironment(scene)
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.atmosphere")}")) {
            val skyColor = floatArrayOf(scene.sceneData.skyColor.x, scene.sceneData.skyColor.y, scene.sceneData.skyColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sky_color"), skyColor)) {
                scene.sceneData.skyColor.set(skyColor[0], skyColor[1], skyColor[2])
            }

            val skyTint = floatArrayOf(scene.sceneData.skyTint.x, scene.sceneData.skyTint.y, scene.sceneData.skyTint.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sky_tint"), skyTint)) {
                scene.sceneData.skyTint.set(skyTint[0], skyTint[1], skyTint[2])
            }

            val exposure = floatArrayOf(scene.sceneData.skyExposure)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sky_exposure"), exposure, 0.01f, 0f, 10f)) {
                scene.sceneData.skyExposure = exposure[0]
            }

            val skyRot = floatArrayOf(scene.sceneData.skyRotation)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sky_rotation"), skyRot, 0.1f, 0f, 360f)) {
                scene.sceneData.skyRotation = skyRot[0]
            }

            ImGui.separator()
            ImGui.text("${Icons.SUN} ${stringManager.getString("lbl.environment.sun")}")

            val sunDir = floatArrayOf(scene.sceneData.sun.direction.x, scene.sceneData.sun.direction.y, scene.sceneData.sun.direction.z)
            if (ImGui.dragFloat3(stringManager.getString("lbl.environment.sun_direction"), sunDir, 0.01f, -1f, 1f)) {
                scene.sceneData.sun.direction.set(sunDir[0], sunDir[1], sunDir[2]).normalize()
            }

            val sunColor = floatArrayOf(scene.sceneData.sun.color.x, scene.sceneData.sun.color.y, scene.sceneData.sun.color.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.sun_color"), sunColor)) {
                scene.sceneData.sun.color.set(sunColor[0], sunColor[1], sunColor[2])
            }

            val sunIntensity = floatArrayOf(scene.sceneData.sun.intensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.sun_intensity"), sunIntensity, 0.1f, 0f, 10f)) {
                scene.sceneData.sun.intensity = sunIntensity[0]
            }
        }

        if (ImGui.collapsingHeader("${Icons.CLOUD} ${stringManager.getString("lbl.environment.fog")}")) {
            val fogColor = floatArrayOf(scene.sceneData.fogColor.x, scene.sceneData.fogColor.y, scene.sceneData.fogColor.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.fog_color"), fogColor)) {
                scene.sceneData.fogColor.set(fogColor[0], fogColor[1], fogColor[2])
            }

            val fogDensity = floatArrayOf(scene.sceneData.fogDensity)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.fog_density"), fogDensity, 0.0001f, 0f, 0.1f, "%.4f")) {
                scene.sceneData.fogDensity = fogDensity[0]
            }

            val fogGradient = floatArrayOf(scene.sceneData.fogGradient)
            if (ImGui.dragFloat(stringManager.getString("lbl.environment.fog_gradient"), fogGradient, 0.1f, 0.1f, 10f)) {
                scene.sceneData.fogGradient = fogGradient[0]
            }
        }

        if (ImGui.collapsingHeader("${Icons.PALETTE} ${stringManager.getString("lbl.environment.lighting")}")) {
            val useAmbient = ImBoolean(scene.sceneData.useAmbient)
            if (ImGui.checkbox(stringManager.getString("lbl.environment.use_ambient"), useAmbient)) {
                scene.sceneData.useAmbient = useAmbient.get()
            }

            val ambient = floatArrayOf(scene.sceneData.ambientLight.x, scene.sceneData.ambientLight.y, scene.sceneData.ambientLight.z)
            if (ImGui.colorEdit3(stringManager.getString("lbl.environment.ambient_light"), ambient)) {
                scene.sceneData.ambientLight.set(ambient[0], ambient[1], ambient[2])
            }
        }

        ImGui.end()
    }

    private fun updateEnvironment(scene: Scene) {
        val t = scene.sceneData.timeOfDay / 24.0f

        val angle = (t - 0.5f) * 2.0f * PI.toFloat()
        val totalRotation = -angle + Math.toRadians(scene.sceneData.skyRotation.toDouble()).toFloat()

        scene.sceneData.sun.direction.set(0f, 0f, -1f)
        val rotMatrix = Matrix4f().rotateY(totalRotation).rotateX(Math.toRadians(15.0).toFloat())
        val dir4 = Vector4f(scene.sceneData.sun.direction, 0f)
        rotMatrix.transform(dir4)
        scene.sceneData.sun.direction.set(dir4.x, dir4.y, dir4.z).normalize()

        val sunCos = -scene.sceneData.sun.direction.y
        val sunIntensity = sunCos.coerceIn(0f, 1f)
        scene.sceneData.sun.intensity = sunIntensity * 1.5f

        val dayColor = Vector3f(1f, 1f, 0.9f)
        val sunsetColor = Vector3f(1f, 0.4f, 0.2f)
        scene.sceneData.sun.color.set(dayColor).lerp(sunsetColor, 1f - sunIntensity)

        val noonSky = Vector3f(0.5f, 0.7f, 1.0f)
        val sunsetSky = Vector3f(1.0f, 0.4f, 0.2f)
        val twilightSky = Vector3f(0.1f, 0.15f, 0.35f)
        val nightSky = Vector3f(0.02f, 0.02f, 0.05f)

        if (sunCos > 0.2f) {
            val factor = ((sunCos - 0.2f) / 0.8f).coerceIn(0f, 1f)
            scene.sceneData.skyColor.set(sunsetSky).lerp(noonSky, factor)
        } else if (sunCos > 0.0f) {
            val factor = (sunCos / 0.2f).coerceIn(0f, 1f)
            scene.sceneData.skyColor.set(twilightSky).lerp(sunsetSky, factor)
        } else if (sunCos > -0.2f) {
            val factor = ((sunCos + 0.2f) / 0.2f).coerceIn(0f, 1f)
            scene.sceneData.skyColor.set(nightSky).lerp(twilightSky, factor)
        } else {
            scene.sceneData.skyColor.set(nightSky)
        }

        scene.sceneData.fogColor.set(scene.sceneData.skyColor)
        scene.sceneData.fogDensity = 0.0005f + (1f - sunIntensity.coerceAtLeast(0.5f)) * 0.0006f
        scene.sceneData.fogGradient = 0.8f

        val baseAmbient = Vector3f(0.05f, 0.05f, 0.1f)
        val dayAmbient = Vector3f(0.2f, 0.2f, 0.2f)
        scene.sceneData.ambientLight.set(baseAmbient).lerp(dayAmbient, sunIntensity)
    }
}
