package com.pafoid.skate.engine.procedural

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.lang.management.ManagementFactory
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** REN-28 task 2: [NoiseField] over the vendored FastNoiseLite. GL-free. */
class NoiseFieldTest {

    private val points = (0 until 400).map { i -> floatArrayOf((i % 20) * 13.7f - 120f, (i / 20) * 9.3f + 4000f) }

    private fun values(field: NoiseField) = points.map { field.sample(it[0], it[1]) }

    /**
     * Upstream FastNoiseLite 1.1.1 output for the default settings and seed 1337 (computed with the unmodified Java
     * file). Saved worlds are regenerated from seeds, so these values must never change, whatever the JVM.
     */
    @Test
    fun defaultSettings_MatchUpstreamFastNoiseLite_BitForBit() {
        val field = NoiseField(NoiseSettings(), 1337)
        assertEquals(0f, field.sample(0f, 0f))
        assertEquals(1043347198, field.sample(12.5f, -7.25f).toRawBits())
        assertEquals(-1109815402, field.sample(1000f, 2000f).toRawBits())
        assertEquals(1013458056, field.sample(-8000f, 7999.5f).toRawBits())
    }

    @Test
    fun sameSettingsAndSeed_GiveTheSameValues_DifferentSeedDoesNot() {
        val s = NoiseSettings(fractal = FractalKind.RIDGED, octaves = 5)
        assertEquals(values(NoiseField(s, 42)), values(NoiseField(s, 42)))
        assertNotEquals(values(NoiseField(s, 42)), values(NoiseField(s, 43)))
    }

    @TestFactory
    fun everyKind_StaysInsideAmplitudeAndBias() = NoiseKind.entries.flatMap { kind ->
        FractalKind.entries.map { fractal ->
            DynamicTest.dynamicTest("$kind/$fractal") {
                val field = NoiseField(NoiseSettings(kind = kind, fractal = fractal, amplitude = 25f, bias = 10f), 7)
                val v = values(field)
                assertTrue(v.all { it.isFinite() && it in -15.001f..35.001f }, "out of range: ${v.minOrNull()}..${v.maxOrNull()}")
                if (kind != NoiseKind.CELLULAR || fractal != FractalKind.NONE) assertTrue(v.toSet().size > 10, "flat noise")
            }
        }
    }

    @Test
    fun amplitudeAndBias_ScaleTheRawNoise() {
        val raw = NoiseField(NoiseSettings(), 5)
        val scaled = NoiseField(NoiseSettings(amplitude = 3f, bias = -2f), 5)
        points.forEach { p -> assertEquals(3f * raw.sample(p[0], p[1]) - 2f, scaled.sample(p[0], p[1]), 1e-5f) }
    }

    @Test
    fun domainWarp_ChangesTheField_AndStaysDeterministic() {
        val warped = NoiseSettings(warp = WarpKind.OPEN_SIMPLEX2, warpAmplitude = 40f)
        assertNotEquals(values(NoiseField(NoiseSettings(), 9)), values(NoiseField(warped, 9)))
        assertEquals(values(NoiseField(warped, 9)), values(NoiseField(warped, 9)))
        val f = NoiseField(warped, 9)
        assertEquals(f.sample(1f, 2f, 3f), f.sample(1f, 2f, 3f))
    }

    @Test
    fun threeDimensionalSamples_AreDeterministicAndVaryWithHeight() {
        val f = NoiseField(NoiseSettings(frequency = 0.05f), 11)
        assertEquals(f.sample(3f, 4f, 5f), NoiseField(NoiseSettings(frequency = 0.05f), 11).sample(3f, 4f, 5f))
        assertNotEquals(f.sample(3f, 0f, 5f), f.sample(3f, 20f, 5f))
    }

    /** Chunk grids: fill equals sample, and two chunks sharing an edge get identical edge values (no seams). */
    @Test
    fun fill_MatchesSample_AndNeighbourGridsShareTheirEdge() {
        val f = NoiseField(NoiseSettings(warp = WarpKind.BASIC_GRID), 3)
        val n = 65
        val a = FloatArray(n * n)
        val b = FloatArray(n * n)
        f.fill(a, 0f, 0f, 1f, n, n)
        f.fill(b, 64f, 0f, 1f, n, n)
        for (z in 0 until n) {
            assertEquals(f.sample(17f, z.toFloat()), a[z * n + 17])
            assertEquals(a[z * n + 64], b[z * n], "seam at row $z")
        }
        assertThrows(IllegalArgumentException::class.java) { f.fill(FloatArray(10), 0f, 0f, 1f, 4, 4) }
    }

    /** Workers share one field: parallel sampling gives exactly the serial values. */
    @Test
    fun parallelSampling_EqualsSerialSampling() {
        val f = NoiseField(NoiseSettings(warp = WarpKind.OPEN_SIMPLEX2, octaves = 4), 21)
        val serial = values(f)
        val pool = Executors.newFixedThreadPool(8)
        try {
            val results = pool.invokeAll((0 until 16).map { Callable { values(f) } }).map { it.get() }
            results.forEach { assertEquals(serial, it) }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun sampling_AllocatesNothingOnceWarm() {
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        val f = NoiseField(NoiseSettings(warp = WarpKind.OPEN_SIMPLEX2), 1)
        val out = FloatArray(65 * 65)
        var sink = 0f
        repeat(20) { f.fill(out, 0f, 0f, 1f, 65, 65); sink += f.sample(it.toFloat(), 0f, 1f) } // warm up (JIT, thread-local scratch)
        val id = Thread.currentThread().id
        val before = bean.getThreadAllocatedBytes(id)
        repeat(50) { f.fill(out, it * 64f, 0f, 1f, 65, 65); sink += f.sample(it.toFloat(), 2f, 3f) }
        val allocated = bean.getThreadAllocatedBytes(id) - before
        assertTrue(allocated < 4_096, "allocated $allocated bytes for ~211k samples ($sink)")
    }

    @Test
    fun invalidSettings_AreRejectedWithTheReason() {
        listOf(
            NoiseSettings(frequency = 0f), NoiseSettings(octaves = 0), NoiseSettings(octaves = 13),
            NoiseSettings(lacunarity = -1f), NoiseSettings(gain = Float.NaN),
            NoiseSettings(warp = WarpKind.BASIC_GRID, warpFrequency = 0f), NoiseSettings(amplitude = Float.POSITIVE_INFINITY),
        ).forEach { s ->
            assertTrue(s.problems().isNotEmpty(), "$s")
            assertThrows(IllegalArgumentException::class.java) { NoiseField(s, 0) }
        }
        assertEquals(emptyList<String>(), NoiseSettings().problems())
    }

    @Test
    fun settings_RoundTripThroughJson() {
        val s = NoiseSettings(
            kind = NoiseKind.CELLULAR, frequency = 0.02f, fractal = FractalKind.PING_PONG, octaves = 6,
            cellularDistance = CellularDistance.MANHATTAN, cellularReturn = CellularReturn.DISTANCE2_SUB,
            warp = WarpKind.OPEN_SIMPLEX2_REDUCED, warpAmplitude = 12f, amplitude = 80f, bias = -3f,
        )
        val json = Json.encodeToString(NoiseSettings.serializer(), s)
        assertEquals(s, Json.decodeFromString(NoiseSettings.serializer(), json))
        assertEquals(NoiseSettings(), Json.decodeFromString(NoiseSettings.serializer(), "{}"))
    }
}
