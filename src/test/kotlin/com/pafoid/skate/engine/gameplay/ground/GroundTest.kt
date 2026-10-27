package com.pafoid.skate.engine.gameplay.ground

import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.tan

/** Ray caster over an analytic world: ground height as a function of (x, z) plus optional wall boxes. */
private class FakeWorld(
    val height: (Float, Float) -> Float = { _, _ -> 0f },
    val walls: List<Wall> = emptyList(),
) : RayCaster {
    /** Axis aligned solid from [minX]..[maxX] in x, spanning y 0..[top], any z. */
    class Wall(val minX: Float, val maxX: Float, val top: Float)

    override fun cast(from: Vector3f, to: Vector3f): Float? {
        val steps = 400
        for (i in 0..steps) {
            val t = i / steps.toFloat()
            val x = from.x + (to.x - from.x) * t
            val y = from.y + (to.y - from.y) * t
            val z = from.z + (to.z - from.z) * t
            val solid = y <= height(x, z) || walls.any { x in it.minX..it.maxX && y <= it.top }
            if (solid) return t
        }
        return null
    }
}

class GroundMathTest {
    @Test
    fun GroundMath_NormalOfFlatPlaneIsUp() {
        val n = GroundMath.normalFromPoints(Vector3f(0f, 0f, 0f), Vector3f(1f, 0f, 0f), Vector3f(0f, 0f, 1f))!!
        assertEquals(1f, n.y, 1e-5f)
        assertEquals(0f, GroundMath.slopeDegrees(n), 1e-3f)
    }

    @Test
    fun GroundMath_NormalIsFlippedUpAndDegenerateReturnsNull() {
        val n = GroundMath.normalFromPoints(Vector3f(0f, 0f, 0f), Vector3f(0f, 0f, 1f), Vector3f(1f, 0f, 0f))!!
        assertTrue(n.y > 0f)
        assertNull(GroundMath.normalFromPoints(Vector3f(), Vector3f(1f, 0f, 0f), Vector3f(2f, 0f, 0f)))
    }

    @Test
    fun GroundMath_VerticalOnSlopeFollowsSurface() {
        // 45 degree slope rising along +x: normal tilts toward -x
        val n = Vector3f(-1f, 1f, 0f).normalize()
        assertEquals(1f, GroundMath.verticalOnSlope(1f, 0f, n), 1e-5f) // uphill climbs
        assertEquals(-1f, GroundMath.verticalOnSlope(-1f, 0f, n), 1e-5f) // downhill descends
        assertEquals(0f, GroundMath.verticalOnSlope(0f, 1f, n), 1e-5f) // across the slope stays level
        assertEquals(0f, GroundMath.verticalOnSlope(1f, 0f, Vector3f(1f, 0f, 0f)))
    }

    @Test
    fun GroundMath_SnapAndStepVelocitiesAreCapped() {
        assertEquals(-5f, GroundMath.snapVelocity(0.05f, 0.01f, 6f), 1e-4f)
        assertEquals(-6f, GroundMath.snapVelocity(1f, 0.01f, 6f))
        assertEquals(0f, GroundMath.snapVelocity(-0.1f, 0.01f, 6f))
        assertEquals(6f, GroundMath.stepVelocity(1f, 0.01f, 6f))
        assertEquals(0f, GroundMath.stepVelocity(0.2f, 0f, 6f))
    }

    @Test
    fun GroundMath_WalkableLimit() {
        assertTrue(GroundMath.isWalkable(50f, 50f))
        assertFalse(GroundMath.isWalkable(51f, 50f))
    }
}

class GroundDetectorTest {
    private val config = GroundConfig()
    private val feet = Vector3f(0f, 0f, 0f)

    @Test
    fun GroundDetector_FlatGroundUnderFeetIsStanding() {
        val info = GroundDetector(config, FakeWorld()).probe(feet)
        assertTrue(info.found)
        assertTrue(info.standing)
        assertEquals(0f, info.distance, 0.01f)
        assertEquals(0f, info.slopeDeg, 0.5f)
    }

    @Test
    fun GroundDetector_NoGroundWithinRangeIsNotFound() {
        val info = GroundDetector(config, FakeWorld(height = { _, _ -> -5f })).probe(feet)
        assertFalse(info.found)
        assertFalse(info.grounded)
    }

    @Test
    fun GroundDetector_HoverAboveGroundReportsDistanceButNotGrounded() {
        val info = GroundDetector(config, FakeWorld(height = { _, _ -> -0.2f })).probe(feet)
        assertTrue(info.found)
        assertFalse(info.grounded)
        assertEquals(0.2f, info.distance, 0.02f)
    }

    @Test
    fun GroundDetector_EstimatesSlopeFromSamples() {
        val slope = tan(Math.toRadians(30.0)).toFloat()
        val info = GroundDetector(config.copy(snapDistance = 0.5f), FakeWorld(height = { x, _ -> x * slope })).probe(feet)
        assertEquals(30f, info.slopeDeg, 2f)
        assertTrue(info.walkable)
        assertTrue(info.normal.x < 0f)
    }

    @Test
    fun GroundDetector_TooSteepSlopeIsNotWalkable() {
        val slope = tan(Math.toRadians(70.0)).toFloat()
        val info = GroundDetector(config.copy(snapDistance = 0.8f), FakeWorld(height = { x, _ -> x * slope })).probe(feet)
        assertFalse(info.walkable)
        assertFalse(info.standing)
    }

    @Test
    fun GroundDetector_FindsClimbableStepAhead() {
        val step = GroundDetector(config, FakeWorld(walls = listOf(FakeWorld.Wall(0.2f, 5f, 0.2f)))).findStep(feet, 1f, 0f)
        assertNotNull(step)
        assertEquals(0.2f, step!!, 0.02f)
    }

    @Test
    fun GroundDetector_IgnoresWallsTallerThanStepHeightAndOpenGround() {
        val tall = FakeWorld(walls = listOf(FakeWorld.Wall(0.2f, 5f, 1f)))
        assertNull(GroundDetector(config, tall).findStep(feet, 1f, 0f))
        assertNull(GroundDetector(config, FakeWorld()).findStep(feet, 1f, 0f))
        assertNull(GroundDetector(config, tall).findStep(feet, 0f, 0f))
    }
}

class GroundMotionTest {
    private val config = GroundConfig()
    private val dt = 0.016f
    private val standing = GroundInfo(found = true, distance = 0f, grounded = true, walkable = true)

    private fun vy(
        ground: GroundInfo = standing,
        step: Float? = null,
        wasGrounded: Boolean = true,
        currentVy: Float = 0f,
        vx: Float = 0f,
        jumping: Boolean = false,
    ) = GroundMotion.verticalVelocity(config, ground, step, wasGrounded, currentVy, vx, 0f, jumping, dt)

    @Test
    fun GroundMotion_JumpingOrRisingLeavesPhysicsAlone() {
        assertNull(vy(jumping = true))
        assertNull(vy(currentVy = 3f))
    }

    @Test
    fun GroundMotion_StandingStillOnGroundIsUntouched() {
        assertNull(vy())
    }

    @Test
    fun GroundMotion_MovingFollowsSlope() {
        val slope = GroundInfo(true, 0f, Vector3f(-1f, 1f, 0f).normalize(), 45f, grounded = true, walkable = true)
        assertEquals(2f, vy(ground = slope, vx = 2f)!!, 1e-4f)
    }

    @Test
    fun GroundMotion_StepClimbsAndRespectsToggle() {
        assertEquals(0.05f / dt, vy(step = 0.05f)!!, 1e-3f)
        assertEquals(config.maxSnapSpeed, vy(step = 0.2f)!!, 1e-3f)
        val off = GroundMotion.verticalVelocity(config.copy(stepUp = false), standing, 0.2f, true, 0f, 0f, 0f, false, dt)
        assertNull(off)
    }

    @Test
    fun GroundMotion_SnapsDownOnlyWhenRecentlyGrounded() {
        val gap = GroundInfo(found = true, distance = 0.1f, grounded = false, walkable = true)
        assertTrue(vy(ground = gap, wasGrounded = true)!! < 0f)
        assertNull(vy(ground = gap, wasGrounded = false))
        assertNull(GroundMotion.verticalVelocity(config.copy(snapToGround = false), gap, null, true, 0f, 0f, 0f, false, dt))
    }

    @Test
    fun GroundMotion_NeverSnapsOntoUnwalkableGround() {
        val steep = GroundInfo(found = true, distance = 0.1f, grounded = false, walkable = false)
        assertNull(vy(ground = steep))
    }
}

class CharacterExtensionTest {
    /** A non-skate game extends the generic locomotion and tunes ground handling through its own config. */
    private class Explorer(config: com.pafoid.skate.engine.gameplay.LocomotionConfig) :
        com.pafoid.skate.engine.gameplay.LocomotionBehavior(config)

    @Test
    fun CharacterExtension_CustomBehaviorUsesRegistryAndGroundConfig() {
        val config = com.pafoid.skate.engine.gameplay.LocomotionConfig(ground = GroundConfig(stepHeight = 0.5f))
        com.pafoid.skate.engine.gameplay.CharacterBehaviors.register("explorer") { Explorer(config) }
        val behavior = com.pafoid.skate.engine.gameplay.CharacterBehaviors.create("explorer", "")
        assertTrue(behavior is Explorer)
        assertEquals(0.5f, behavior.ground.stepHeight)
    }
}
