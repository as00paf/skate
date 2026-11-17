package com.pafoid.skate.engine.environment

import com.pafoid.skate.engine.ecs.components.WaterPreset
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.math.abs

/** REN-29 §6.1: the Gerstner sum `water_waves.glsl` mirrors and buoyancy samples. */
class WaterWavesTest {
    private val eps = 1e-4f
    private val lake = WaterWaves().configure(WaterPreset.LAKE.waves())

    @Test
    fun WaterWaves_Table_PinsDisplacementAndNormal() {
        // (x, z, t) -> displaced (x, y, z), normal (x, y, z): the "Lake" preset
        val table = listOf(
            floatArrayOf(0f, 0f, 0f, 0.022052f, 0f, 0.028119f, -0.049744f, 0.994727f, -0.089683f),
            floatArrayOf(1.5f, -2.25f, 3f, 1.478963f, 0.014059f, -2.276492f, 0.049223f, 0.995604f, 0.079687f),
            floatArrayOf(-7f, 4f, 10.5f, -7.020165f, 0.041283f, 3.988252f, 0.068203f, 0.997669f, -0.002239f),
        )
        val p = Vector3f()
        val n = Vector3f()
        for (r in table) {
            lake.displace(r[0], r[1], r[2], p)
            lake.normalAt(r[0], r[1], r[2], n)
            assertEquals(r[3], p.x, eps); assertEquals(r[4], p.y, eps); assertEquals(r[5], p.z, eps)
            assertEquals(r[6], n.x, eps); assertEquals(r[7], n.y, eps); assertEquals(r[8], n.z, eps)
        }
    }

    @Test
    fun WaterWaves_HeightAt_InvertsTheHorizontalShiftWithinOneCentimetre() {
        val ocean = WaterWaves().configure(WaterPreset.OCEAN_SWELL.waves())
        val p = Vector3f()
        for (x in -20..20 step 5) for (z in -20..20 step 5) for (t in listOf(0f, 1.3f, 7.9f)) {
            ocean.displace(x.toFloat(), z.toFloat(), t, p)
            assertEquals(p.y, ocean.heightAt(p.x, p.z, t), 0.01f, "at ($x, $z, $t)")
        }
    }

    @Test
    fun WaterWaves_SteepnessIsNormalizedSoCrestsNeverLoop() {
        val steep = WaterWaves().configure(List(4) { GerstnerWave(it * 40f, wavelength = 2f, amplitude = 0.5f, steepness = 1f) })
        assertEquals(1f, steep.steepnessSum(), 1e-5f)
        // A gentle set is left alone
        assertTrue(lake.steepnessSum() < 0.1f)
        assertEquals(0.4f, lake.q[0], 1e-6f)
    }

    @Test
    fun WaterWaves_KeepsAtMostFourWaves_AndSkipsDegenerateOnes() {
        val many = WaterWaves().configure(List(6) { GerstnerWave(wavelength = 5f) } + GerstnerWave(wavelength = 0f))
        assertEquals(WaterWaves.MAX_WAVES, many.count)
        assertEquals(0, WaterWaves().configure(listOf(GerstnerWave(wavelength = 0f))).count)
    }

    @Test
    fun WaterWaves_SpeedFollowsDeepWaterDispersion() {
        val one = WaterWaves().configure(listOf(GerstnerWave(wavelength = 10f)))
        val k = (2 * Math.PI / 10).toFloat()
        assertEquals(kotlin.math.sqrt(9.81f * k), one.omega[0], 1e-5f)
    }

    @Test
    fun WaterWaves_FlatWater_HasUpNormalAndUnitJacobian() {
        val flat = WaterWaves().configure(emptyList())
        assertEquals(Vector3f(0f, 1f, 0f), flat.normalAt(3f, 4f, 5f, Vector3f()))
        assertEquals(1f, flat.jacobian(3f, 4f, 5f))
        assertEquals(0f, flat.heightAt(3f, 4f, 5f))
    }

    @Test
    fun WaterWaves_SteepCrestsPinchTheJacobian() {
        val swell = WaterWaves().configure(listOf(GerstnerWave(wavelength = 10f, amplitude = 1.5f, steepness = 1f)))
        var min = Float.MAX_VALUE
        for (i in 0 until 100) min = minOf(min, swell.jacobian(0f, i * 0.1f, 0f))
        assertTrue(min < 0.2f, "crest jacobian $min")
    }

    @Test
    fun WaterWaves_WindDriven_ScalesAmplitudeAndTurnsWaveZeroIntoTheWind() {
        val calm = WaterWaves().configure(WaterPreset.LAKE.waves(), windDriven = true, windSpeed = 0f, windDirectionDeg = 90f)
        assertEquals(0f, calm.totalAmplitude)
        val storm = WaterWaves().configure(WaterPreset.LAKE.waves(), windDriven = true, windSpeed = 16f, windDirectionDeg = 90f)
        assertEquals(lake.totalAmplitude * WaterWaves.MAX_WIND_SCALE, storm.totalAmplitude, 1e-5f)
        assertEquals(1f, storm.dirX[0], 1e-6f)
        assertEquals(0f, storm.dirZ[0], 1e-6f)
    }

    @Test
    fun WaterWavesGlsl_MirrorsTheKotlinFormula() {
        val glsl = File("assets/Shaders/water_waves.glsl").readText()
        for (term in listOf("float th = k * dot(dir, p) - omega * t;", "d.xz += q * amp * dir * c;", "d.y += amp * s;",
            "n.xz -= dir * k * amp * c;", "n.y -= q * k * amp * s;", "jacobian = jxx * jzz - jxz * jxz;")) {
            assertTrue(term in glsl, term)
        }
        assertTrue(abs(WaterWaves.GRAVITY - 9.81f) < 1e-6f)
    }
}
