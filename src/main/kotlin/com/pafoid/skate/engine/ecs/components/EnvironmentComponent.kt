package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector3f

@Serializable
data class EnvironmentComponent(
    @Contextual
    val skyColor: Vector3f = Vector3f(0.6f, 0.7f, 0.9f),
    @Contextual
    val skyTint: Vector3f = Vector3f(1.0f, 1.0f, 1.0f),
    var skyExposure: Float = 1.0f,
    var skyRotation: Float = 0.0f,
    var skyTexture: Texture? = null,
    @Contextual
    val fogColor: Vector3f = Vector3f(0.8f, 0.8f, 0.8f),
    var fogDensity: Float = 0.0f,
    var fogGradient: Float = 1.5f,
    var renderSky: Boolean = true,
    var renderFog: Boolean = true
) : SceneComponent() {

    override fun reset() {
        skyColor.set(0.6f, 0.7f, 0.9f)
        skyTint.set(1.0f, 1.0f, 1.0f)
        skyExposure = 1.0f
        skyRotation = 0.0f
        fogColor.set(0.8f, 0.8f, 0.8f)
        fogDensity = 0.0f
        fogGradient = 1.5f
        renderSky = true
        renderFog = true
    }

    fun applyPreset(preset: EnvironmentPreset) {
        when (preset) {
            EnvironmentPreset.CLEAR_DAY -> {
                skyColor.set(0.6f, 0.7f, 0.9f)
                fogColor.set(0.6f, 0.7f, 0.9f)
                fogDensity = 0.0008f
                fogGradient = 0.8f
            }

            EnvironmentPreset.CLOUDY -> {
                skyColor.set(0.5f, 0.5f, 0.5f)
                fogColor.set(0.7f, 0.7f, 0.7f)
                fogDensity = 0.02f
                fogGradient = 1.0f
            }

            EnvironmentPreset.FOGGY -> {
                skyColor.set(0.7f, 0.7f, 0.7f)
                fogColor.set(0.8f, 0.8f, 0.8f)
                fogDensity = 0.05f
                fogGradient = 0.5f
            }

            EnvironmentPreset.SUNSET -> {
                skyColor.set(0.9f, 0.5f, 0.3f)
                fogColor.set(0.8f, 0.6f, 0.5f)
                fogDensity = 0.001f
                fogGradient = 1.2f
            }

            EnvironmentPreset.NO_FOG -> {
                skyColor.set(0.6f, 0.7f, 0.9f)
                fogColor.set(0.8f, 0.8f, 0.8f)
                fogDensity = 0.0f
                fogGradient = 1.5f
            }
        }
    }
}

