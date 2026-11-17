package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.assets.data.Texture
import com.pafoid.skate.engine.ecs.config.EnvironmentPreset
import com.pafoid.skate.engine.ecs.config.FogCycle
import com.pafoid.skate.engine.ecs.config.FogMode
import com.pafoid.skate.engine.environment.EnvironmentClock
import com.pafoid.skate.engine.environment.WindField
import kotlinx.serialization.Transient
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
    /**
     * Ambient light (REN-26): uniform light with no position, applied to every surface. Scene-wide, so it lives in the
     * environment (it was a positional-looking AmbientLightComponent). [DayNightCycleComponent.autoAmbient] drives it.
     */
    @Contextual
    val ambientColor: Vector3f = Vector3f(1f, 1f, 1f),
    var ambientIntensity: Float = DEFAULT_AMBIENT_INTENSITY,
    /** Strength of sky reflections on surfaces (image-based specular, REN-27); 0 turns reflections off. */
    var reflectionIntensity: Float = 1f,
    /** Strength of the light the sky casts on surfaces (image-based diffuse, REN-27), on top of the ambient light. */
    var skyLightIntensity: Float = DEFAULT_SKY_LIGHT_INTENSITY,
    /** Name of a runtime UI button that cycles the fog demo steps ([FogCycle]) when clicked in Play; empty = none. */
    @GameObjectRef var fogCycleButton: String = "",
    /** Wind (REN-29 §3.1): yaw in degrees the wind blows towards (0° = +Z, 90° = +X). */
    var windDirection: Float = DEFAULT_WIND_DIRECTION,
    /** Wind speed in m/s; 0 disables wind. Presets: calm 0.5, breeze 3, strong 8, storm 16 ([WindField]). */
    var windSpeed: Float = DEFAULT_WIND_SPEED,
    /** Gust amplitude relative to [windSpeed]. */
    var windGustStrength: Float = DEFAULT_WIND_GUST_STRENGTH,
    /** Gust front wavelength in metres; gusts travel along the wind at [windSpeed]. */
    var windGustScale: Float = DEFAULT_WIND_GUST_SCALE,
    /** High-frequency flutter weight (leaves, grass tips). */
    var windTurbulence: Float = DEFAULT_WIND_TURBULENCE,
    /** Wind and waves move in edit mode (preview); off freezes them at time 0. */
    var animateInEditor: Boolean = true,
    /** Name of a runtime UI button that cycles the wind presets (calm, breeze, storm) when clicked in Play; empty = none. */
    @GameObjectRef var windCycleButton: String = "",
) : SceneComponent() {

    /** Not serialized: the animation clock of wind and water ([EnvironmentClock]), stepped by `EnvironmentClockSystem`. */
    @Transient
    val runtime: EnvironmentClock = EnvironmentClock()

    @Transient
    private val windField = WindField()

    /** This environment's wind as a [WindField] (one instance, refreshed from the fields on each call). */
    fun wind(): WindField = windField.set(windDirection, windSpeed, windGustStrength, windGustScale, windTurbulence)

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
        ambientColor.set(1f, 1f, 1f)
        ambientIntensity = DEFAULT_AMBIENT_INTENSITY
        reflectionIntensity = 1f
        skyLightIntensity = DEFAULT_SKY_LIGHT_INTENSITY
        windDirection = DEFAULT_WIND_DIRECTION
        windSpeed = DEFAULT_WIND_SPEED
        windGustStrength = DEFAULT_WIND_GUST_STRENGTH
        windGustScale = DEFAULT_WIND_GUST_SCALE
        windTurbulence = DEFAULT_WIND_TURBULENCE
        animateInEditor = true
        runtime.reset()
    }

    /** Ambient light reaching surfaces: [ambientColor] scaled by [ambientIntensity], written into [out]. */
    fun ambient(out: Vector3f): Vector3f = out.set(ambientColor).mul(ambientIntensity)

    companion object {
        const val DEFAULT_AMBIENT_INTENSITY = 0.25f
        const val DEFAULT_SKY_LIGHT_INTENSITY = 0.35f
        const val DEFAULT_WIND_DIRECTION = 45f
        const val DEFAULT_WIND_SPEED = WindField.BREEZE
        const val DEFAULT_WIND_GUST_STRENGTH = 0.4f
        const val DEFAULT_WIND_GUST_SCALE = 20f
        const val DEFAULT_WIND_TURBULENCE = 0.3f
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
