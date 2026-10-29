package com.pafoid.skate.engine.gameplay.ik

import com.pafoid.skate.engine.animation.ik.IkRig
import com.pafoid.skate.engine.physics3d.PhysicsHit
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FootIkTest {
    private val config = FootIkConfig()
    private val left = config.legs[0]

    private fun hit(point: Vector3f, normal: Vector3f = Vector3f(0f, 1f, 0f)) = PhysicsHit(null, point, normal, 0.5f)

    @Test
    fun FootIk_TargetIsSurfaceLiftedByAnkleHeight() {
        val feet = Vector3f(1f, 0f, 2f)
        val target = FootPlacement.compute(feet, 0f, left, config) { from, _ -> hit(Vector3f(from.x, 0.2f, from.z)) }

        assertTrue(target.valid)
        assertEquals(0.2f + config.ankleHeight, target.position.y, 1e-5f)
        assertEquals(1f + left.lateral, target.position.x, 1e-5f)
        assertEquals(2f, target.position.z, 1e-5f)
    }

    @Test
    fun FootIk_SlopeNormalOffsetsTargetAlongNormal() {
        val normal = Vector3f(0.6f, 0.8f, 0f)
        val target = FootPlacement.compute(Vector3f(), 0f, left, config) { _, _ -> hit(Vector3f(0f, 0.1f, 0f), normal) }
        assertEquals(0.6f * config.ankleHeight, target.position.x, 1e-5f)
        assertEquals(0.1f + 0.8f * config.ankleHeight, target.position.y, 1e-5f)
    }

    @Test
    fun FootIk_ProbeSideRotatesWithHeading() {
        var from = Vector3f()
        FootPlacement.compute(Vector3f(), Math.PI.toFloat() / 2, left, config) { f, _ -> from = f; null }
        assertEquals(0f, from.x, 1e-5f)
        assertEquals(-left.lateral, from.z, 1e-5f)
    }

    @Test
    fun FootIk_NoGroundOrTooFarIsInvalid() {
        assertFalse(FootPlacement.compute(Vector3f(), 0f, left, config) { _, _ -> null }.valid)
        assertFalse(FootPlacement.compute(Vector3f(), 0f, left, config) { _, _ -> hit(Vector3f(0f, config.maxReach + 0.1f, 0f)) }.valid)
    }

    @Test
    fun FootIk_WeightRampsAtConfiguredRate() {
        assertEquals(0.5f, FootPlacement.weightStep(0f, 1f, 0.075f, 0.15f), 1e-5f)
        assertEquals(1f, FootPlacement.weightStep(0.95f, 1f, 0.1f, 0.15f), 0f)
        assertEquals(0f, FootPlacement.weightStep(0.05f, 0f, 0.1f, 0.15f), 0f)
        assertEquals(1f, FootPlacement.weightStep(0f, 1f, 0.016f, 0f), 0f)
    }

    @Test
    fun FootIk_BinderRegistersLegsOnceAndFollowsTargets() {
        val rig = IkRig()
        val binder = FootIkBinder(config.copy(rampSeconds = 0f))
        val targets = listOf(
            FootTarget(Vector3f(1f, 2f, 3f), true),
            FootTarget(Vector3f(), false),
        )
        binder.update(rig, targets, enabled = true, dt = 0.016f)
        binder.update(rig, targets, enabled = true, dt = 0.016f)

        assertEquals(2, rig.twoBone.size)
        assertEquals(Vector3f(1f, 2f, 3f), rig.twoBone[0].target)
        assertEquals(1f, rig.twoBone[0].weight)
        assertEquals(0f, rig.twoBone[1].weight)

        binder.update(rig, targets, enabled = false, dt = 0.016f)
        assertEquals(0f, rig.twoBone[0].weight)
    }
}
