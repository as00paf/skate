package com.pafoid.skate.engine.render.lighting

import org.joml.Matrix4f
import org.joml.Vector3f
import java.nio.FloatBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LightingTest {
    private fun point(x: Float, intensity: Float = 1f, q: Float = 0.032f, l: Float = 0.1f) =
        LightData(LightType.POINT, Vector3f(x, 0f, 0f), Vector3f(), Vector3f(intensity), 1f, l, q)

    @Test
    fun LightRange_AttenuatesToCutoffAndGrowsWithIntensity() {
        val r1 = LightRange.of(1f, 1f, 0.1f, 0.032f)
        val r10 = LightRange.of(10f, 1f, 0.1f, 0.032f)
        assertEquals(1f, 1f / (1f + 0.1f * r1 + 0.032f * r1 * r1) * 256f, 0.02f)
        assertTrue(r10 > r1)
        assertEquals(0f, LightRange.of(0f, 1f, 0.1f, 0.032f))
    }

    @Test
    fun LightCulling_RejectsLightsOutOfReach() {
        val lights = listOf(point(0f), point(500f))
        val out = IntArray(4)
        val n = LightCulling.select(lights, Vector3f(1f, 0f, 0f), 1f, out)
        assertEquals(1, n); assertEquals(0, out[0])
    }

    @Test
    fun LightCulling_BoundsRadiusExtendsReach() {
        val l = point(0f)
        val far = Vector3f(l.range + 5f, 0f, 0f)
        assertEquals(0, LightCulling.select(listOf(l), far, 1f, IntArray(2)))
        assertEquals(1, LightCulling.select(listOf(l), far, 10f, IntArray(2)))
    }

    @Test
    fun LightCulling_KeepsStrongestWhenOverCapacity() {
        val lights = (0 until 70).map { point(0f, intensity = 1f + it) } // same spot, later = stronger
        val out = IntArray(LightCulling.MAX_LIGHTS_PER_OBJECT)
        val n = LightCulling.select(lights, Vector3f(), 0f, out)
        assertEquals(16, n)
        assertEquals((54..69).toSet(), out.toSet())
        assertEquals(16, out.toSet().size)
    }

    @Test
    fun LightPacking_Writes64BytesPerLightAndCapsAt64() {
        val buf = FloatBuffer.allocate(LightPacking.MAX_LIGHTS * LightPacking.FLOATS_PER_LIGHT)
        val spot = LightData(LightType.SPOT, Vector3f(1f, 2f, 3f), Vector3f(0f, -1f, 0f), Vector3f(0.5f), 1f, 0.1f, 0.2f, 0.9f, 0.8f)
        assertEquals(1, LightPacking.pack(listOf(spot), buf))
        assertEquals(16, buf.position())
        assertEquals(1f, buf.get(7)); assertEquals(0.9f, buf.get(11)); assertEquals(0.8f, buf.get(15))
        buf.clear()
        assertEquals(64, LightPacking.pack(List(100) { point(0f) }, buf))
    }

    @Test
    fun ModelBounds_CentersAndScalesToWorld() {
        val b = ModelBounds.compute(listOf(floatArrayOf(0f, 0f, 0f, 2f, 0f, 0f)))
        assertEquals(1f, b.center.x); assertEquals(1f, b.radius, 1e-5f)
        val c = Vector3f()
        val r = b.toWorld(Matrix4f().translate(10f, 0f, 0f).scale(3f), c)
        assertEquals(3f, r, 1e-4f); assertEquals(13f, c.x, 1e-4f)
    }

    @Test
    fun LightingShader_LimitsMatchKotlinConstants() {
        val src = javaClass.getResource("/shaders/shader_3d_default.glsl")!!.readText()
        assertTrue("#define MAX_LIGHTS ${LightPacking.MAX_LIGHTS}\n" in src.replace("\r", ""))
        assertTrue("#define MAX_LIGHTS_PER_OBJECT ${LightCulling.MAX_LIGHTS_PER_OBJECT}\n" in src.replace("\r", ""))
    }
}
