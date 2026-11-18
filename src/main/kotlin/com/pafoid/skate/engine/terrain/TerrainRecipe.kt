package com.pafoid.skate.engine.terrain

import com.pafoid.skate.engine.procedural.FractalKind
import com.pafoid.skate.engine.procedural.NoiseKind
import com.pafoid.skate.engine.procedural.NoiseSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One step of a [TerrainRecipe] (REN-28 §5.3). Steps run in order **per sample at terrain-local (x, z)**, so any chunk
 * can be generated alone. Heights are in units of `TerrainComponent.heightScaleMetres` until the recipe ends; stamps
 * (§5.4) then apply in metres.
 */
@Serializable
sealed class TerrainStep {
    /** Adds `amplitude × noise + offset`; the noise is seeded from `"<seedName>/<name>"`. */
    @Serializable
    @SerialName("noise")
    data class Noise(val name: String = "noise", val settings: NoiseSettings = NoiseSettings(), val amplitude: Float = 1f, val offset: Float = 0f) : TerrainStep()

    /** Moves the sample position of the following steps by `strength` metres along two noise fields. */
    @Serializable
    @SerialName("domainWarp")
    data class DomainWarp(val name: String = "warp", val settings: NoiseSettings = NoiseSettings(frequency = 0.004f), val strength: Float = 20f) : TerrainStep()

    /**
     * Pulls the edges down to [floor] between [inner] and [outer] (fractions of the half size, measured from the
     * centre), so a terrain becomes an island. [shape] NONE does nothing.
     */
    @Serializable
    @SerialName("falloff")
    data class Falloff(val shape: FalloffShape = FalloffShape.ISLAND, val inner: Float = 0.55f, val outer: Float = 0.95f, val floor: Float = -0.1f) : TerrainStep()

    /** Quantizes into [steps] levels; [sharpness] 0 keeps the slope, 1 makes vertical risers. */
    @Serializable
    @SerialName("terrace")
    data class Terrace(val steps: Int = 6, val sharpness: Float = 0.6f) : TerrainStep()

    /** Piecewise-linear remap of the height through [points] (sorted by `x`, clamped outside). */
    @Serializable
    @SerialName("curve")
    data class Curve(val points: List<CurvePoint> = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))) : TerrainStep()

    @Serializable
    @SerialName("clamp")
    data class Clamp(val min: Float = 0f, val max: Float = 1f) : TerrainStep()
}

@Serializable
enum class FalloffShape { NONE, ISLAND, SQUARE }

@Serializable
data class CurvePoint(val x: Float, val y: Float)

/** The ordered steps of a procedural terrain (REN-28 §5.3); stamps always run after them. */
@Serializable
data class TerrainRecipe(val steps: List<TerrainStep> = TerrainPreset.ISLAND.recipe().steps)

/** Recipe presets (REN-28 §5.3). */
enum class TerrainPreset {
    FLAT, ROLLING_HILLS, ISLAND, MOUNTAINS, SKATEPARK_BOWL;

    fun recipe(): TerrainRecipe = TerrainRecipe(
        when (this) {
            FLAT -> listOf(TerrainStep.Clamp(0f, 0f))
            ROLLING_HILLS -> listOf(
                TerrainStep.Noise("hills", NoiseSettings(frequency = 0.006f, octaves = 4), amplitude = 0.25f, offset = 0.2f),
                TerrainStep.Clamp(0f, 1f),
            )
            ISLAND -> listOf(
                TerrainStep.DomainWarp(),
                TerrainStep.Noise("hills", NoiseSettings(frequency = 0.008f, octaves = 4), amplitude = 0.3f, offset = 0.35f),
                TerrainStep.Noise("peaks", NoiseSettings(kind = NoiseKind.OPEN_SIMPLEX2, fractal = FractalKind.RIDGED, frequency = 0.01f, octaves = 3), amplitude = 0.2f),
                TerrainStep.Falloff(FalloffShape.ISLAND, inner = 0.45f, outer = 0.95f, floor = -0.1f),
                TerrainStep.Clamp(-0.1f, 1f),
            )
            MOUNTAINS -> listOf(
                TerrainStep.DomainWarp(strength = 35f),
                TerrainStep.Noise("ridges", NoiseSettings(fractal = FractalKind.RIDGED, frequency = 0.004f, octaves = 5), amplitude = 0.6f, offset = 0.3f),
                TerrainStep.Curve(listOf(CurvePoint(0f, 0f), CurvePoint(0.4f, 0.2f), CurvePoint(1f, 1f))),
                TerrainStep.Clamp(0f, 1f),
            )
            SKATEPARK_BOWL -> listOf(
                TerrainStep.Noise("hills", NoiseSettings(frequency = 0.01f, octaves = 2), amplitude = 0.05f, offset = 0.05f),
                TerrainStep.Clamp(0f, 1f),
            )
        },
    )
}
