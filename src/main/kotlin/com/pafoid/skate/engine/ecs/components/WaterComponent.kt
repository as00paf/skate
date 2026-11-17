package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.engine.environment.GerstnerWave
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import org.joml.Vector2f

/**
 * A bounded body of water (REN-29 §3.4): a [sizeX] × [sizeZ] m surface centred on the Transform (only its position
 * and yaw are used), [depth] m deep. The water pass draws it; `BuoyancySystem` (PHY-16) floats bodies in it with the
 * same waves and clock (`EnvironmentComponent.runtime`).
 */
@Serializable
data class WaterComponent(
    var sizeX: Float = 20f,
    var sizeZ: Float = 20f,
    /** The volume runs from the surface down by this much: buoyancy bounds and the absorption fallback. */
    var depth: Float = 2f,
    /** Up to 4 Gerstner waves; extra entries are ignored. */
    val waves: MutableList<GerstnerWave> = WaterPreset.LAKE.waves(),
    /** Scales wave amplitudes with the wind speed and turns wave 0 into the wind. */
    var windDrivenWaves: Boolean = false,
    /** River flow (m/s on the water's XZ): scrolls detail normals and foam, and drags floating bodies. */
    @Contextual
    val flowVelocity: Vector2f = Vector2f(),
    /** `.skmat` with schema `"water"`, or a bundled preset ([WaterMaterials]). */
    var material: String = WaterMaterials.DEFAULT,
    /** Surface grid resolution near the camera, in metres. */
    var quadSize: Float = 0.5f,
    /** Fluid density in kg/m³ (buoyancy). */
    var density: Float = 1000f,
    /** Linear drag per second, scaled by the submerged fraction. */
    var linearDrag: Float = 1f,
    /** Angular drag per second, scaled by the submerged fraction. */
    var angularDrag: Float = 0.5f,
    /** Sun shadows on the surface; water never casts. */
    var receiveShadows: Boolean = true,
    /** Over an ocean, the ocean is neither drawn nor queried inside this rectangle (REN-29 §6.7). */
    var cutsOcean: Boolean = true,
) : Component() {

    /** Applies an inspector preset: waves, flow and material. */
    fun applyPreset(preset: WaterPreset) {
        waves.clear()
        waves.addAll(preset.waves())
        flowVelocity.set(preset.flow, 0f)
        material = preset.material
    }

    override fun reset() {
        sizeX = 20f; sizeZ = 20f; depth = 2f
        applyPreset(WaterPreset.LAKE)
        windDrivenWaves = false
        quadSize = 0.5f
        density = 1000f; linearDrag = 1f; angularDrag = 0.5f
        receiveShadows = true; cutsOcean = true
    }
}

/** Bundled water materials (schema `"water"`, REN-29 §6.5); the renderer knows them without a file. */
object WaterMaterials {
    const val DEFAULT = "engine:Materials/water_default.skmat"
    const val POOL = "engine:Materials/water_pool.skmat"
    const val OCEAN = "engine:Materials/water_ocean.skmat"

    val ALL = listOf(DEFAULT, POOL, OCEAN)
}

/** Inspector presets (REN-29 §3.4). */
enum class WaterPreset(val flow: Float, val material: String) {
    CALM_POOL(0f, WaterMaterials.POOL),
    LAKE(0f, WaterMaterials.DEFAULT),
    OCEAN_SWELL(0f, WaterMaterials.OCEAN),
    RIVER(1f, WaterMaterials.DEFAULT);

    fun waves(): MutableList<GerstnerWave> = when (this) {
        CALM_POOL -> mutableListOf(
            GerstnerWave(directionDeg = 20f, wavelength = 3f, amplitude = 0.01f, steepness = 0.2f),
            GerstnerWave(directionDeg = 110f, wavelength = 1.7f, amplitude = 0.006f, steepness = 0.2f),
        )
        LAKE -> mutableListOf(
            GerstnerWave(directionDeg = 30f, wavelength = 8f, amplitude = 0.06f, steepness = 0.4f),
            GerstnerWave(directionDeg = 75f, wavelength = 4.5f, amplitude = 0.03f, steepness = 0.4f),
            GerstnerWave(directionDeg = -20f, wavelength = 2.3f, amplitude = 0.015f, steepness = 0.3f),
        )
        OCEAN_SWELL -> mutableListOf(
            GerstnerWave(directionDeg = 0f, wavelength = 40f, amplitude = 0.6f, steepness = 0.6f),
            GerstnerWave(directionDeg = 25f, wavelength = 18f, amplitude = 0.25f, steepness = 0.6f),
            GerstnerWave(directionDeg = -35f, wavelength = 9f, amplitude = 0.1f, steepness = 0.5f),
            GerstnerWave(directionDeg = 60f, wavelength = 4f, amplitude = 0.04f, steepness = 0.4f),
        )
        RIVER -> mutableListOf(
            GerstnerWave(directionDeg = 90f, wavelength = 2.5f, amplitude = 0.02f, steepness = 0.3f),
            GerstnerWave(directionDeg = 60f, wavelength = 1.2f, amplitude = 0.01f, steepness = 0.3f),
        )
    }
}
