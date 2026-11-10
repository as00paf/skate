package com.pafoid.skate.engine.procedural

import com.pafoid.skate.engine.procedural.noise.FastNoiseLite

/**
 * Deterministic noise sampled at world positions (REN-28 §5.2), a thin wrapper over the vendored FastNoiseLite.
 * Same [settings] and [seed] give the same values on every run, thread and machine; nothing depends on the order
 * samples are taken in, so terrain chunks can be generated independently on workers.
 *
 * Sampling allocates nothing and is thread-safe: FastNoiseLite only reads its configuration while sampling, and the
 * domain-warp scratch position is per thread. Build fields once (per recipe change), not per sample.
 */
class NoiseField(val settings: NoiseSettings, val seed: Int) {

    init {
        val problems = settings.problems()
        require(problems.isEmpty()) { "Invalid NoiseSettings: ${problems.joinToString("; ")}" }
    }

    private val noise = FastNoiseLite(seed).apply {
        SetNoiseType(settings.kind.toFnl())
        SetFrequency(settings.frequency)
        SetFractalType(settings.fractal.toFnl())
        SetFractalOctaves(settings.octaves)
        SetFractalLacunarity(settings.lacunarity)
        SetFractalGain(settings.gain)
        SetFractalWeightedStrength(settings.weightedStrength)
        SetFractalPingPongStrength(settings.pingPongStrength)
        SetCellularDistanceFunction(settings.cellularDistance.toFnl())
        SetCellularReturnType(settings.cellularReturn.toFnl())
        SetCellularJitter(settings.cellularJitter)
        // 3D sampling with Y up: rotate so XZ slices don't show the simplex grid (FastNoiseLite's recommendation)
        SetRotationType3D(FastNoiseLite.RotationType3D.ImproveXZPlanes)
    }

    /** A second instance for the warp, so it has its own frequency and a seed that differs from the noise's. */
    private val warp: FastNoiseLite? = if (settings.warp == WarpKind.NONE) null else FastNoiseLite(seed xor WARP_SEED_SALT).apply {
        SetDomainWarpType(settings.warp.toFnl())
        SetDomainWarpAmp(settings.warpAmplitude)
        SetFrequency(settings.warpFrequency)
        SetRotationType3D(FastNoiseLite.RotationType3D.ImproveXZPlanes)
    }

    private val scratch2 = ThreadLocal.withInitial { FastNoiseLite.Vector2(0f, 0f) }
    private val scratch3 = ThreadLocal.withInitial { FastNoiseLite.Vector3(0f, 0f, 0f) }

    /** Noise at world (x, z), on the ground plane: `amplitude * noise + bias`. */
    fun sample(x: Float, z: Float): Float {
        val w = warp ?: return settings.amplitude * noise.GetNoise(x, z) + settings.bias
        val p = scratch2.get()
        p.x = x
        p.y = z
        w.DomainWarp(p)
        return settings.amplitude * noise.GetNoise(p.x, p.y) + settings.bias
    }

    /** Noise at world (x, y, z), for volumes (clouds, caves later, 3D masks). */
    fun sample(x: Float, y: Float, z: Float): Float {
        val w = warp ?: return settings.amplitude * noise.GetNoise(x, y, z) + settings.bias
        val p = scratch3.get()
        p.x = x
        p.y = y
        p.z = z
        w.DomainWarp(p)
        return settings.amplitude * noise.GetNoise(p.x, p.y, p.z) + settings.bias
    }

    /**
     * Fills [out] with a [countX] × [countZ] grid of 2D samples starting at ([originX], [originZ]) every [step] metres,
     * row by row (index `z * countX + x`). Each value equals [sample] at that point, so neighbouring grids that share an
     * edge produce identical edge values.
     */
    fun fill(out: FloatArray, originX: Float, originZ: Float, step: Float, countX: Int, countZ: Int, offset: Int = 0) {
        require(countX >= 0 && countZ >= 0) { "Negative grid size" }
        require(offset >= 0 && offset + countX * countZ <= out.size) { "Output too small for $countX x $countZ at $offset" }
        var i = offset
        for (iz in 0 until countZ) {
            val z = originZ + iz * step
            for (ix in 0 until countX) out[i++] = sample(originX + ix * step, z)
        }
    }

    private companion object {
        const val WARP_SEED_SALT = 0x5EED_A11
    }
}

private fun NoiseKind.toFnl() = when (this) {
    NoiseKind.OPEN_SIMPLEX2 -> FastNoiseLite.NoiseType.OpenSimplex2
    NoiseKind.OPEN_SIMPLEX2S -> FastNoiseLite.NoiseType.OpenSimplex2S
    NoiseKind.CELLULAR -> FastNoiseLite.NoiseType.Cellular
    NoiseKind.PERLIN -> FastNoiseLite.NoiseType.Perlin
    NoiseKind.VALUE_CUBIC -> FastNoiseLite.NoiseType.ValueCubic
    NoiseKind.VALUE -> FastNoiseLite.NoiseType.Value
}

private fun FractalKind.toFnl() = when (this) {
    FractalKind.NONE -> FastNoiseLite.FractalType.None
    FractalKind.FBM -> FastNoiseLite.FractalType.FBm
    FractalKind.RIDGED -> FastNoiseLite.FractalType.Ridged
    FractalKind.PING_PONG -> FastNoiseLite.FractalType.PingPong
}

private fun CellularDistance.toFnl() = when (this) {
    CellularDistance.EUCLIDEAN -> FastNoiseLite.CellularDistanceFunction.Euclidean
    CellularDistance.EUCLIDEAN_SQ -> FastNoiseLite.CellularDistanceFunction.EuclideanSq
    CellularDistance.MANHATTAN -> FastNoiseLite.CellularDistanceFunction.Manhattan
    CellularDistance.HYBRID -> FastNoiseLite.CellularDistanceFunction.Hybrid
}

private fun CellularReturn.toFnl() = when (this) {
    CellularReturn.CELL_VALUE -> FastNoiseLite.CellularReturnType.CellValue
    CellularReturn.DISTANCE -> FastNoiseLite.CellularReturnType.Distance
    CellularReturn.DISTANCE2 -> FastNoiseLite.CellularReturnType.Distance2
    CellularReturn.DISTANCE2_ADD -> FastNoiseLite.CellularReturnType.Distance2Add
    CellularReturn.DISTANCE2_SUB -> FastNoiseLite.CellularReturnType.Distance2Sub
    CellularReturn.DISTANCE2_MUL -> FastNoiseLite.CellularReturnType.Distance2Mul
    CellularReturn.DISTANCE2_DIV -> FastNoiseLite.CellularReturnType.Distance2Div
}

private fun WarpKind.toFnl() = when (this) {
    WarpKind.NONE -> error("no warp")
    WarpKind.OPEN_SIMPLEX2 -> FastNoiseLite.DomainWarpType.OpenSimplex2
    WarpKind.OPEN_SIMPLEX2_REDUCED -> FastNoiseLite.DomainWarpType.OpenSimplex2Reduced
    WarpKind.BASIC_GRID -> FastNoiseLite.DomainWarpType.BasicGrid
}
