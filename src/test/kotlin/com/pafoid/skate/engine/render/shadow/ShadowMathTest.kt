package com.pafoid.skate.engine.render.shadow

import com.pafoid.skate.engine.render.lighting.LightData
import com.pafoid.skate.engine.render.lighting.LightType
import org.joml.Matrix4f
import org.joml.Vector3f
import org.joml.Vector4f
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShadowMathTest {

    // --- Cascade splits -------------------------------------------------------------------------------------------

    @Test
    fun CascadeSplits_EndsAtNearAndFarAndIncreases() {
        val s = CascadeSplits.compute(0.1f, 100f, 4)
        assertEquals(5, s.size)
        assertEquals(0.1f, s[0]); assertEquals(100f, s[4])
        for (i in 1..4) assertTrue(s[i] > s[i - 1], "split $i not increasing: ${s.toList()}")
    }

    @Test
    fun CascadeSplits_LambdaZeroIsUniformAndOneIsLogarithmic() {
        val uniform = CascadeSplits.compute(1f, 101f, 4, lambda = 0f)
        assertEquals(26f, uniform[1], 1e-4f); assertEquals(51f, uniform[2], 1e-4f); assertEquals(76f, uniform[3], 1e-4f)

        val log = CascadeSplits.compute(1f, 10_000f, 4, lambda = 1f)
        assertEquals(10f, log[1], 1e-2f); assertEquals(100f, log[2], 1e-1f); assertEquals(1000f, log[3], 1f)
    }

    @Test
    fun CascadeSplits_PracticalBlendSitsBetweenSchemes() {
        val l = 0.75f
        val s = CascadeSplits.compute(0.5f, 50f, 3, lambda = l)
        val p = 1f / 3f
        val expected = l * 0.5f * (50f / 0.5f).pow(p) + (1 - l) * (0.5f + 49.5f * p)
        assertEquals(expected, s[1], 1e-4f)
    }

    @Test
    fun CascadeSplits_SingleCascadeCoversWholeRangeAndRejectsBadInput() {
        val s = CascadeSplits.compute(0.1f, 50f, 1)
        assertEquals(listOf(0.1f, 50f), s.toList())
        assertFailsWith<IllegalArgumentException> { CascadeSplits.compute(0.1f, 50f, 0) }
        assertFailsWith<IllegalArgumentException> { CascadeSplits.compute(0.1f, 50f, 5) }
        assertFailsWith<IllegalArgumentException> { CascadeSplits.compute(10f, 5f, 2) }
    }

    // --- Cascade fitting ------------------------------------------------------------------------------------------

    private fun cameraInverseView(eye: Vector3f, yawDeg: Float, pitchDeg: Float = -20f): Matrix4f =
        Matrix4f().rotateX(Math.toRadians(-pitchDeg.toDouble()).toFloat())
            .rotateY(Math.toRadians(yawDeg.toDouble()).toFloat()).translate(-eye.x, -eye.y, -eye.z).invert()

    private val sun = Vector3f(-0.4f, -1f, -0.3f)

    @Test
    fun CascadeFitter_SliceCornersLandInsideTheShadowMap() {
        val fitter = CascadeFitter()
        val fit = CascadeFit()
        val inv = cameraInverseView(Vector3f(3f, 2f, 8f), 35f)
        val corners = fitter.perspectiveSliceCorners(inv, Math.toRadians(60.0).toFloat(), 16f / 9f, 2f, 15f)
        fitter.fit(corners, sun, 2048, 50f, fit)
        val v = Vector4f()
        for (c in corners) {
            fit.matrix.transform(v.set(c, 1f))
            assertTrue(abs(v.x) <= 1f && abs(v.y) <= 1f && abs(v.z) <= 1f, "corner $c -> $v outside NDC")
        }
    }

    @Test
    fun CascadeFitter_SliceCornersMatchCameraFrustum() {
        val inv = Matrix4f() // camera at origin looking down -Z
        val corners = CascadeFitter().perspectiveSliceCorners(inv, Math.toRadians(90.0).toFloat(), 2f, 1f, 4f)
        // tan(45deg) = 1: near half-height 1, half-width 2; far half-height 4, half-width 8.
        assertEquals(Vector3f(-2f, -1f, -1f), corners[0])
        assertEquals(Vector3f(8f, 4f, -4f), corners[7])
    }

    @Test
    fun CascadeFitter_RadiusDoesNotChangeWhenCameraRotates() {
        val fitter = CascadeFitter()
        val a = CascadeFit(); val b = CascadeFit()
        val fov = Math.toRadians(60.0).toFloat()
        fitter.fit(fitter.perspectiveSliceCorners(cameraInverseView(Vector3f(), 0f), fov, 1.6f, 5f, 20f), sun, 2048, 50f, a)
        fitter.fit(fitter.perspectiveSliceCorners(cameraInverseView(Vector3f(), 117f, 10f), fov, 1.6f, 5f, 20f), sun, 2048, 50f, b)
        assertEquals(a.radius, b.radius)
        assertEquals(a.texelWorldSize, b.texelWorldSize)
    }

    @Test
    fun CascadeFitter_SnapsToWholeTexelsSoShadowsDoNotShimmer() {
        val fitter = CascadeFitter()
        val fit = CascadeFit()
        val res = 1024
        val v = Vector4f()
        for (dx in listOf(0f, 0.0137f, 0.31f, 2.77f)) {
            fitter.fitSphere(Vector3f(10f + dx, 0f, -5f + dx * 0.5f), 12f, sun, res, 50f, fit)
            // A fixed world point must always land on the same sub-texel offset (here: a texel corner).
            fit.matrix.transform(v.set(0f, 0f, 0f, 1f))
            val texel = (v.x * 0.5f + 0.5f) * res
            assertEquals(round(texel), texel, 2e-2f, "origin at texel $texel for dx=$dx")
        }
    }

    @Test
    fun CascadeFitter_ExtendsDepthTowardsTheLightForCasters() {
        val fit = CascadeFitter().fitSphere(Vector3f(), 10f, Vector3f(0f, -1f, 0f), 1024, 50f, CascadeFit())
        assertEquals(70f, fit.depthRange, 1e-3f)
        val v = Vector4f()
        fit.matrix.transform(v.set(0f, 55f, 0f, 1f)) // tall caster 55 m above the slice centre
        assertTrue(v.z >= -1f && v.z <= 1f, "caster outside depth range: $v")
        fit.matrix.transform(v.set(0f, 65f, 0f, 1f))
        assertTrue(v.z < -1f, "beyond the extension should be clipped: $v")
    }

    @Test
    fun CascadeFitter_HandlesStraightDownSun() {
        val fit = CascadeFitter().fitSphere(Vector3f(1f, 0f, 1f), 5f, Vector3f(0f, -1f, 0f), 512, 10f, CascadeFit())
        assertTrue(fit.matrix.isFinite)
    }

    // --- Bias --------------------------------------------------------------------------------------------------

    @Test
    fun ShadowBias_ScalesWithTexelSizeKernelAndDepthRange() {
        assertEquals(0.01f, ShadowBias.normalOffset(0.01f, 0), 1e-6f)
        assertTrue(ShadowBias.normalOffset(0.01f, 2) > ShadowBias.normalOffset(0.01f, 0))
        assertEquals(0.01f + 0.002f, ShadowBias.normalOffset(0.01f, 0, 0.002f), 1e-6f)
        // Same world bias -> smaller depth-unit bias for a deeper cascade.
        assertTrue(ShadowBias.depthBias(100f) < ShadowBias.depthBias(10f))
        assertEquals(ShadowBias.BASE_DEPTH_BIAS_WORLD / 50f, ShadowBias.depthBias(50f), 1e-9f)
        assertEquals(0f, ShadowBias.depthBias(0f))
    }

    // --- Quality ------------------------------------------------------------------------------------------------

    @Test
    fun ShadowQuality_TiersIncreaseAndMapFromSettingNames() {
        val (low, med, high) = listOf(ShadowQualityLevel.LOW, ShadowQualityLevel.MEDIUM, ShadowQualityLevel.HIGH)
        assertTrue(low.cascadeCount <= med.cascadeCount && med.cascadeCount <= high.cascadeCount)
        assertTrue(low.pcfRadius < med.pcfRadius && med.pcfRadius < high.pcfRadius)
        assertTrue(low.localLayers < high.localLayers)
        for (q in ShadowQualityLevel.entries) {
            assertTrue(q.cascadeCount in 1..CascadeSplits.MAX_CASCADES)
            assertTrue(q.localLayers <= LocalShadows.MAX_LAYERS)
            assertTrue(q.localLayers >= LocalShadows.POINT_FACES, "$q must fit at least one point light")
        }
        assertEquals(ShadowQualityLevel.HIGH, ShadowQualityLevel.fromName("high"))
        assertEquals(ShadowQualityLevel.DEFAULT, ShadowQualityLevel.fromName(null))
        assertEquals(ShadowQualityLevel.DEFAULT, ShadowQualityLevel.fromName("ULTRA"))
    }

    @Test
    fun ShadowSettings_CycleWrapsAround() {
        val before = ShadowSettings.quality
        try {
            ShadowSettings.quality = ShadowQualityLevel.HIGH
            assertEquals(ShadowQualityLevel.LOW, ShadowSettings.cycle())
            assertEquals(ShadowQualityLevel.MEDIUM, ShadowSettings.cycle())
        } finally {
            ShadowSettings.quality = before
        }
    }

    // --- Local shadows ------------------------------------------------------------------------------------------

    private fun light(type: LightType, x: Float, cosOuter: Float = cos(Math.toRadians(30.0)).toFloat()) =
        LightData(type, Vector3f(x, 0f, 0f), Vector3f(0f, -1f, 0f), Vector3f(1f), 1f, 0.1f, 0.032f, 0.95f, cosOuter)

    @Test
    fun LocalShadows_AllocatesClosestLightsWithinLayerBudget() {
        val lights = listOf(light(LightType.SPOT, 300f), light(LightType.POINT, 0f), light(LightType.SPOT, 10f))
        val slots = LocalShadows.allocate(lights, Vector3f(), 7, ArrayList())
        assertEquals(listOf(1, 2), slots.map { it.lightIndex })
        assertEquals(listOf(0, 6), slots.map { it.baseLayer })
        assertEquals(7, slots.sumOf { it.layerCount })
    }

    @Test
    fun LocalShadows_SkipsPointThatDoesNotFitButKeepsSpots() {
        val lights = listOf(light(LightType.SPOT, 0f), light(LightType.POINT, 1f), light(LightType.SPOT, 2f))
        val slots = LocalShadows.allocate(lights, Vector3f(), 4, ArrayList())
        assertEquals(listOf(0, 2), slots.map { it.lightIndex })
        assertEquals(listOf(0, 1), slots.map { it.baseLayer })
        assertTrue(LocalShadows.allocate(lights, Vector3f(), 0, ArrayList()).isEmpty())
    }

    @Test
    fun LocalShadows_CapsShadowedLightCount() {
        val lights = List(20) { light(LightType.SPOT, it.toFloat()) }
        val slots = LocalShadows.allocate(lights, Vector3f(), LocalShadows.MAX_LAYERS, ArrayList())
        assertEquals(LocalShadows.MAX_SHADOWED_LIGHTS, slots.size)
    }

    @Test
    fun LocalShadows_CubeFacesCoverTheirDirection() {
        val pos = Vector3f(2f, 3f, -1f)
        val dirs = listOf(Vector3f(1f, 0.2f, -0.3f), Vector3f(-1f, 0.4f, 0.1f), Vector3f(0.3f, 1f, 0.2f),
            Vector3f(-0.2f, -1f, 0.5f), Vector3f(0.1f, -0.6f, 1f), Vector3f(0.5f, 0.5f, -1f))
        val m = Matrix4f(); val v = Vector4f()
        for ((expectedFace, d) in dirs.withIndex()) {
            assertEquals(expectedFace, LocalShadows.cubeFace(d.x, d.y, d.z))
            LocalShadows.pointFaceMatrix(pos, expectedFace, 0.1f, 20f, m)
            m.transform(v.set(pos.x + d.x * 3f, pos.y + d.y * 3f, pos.z + d.z * 3f, 1f))
            v.div(v.w)
            assertTrue(abs(v.x) <= 1f && abs(v.y) <= 1f && abs(v.z) <= 1f, "face $expectedFace misses $d: $v")
        }
    }

    @Test
    fun LocalShadows_SpotFrustumContainsTheConeEdge() {
        val cosOuter = cos(Math.toRadians(25.0)).toFloat()
        val fov = LocalShadows.spotFov(cosOuter)
        val m = LocalShadows.spotMatrix(Vector3f(0f, 5f, 0f), Vector3f(0f, -1f, 0f), fov, 0.1f, 20f, Matrix4f())
        // Point on the outer cone, 4 m below the light.
        val r = 4f * kotlin.math.tan(Math.toRadians(25.0)).toFloat()
        val v = m.transform(Vector4f(r, 1f, 0f, 1f)); v.div(v.w)
        assertTrue(abs(v.x) < 1f && abs(v.y) < 1f && v.z in -1f..1f, "cone edge outside spot map: $v")
        assertEquals(2f * 4f * kotlin.math.tan(fov / 2f) / 512f, LocalShadows.texelWorldSize(4f, kotlin.math.tan(fov / 2f), 512), 1e-6f)
    }

    // --- Culling ------------------------------------------------------------------------------------------------

    @Test
    fun ShadowCulling_RejectsCastersOutsideCascadeAndLight() {
        val fit = CascadeFitter().fitSphere(Vector3f(), 10f, Vector3f(0f, -1f, 0f), 1024, 20f, CascadeFit())
        assertTrue(ShadowCulling.intersectsCascade(fit, Vector3f(5f, 0f, 5f), 1f))
        assertTrue(ShadowCulling.intersectsCascade(fit, Vector3f(10.5f, 0f, 0f), 1f)) // overlaps the edge
        assertFalse(ShadowCulling.intersectsCascade(fit, Vector3f(40f, 0f, 0f), 1f))
        assertFalse(ShadowCulling.intersectsCascade(fit, Vector3f(0f, -30f, 0f), 1f)) // below the receivers
        assertTrue(ShadowCulling.intersectsCascade(fit, Vector3f(0f, 200f, 0f), 1f)) // high above: depth-clamped caster
        assertFalse(ShadowCulling.intersectsCascade(CascadeFit(), Vector3f(), 1f))

        assertTrue(ShadowCulling.intersectsLight(Vector3f(), 5f, Vector3f(5.5f, 0f, 0f), 1f))
        assertFalse(ShadowCulling.intersectsLight(Vector3f(), 5f, Vector3f(7f, 0f, 0f), 1f))
    }

    @Test
    fun ShadowFrame_ConsumeHandsDataOverOnce() {
        val f = ShadowFrame()
        assertFalse(f.consume())
        f.publish()
        assertTrue(f.consume())
        assertFalse(f.consume())
    }
}
