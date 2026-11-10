package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.render.lighting.IblMath.RadianceImage
import org.joml.Matrix4f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** REN-27: image-based lighting math (encoding, sky mapping, GGX prefilter, BRDF table, SH irradiance). */
class IblMathTest {

    @Test
    fun encodeDecode_RoundTripAndMatchTheMeshTonemap() {
        for (r in listOf(0f, 0.05f, 0.3f, 1f, 2.5f, 8f)) {
            assertEquals(r, IblMath.decode(IblMath.encode(r)), r * 0.01f + 1e-4f)
        }
        // decode inverts Reinhard + gamma: tonemapping the decoded radiance gives the display value back
        for (d in listOf(0.1f, 0.5f, 0.8f, 0.95f)) {
            val r = IblMath.decode(d)
            assertEquals(d, Math.pow((r / (1 + r)).toDouble(), 1 / 2.2).toFloat(), 1e-4f)
        }
        assertEquals(IblMath.MAX_RADIANCE, IblMath.decode(1f), 1e-3f)
        assertTrue(IblMath.decode(0.6f) < IblMath.decode(0.7f))
    }

    @Test
    fun skyMapping_MatchesTheSkyDomeSphere() {
        val dir = Vector3f(); val uv = FloatArray(2)
        assertEquals(1f, IblMath.skyUv(Vector3f(0f, 1f, 0f), uv)[1], 1e-5f, "zenith at v = 1")
        assertEquals(0f, IblMath.skyUv(Vector3f(0f, -1f, 0f), uv)[1], 1e-5f, "nadir at v = 0")
        assertEquals(0f, IblMath.skyUv(Vector3f(1f, 0f, 0f), uv)[0], 1e-5f)
        assertEquals(0.25f, IblMath.skyUv(Vector3f(0f, 0f, 1f), uv)[0], 1e-5f)
        assertEquals(0.75f, IblMath.skyUv(Vector3f(0f, 0f, -1f), uv)[0], 1e-5f)
        for ((u, v) in listOf(0.1f to 0.2f, 0.5f to 0.5f, 0.8f to 0.9f)) {
            IblMath.skyDir(u, v, dir)
            // SkyDomeRenderer.generateUVSphere vertex for texture coordinates (u, v)
            val y = sin(-PI / 2 + PI * v); val x = cos(2 * PI * u) * sin(PI * v); val z = sin(2 * PI * u) * sin(PI * v)
            assertEquals(x.toFloat(), dir.x, 1e-5f); assertEquals(y.toFloat(), dir.y, 1e-5f); assertEquals(z.toFloat(), dir.z, 1e-5f)
            IblMath.skyUv(dir, uv)
            assertEquals(u, uv[0], 1e-4f); assertEquals(v, uv[1], 1e-4f)
        }
    }

    @Test
    fun toSkyLocal_UndoesTheDomeRotation() {
        val rotation = 1.234f
        val local = Vector3f(0.3f, 0.5f, -0.8f).normalize()
        val world = Matrix4f().rotateY(rotation).transformDirection(Vector3f(local))
        val back = IblMath.toSkyLocal(world, rotation, Vector3f())
        assertEquals(0f, back.distance(local), 1e-5f)
    }

    @Test
    fun horizonBlend_FogBelowSkyAbove() {
        assertEquals(0f, IblMath.horizonBlend(0.3f), 1e-6f)
        assertEquals(1f, IblMath.horizonBlend(0.6f), 1e-6f)
        assertTrue(IblMath.horizonBlend(0.47f) in 0.1f..0.9f)
    }

    @Test
    fun hammersley_IsStratified() {
        val p = FloatArray(2)
        assertEquals(0f, IblMath.hammersley(0, 4, p)[1], 1e-6f)
        assertEquals(0.5f, IblMath.hammersley(1, 4, p)[1], 1e-6f)
        assertEquals(0.25f, IblMath.hammersley(2, 4, p)[1], 1e-6f)
        assertEquals(0.75f, IblMath.hammersley(3, 4, p)[0], 1e-6f)
    }

    @Test
    fun ggxSamples_StayAroundTheNormal_AndNarrowWithRoughness() {
        val n = Vector3f(0.2f, 0.9f, -0.3f).normalize(); val h = Vector3f(); val xi = FloatArray(2)
        var spreadSmooth = 0f; var spreadRough = 0f
        for (i in 0 until 64) {
            IblMath.hammersley(i, 64, xi)
            IblMath.importanceSampleGgx(xi[0], xi[1], n, 0.1f, h)
            assertEquals(1f, h.length(), 1e-4f)
            assertTrue(h.dot(n) > 0f)
            spreadSmooth += 1f - h.dot(n)
            IblMath.importanceSampleGgx(xi[0], xi[1], n, 0.9f, h)
            spreadRough += 1f - h.dot(n)
        }
        assertTrue(spreadSmooth < spreadRough / 10f, "smooth $spreadSmooth vs rough $spreadRough")
    }

    private fun constant(w: Int, h: Int, r: Float, g: Float, b: Float) =
        RadianceImage(w, h).also { img -> for (y in 0 until h) for (x in 0 until w) img.set(x, y, r, g, b) }

    @Test
    fun prefilter_KeepsAConstantEnvironment() {
        val pyramid = IblMath.pyramid(constant(64, 32, 0.4f, 0.8f, 1.6f))
        for (roughness in listOf(0f, 0.5f, 1f)) {
            val out = IblMath.prefilter(pyramid, 16, 8, roughness, 32)
            for (i in 0 until 16 * 8) {
                assertEquals(0.4f, out.rgb[i * 3], 1e-3f); assertEquals(1.6f, out.rgb[i * 3 + 2], 1e-3f)
            }
        }
    }

    @Test
    fun prefilter_BlursMoreWithRoughness() {
        // A bright band around the zenith: rougher levels spread it, lowering its peak and lighting the horizon
        val sky = RadianceImage(64, 32).also { img ->
            for (y in 0 until 32) for (x in 0 until 64) { val v = if (y >= 28) 10f else 0.1f; img.set(x, y, v, v, v) }
        }
        val pyramid = IblMath.pyramid(sky)
        fun at(img: RadianceImage, v: Float) = img.rgb[((v * img.height).toInt().coerceAtMost(img.height - 1) * img.width) * 3]
        val sharp = IblMath.prefilter(pyramid, 32, 16, 0.05f, 64)
        val blurred = IblMath.prefilter(pyramid, 32, 16, 0.8f, 64)
        assertTrue(at(blurred, 0.97f) < at(sharp, 0.97f), "peak drops")
        assertTrue(at(blurred, 0.7f) > at(sharp, 0.7f), "light spreads toward the horizon")
    }

    @Test
    fun brdfTable_IsBoundedAndHasTheKnownLimits() {
        val ab = FloatArray(2)
        IblMath.integrateBrdf(0.999f, 0.02f, 256, ab)
        assertEquals(1f, ab[0], 0.02f, "head-on mirror: F0 scale 1")
        assertEquals(0f, ab[1], 0.02f, "head-on mirror: no Fresnel bias")
        IblMath.integrateBrdf(0.05f, 0.1f, 256, ab)
        assertTrue(ab[1] > 0.2f, "grazing smooth surfaces reflect strongly whatever F0 (Fresnel): ${ab[1]}")
        val lut = IblMath.brdfLut(16, 64)
        for (i in 0 until 16 * 16) {
            val a = lut[i * 2]; val b = lut[i * 2 + 1]
            assertTrue(a in 0f..1.001f && b in 0f..1.001f && a + b <= 1.02f, "texel $i: $a, $b")
        }
        // Rougher surfaces lose energy head-on (A + B drops)
        assertTrue(lut[(15 * 16 + 15) * 2] + lut[(15 * 16 + 15) * 2 + 1] < lut[15 * 2] + lut[15 * 2 + 1])
    }

    @Test
    fun shIrradiance_ConstantSkyGivesPiTimesRadiance() {
        val sh = IblMath.shConvolve(IblMath.shProject(constant(64, 32, 1f, 0.5f, 0.25f)))
        val e = Vector3f()
        for (n in listOf(Vector3f(0f, 1f, 0f), Vector3f(1f, 0f, 0f), Vector3f(0f, -0.6f, 0.8f))) {
            IblMath.shIrradiance(sh, n, e)
            assertEquals(PI.toFloat(), e.x, 0.02f)
            assertEquals(PI.toFloat() * 0.25f, e.z, 0.01f)
        }
    }

    @Test
    fun shIrradiance_UpperHemisphereLightsUpwardFacesOnly() {
        val sky = RadianceImage(64, 32).also { img ->
            for (y in 0 until 32) for (x in 0 until 64) { val v = if (y >= 16) 1f else 0f; img.set(x, y, v, v, v) }
        }
        val sh = IblMath.shConvolve(IblMath.shProject(sky))
        val e = Vector3f()
        assertEquals(PI.toFloat(), IblMath.shIrradiance(sh, Vector3f(0f, 1f, 0f), e).x, 0.25f, "up: the whole sky")
        assertEquals(PI.toFloat() / 2f, IblMath.shIrradiance(sh, Vector3f(1f, 0f, 0f), e).x, 0.15f, "sideways: half")
        assertTrue(IblMath.shIrradiance(sh, Vector3f(0f, -1f, 0f), e).x < 0.3f, "down: almost none")
    }
}
