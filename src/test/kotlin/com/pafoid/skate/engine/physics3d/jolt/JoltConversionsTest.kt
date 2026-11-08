package com.pafoid.skate.engine.physics3d.jolt

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.pow

class JoltConversionsTest {

    @Test
    fun JoltDamping_DecayPerSecondMatchesBullet() {
        val dt = 1f / 60f
        for (bullet in listOf(0f, 0.1f, 0.5f, 0.9f)) {
            val jolt = JoltConversions.damping(bullet)
            val bulletDecay = (1f - bullet).pow(1f)
            val joltDecay = (1f - jolt * dt).pow(60)
            assertEquals(bulletDecay, joltDecay, 0.02f, "damping $bullet")
        }
    }

    @Test
    fun JoltDamping_ClampsOutOfRangeValues() {
        assertEquals(0f, JoltConversions.damping(-1f))
        assertTrue(JoltConversions.damping(1f).isFinite())
        assertEquals(JoltConversions.damping(0.999f), JoltConversions.damping(5f))
    }

    @Test
    fun JoltAxisRotation_MapsYOntoTheRequestedAxis() {
        assertNull(JoltConversions.axisRotation(1))
        val x = JoltConversions.axisRotation(0)!!.transform(Vector3f(0f, 1f, 0f))
        assertEquals(1f, x.x, 1e-5f)
        val z = JoltConversions.axisRotation(2)!!.transform(Vector3f(0f, 1f, 0f))
        assertEquals(1f, z.z, 1e-5f)
    }

    @Test
    fun JoltConvexRadius_FitsInsideTheShape() {
        assertEquals(0.04f, JoltConversions.boxConvexRadius(0.04f, Vector3f(1f)))
        assertEquals(0.01f, JoltConversions.boxConvexRadius(0.04f, Vector3f(1f, 0.01f, 1f)))
        assertEquals(0f, JoltConversions.boxConvexRadius(-1f, Vector3f(1f)))
        assertEquals(0.02f, JoltConversions.cylinderConvexRadius(0.04f, 0.02f, 1f))
        assertEquals(0.03f, JoltConversions.cylinderConvexRadius(0.04f, 1f, 0.03f))
    }

    @Test
    fun JoltAllowedDofs_ZeroComponentsLockRotation() {
        assertEquals(JoltConversions.ALL_DOFS, JoltConversions.allowedDofs(Vector3f(1f)))
        assertEquals(
            JoltConversions.TRANSLATION_ALL or JoltConversions.ROTATION_Y,
            JoltConversions.allowedDofs(Vector3f(0f, 1f, 0f)),
        )
        assertEquals(JoltConversions.TRANSLATION_ALL, JoltConversions.allowedDofs(Vector3f(0f)))
        assertTrue(JoltConversions.isFractional(Vector3f(0.5f, 1f, 0f)))
        assertFalse(JoltConversions.isFractional(Vector3f(0f, 1f, 0f)))
    }
}
