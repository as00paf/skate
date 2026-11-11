package com.pafoid.skate.engine.procedural

import kotlinx.serialization.Serializable

/** Base noise function (FastNoiseLite's `NoiseType`). Kotlin names, so saved settings don't depend on the Java enum. */
enum class NoiseKind { OPEN_SIMPLEX2, OPEN_SIMPLEX2S, CELLULAR, PERLIN, VALUE_CUBIC, VALUE }

/** How octaves are combined (FastNoiseLite's `FractalType`, 2D/3D noise only; the warp has its own). */
enum class FractalKind { NONE, FBM, RIDGED, PING_PONG }

enum class CellularDistance { EUCLIDEAN, EUCLIDEAN_SQ, MANHATTAN, HYBRID }

enum class CellularReturn { CELL_VALUE, DISTANCE, DISTANCE2, DISTANCE2_ADD, DISTANCE2_SUB, DISTANCE2_MUL, DISTANCE2_DIV }

/** Domain warp: offsets the sample position by another noise before sampling, for twisty, less grid-like shapes. */
enum class WarpKind { NONE, OPEN_SIMPLEX2, OPEN_SIMPLEX2_REDUCED, BASIC_GRID }

/**
 * Serializable description of one noise layer (REN-28 §5.2). [NoiseField] turns it into samples. Frequencies are per
 * world unit (metre): 0.01 gives features about 100 m across. The output is FastNoiseLite's [-1, 1] range times
 * [amplitude], plus [bias].
 *
 * The seed is not stored here: it comes from the project seed and a stream name ([ProceduralSeeds]), so the same
 * settings give a different, reproducible result per terrain or per layer.
 */
@Serializable
data class NoiseSettings(
    val kind: NoiseKind = NoiseKind.OPEN_SIMPLEX2,
    val frequency: Float = 0.01f,
    val fractal: FractalKind = FractalKind.FBM,
    val octaves: Int = 3,
    val lacunarity: Float = 2f,
    val gain: Float = 0.5f,
    val weightedStrength: Float = 0f,
    val pingPongStrength: Float = 2f,
    val cellularDistance: CellularDistance = CellularDistance.EUCLIDEAN_SQ,
    val cellularReturn: CellularReturn = CellularReturn.DISTANCE,
    val cellularJitter: Float = 1f,
    val warp: WarpKind = WarpKind.NONE,
    val warpAmplitude: Float = 30f,
    val warpFrequency: Float = 0.005f,
    val amplitude: Float = 1f,
    val bias: Float = 0f,
) {
    /** Problems that would make sampling meaningless; empty when the settings are usable. */
    fun problems(): List<String> = buildList {
        if (!(frequency > 0f) || !frequency.isFinite()) add("frequency must be > 0 (was $frequency)")
        if (octaves !in 1..MAX_OCTAVES) add("octaves must be in 1..$MAX_OCTAVES (was $octaves)")
        if (!(lacunarity > 0f) || !lacunarity.isFinite()) add("lacunarity must be > 0 (was $lacunarity)")
        if (!gain.isFinite() || gain < 0f) add("gain must be >= 0 (was $gain)")
        if (!cellularJitter.isFinite() || cellularJitter < 0f) add("cellularJitter must be >= 0 (was $cellularJitter)")
        if (warp != WarpKind.NONE && (!(warpFrequency > 0f) || !warpFrequency.isFinite())) add("warpFrequency must be > 0 (was $warpFrequency)")
        if (!warpAmplitude.isFinite()) add("warpAmplitude must be finite")
        if (!amplitude.isFinite() || !bias.isFinite()) add("amplitude and bias must be finite")
    }

    companion object {
        const val MAX_OCTAVES = 12
    }
}
