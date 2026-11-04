package com.pafoid.skate.engine.render.shadow

import org.joml.Matrix4f
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** REN-22: shadow layers are re-rendered only when their light matrix or casters change. */
class ShadowLayerCacheTest {

    private fun signature(matrix: Matrix4f, vararg casterWorld: Matrix4f): Long {
        var h = ShadowSignature.mix(ShadowSignature.SEED, matrix)
        for (w in casterWorld) h = ShadowSignature.mix(h, w)
        return h
    }

    @Test
    fun ShadowLayerCache_UnchangedLayerIsSkipped() {
        val cache = ShadowLayerCache(3)
        val light = Matrix4f().ortho(-10f, 10f, -10f, 10f, 0f, 50f)
        val caster = Matrix4f().translate(1f, 0f, 0f)

        assertTrue(cache.needsRender(0, signature(light, caster)))
        assertFalse(cache.needsRender(0, signature(light, caster)))
        assertTrue(cache.needsRender(1, signature(light, caster)), "layers are tracked independently")
    }

    @Test
    fun ShadowLayerCache_MovedCasterOrLightRerenders() {
        val cache = ShadowLayerCache(1)
        val light = Matrix4f().ortho(-10f, 10f, -10f, 10f, 0f, 50f)
        val caster = Matrix4f()
        cache.needsRender(0, signature(light, caster))

        assertTrue(cache.needsRender(0, signature(light, Matrix4f().translate(0f, 0.001f, 0f))))
        assertTrue(cache.needsRender(0, signature(Matrix4f(light).translate(0.5f, 0f, 0f), Matrix4f().translate(0f, 0.001f, 0f))))
    }

    @Test
    fun ShadowLayerCache_InvalidateAllRerendersEverything() {
        val cache = ShadowLayerCache(2)
        val s = signature(Matrix4f())
        cache.needsRender(0, s); cache.needsRender(1, s)

        cache.invalidateAll()

        assertTrue(cache.needsRender(0, s))
        assertTrue(cache.needsRender(1, s))
    }

    @Test
    fun ShadowSignature_CasterOrderAndExtraCastersChangeIt() {
        val l = Matrix4f()
        val a = Matrix4f().translate(1f, 0f, 0f)
        val b = Matrix4f().translate(0f, 1f, 0f)
        assertNotEquals(signature(l, a), signature(l, a, b))
        assertNotEquals(signature(l, a, b), signature(l, b, a))
    }
}
