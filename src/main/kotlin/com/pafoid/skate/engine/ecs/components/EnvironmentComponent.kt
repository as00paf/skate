package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.config.FogCycle
import com.pafoid.skate.engine.ecs.config.FogMode
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
    /** Distance curve (REN-09): LINEAR uses [fogStart]..[fogEnd], EXP/EXP2 use [fogDensity]. */
    var fogMode: FogMode = FogMode.EXP2,
    /** Per-metre density for EXP/EXP2; 0 means no fog. */
    var fogDensity: Float = 0.0f,
    /** LINEAR: distance (m) where fog starts. */
    var fogStart: Float = 10f,
    /** LINEAR: distance (m) where fog is full. */
    var fogEnd: Float = 150f,
    /** Height fog: world height (m) at and below which the fog is at full density. */
    var fogHeight: Float = 0f,
    /** Height fog: how fast the fog thins above [fogHeight] (per metre); 0 = uniform fog, no height effect. */
    var fogHeightFalloff: Float = 0f,
    var renderSky: Boolean = true,
    var renderFog: Boolean = true,
    /** Name of a runtime UI button that cycles the fog demo steps ([FogCycle]) when clicked in Play; empty = none. */
    var fogCycleButton: String = "",
) : SceneComponent() {

    override fun reset() {
        skyColor.set(0.6f, 0.7f, 0.9f)
        skyTint.set(1.0f, 1.0f, 1.0f)
        skyExposure = 1.0f
        skyRotation = 0.0f
        fogColor.set(0.8f, 0.8f, 0.8f)
        fogMode = FogMode.EXP2
        fogDensity = 0.0f
        fogStart = 10f
        fogEnd = 150f
        fogHeight = 0f
        fogHeightFalloff = 0f
        renderSky = true
        renderFog = true
    }

    fun applyPreset(preset: EnvironmentPreset) {
        when (preset) {
            EnvironmentPreset.CLEAR_DAY -> {
                skyColor.set(0.6f, 0.7f, 0.9f)
                fog(0.6f, 0.7f, 0.9f, FogMode.EXP2, density = 0.004f, falloff = 0.02f)
            }

            EnvironmentPreset.CLOUDY -> {
                skyColor.set(0.5f, 0.5f, 0.5f)
                fog(0.7f, 0.7f, 0.7f, FogMode.EXP, density = 0.01f)
            }

            EnvironmentPreset.FOGGY -> {
                skyColor.set(0.7f, 0.7f, 0.7f)
                // Ground fog: thick at street level, thinning with height so the sky above stays visible
                fog(0.8f, 0.8f, 0.8f, FogMode.EXP2, density = 0.04f, falloff = 0.15f)
            }

            EnvironmentPreset.SUNSET -> {
                skyColor.set(0.9f, 0.5f, 0.3f)
                fog(0.8f, 0.6f, 0.5f, FogMode.EXP2, density = 0.006f, falloff = 0.03f)
            }

            EnvironmentPreset.NO_FOG -> {
                skyColor.set(0.6f, 0.7f, 0.9f)
                fog(0.8f, 0.8f, 0.8f, FogMode.EXP2, density = 0f)
            }
        }
    }

    private fun fog(r: Float, g: Float, b: Float, mode: FogMode, density: Float, falloff: Float = 0f) {
        fogColor.set(r, g, b)
        fogMode = mode
        fogDensity = density
        fogHeight = 0f
        fogHeightFalloff = falloff
    }
}
