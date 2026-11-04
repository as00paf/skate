package com.pafoid.skate.engine.render.lighting

import com.pafoid.skate.engine.assets.data.models.MeshPart
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** REN-22: bounds are cached per model instance, never by the data class's value equality. */
class ModelBoundsCacheTest {

    private fun model() = `3dModel`(mesh = listOf(MeshPart(vertices = floatArrayOf(-1f, 0f, 0f, 1f, 2f, 0f))))

    @Test
    fun ModelBounds_SameInstanceIsComputedOnce() {
        val m = model()
        assertSame(ModelBounds.of(m), ModelBounds.of(m))
        assertEquals(0f, ModelBounds.of(m).center.x, 1e-6f)
        assertEquals(1f, ModelBounds.of(m).center.y, 1e-6f)
    }

    @Test
    fun ModelBounds_MutatedMeshOnEqualModelsDoesNotShareOrMissTheCache() {
        val a = model()
        val b = model() // equal by value, different instance
        val boundsA = ModelBounds.of(a)
        assertNotSame(boundsA, ModelBounds.of(b))
        // A data-class key's hash changed when its mesh was replaced, so the old cache missed every frame
        a.mesh = a.mesh + MeshPart(vertices = floatArrayOf(0f, 0f, 0f))
        assertSame(boundsA, ModelBounds.of(a))
    }
}
