package com.pafoid.skate.game.player

import com.pafoid.skate.engine.gameplay.CharacterBehaviors
import com.pafoid.skate.engine.gameplay.CharacterConfigs
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.LocomotionBehavior
import com.pafoid.skate.game.prefabs.Skater
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SkateBehaviorTest {
    private val cfg = SkateControllerConfig()
    private val dt = 0.016f

    private fun mounted() = SkateBehavior(cfg).also { it.mounted = true }

    private fun input(
        speed: Float = 0f,
        grounded: Boolean = true,
        push: Boolean = false,
        jump: Boolean = false,
        manual: Boolean = false,
        stick: Vector2f = Vector2f(),
        velocityYaw: Float = Float.NaN,
    ) = CharacterInput(
        move = stick, sprint = push, jumpPressed = jump, crouch = manual,
        grounded = grounded, horizontalSpeed = speed, velocityYaw = velocityYaw,
    )

    private fun SkateBehavior.step(i: CharacterInput, time: Float = dt) = update(i, time).state

    @Test
    fun Skate_BundledConfigMatchesDefaults() {
        val loaded = CharacterConfigs.load<SkateControllerConfig>(Skater.CONFIG_PATH) { error("bundled config missing or invalid") }
        assertEquals(SkateControllerConfig(), loaded)
    }

    @Test
    fun Skate_RegisteredBehaviorIsCreatedFromConfig() {
        SkateGameplay.register()
        assertTrue(CharacterBehaviors.create(SkateBehavior.ID, Skater.CONFIG_PATH) is SkateBehavior)
        assertTrue(CharacterBehaviors.create("does-not-exist", "") is LocomotionBehavior)
    }

    @Test
    fun Skate_NotMountedWalksWithGamepad() {
        val b = SkateBehavior(cfg)
        val m = b.update(input(stick = Vector2f(0f, 0.4f)), dt)
        assertEquals("WALK", m.state)
        assertNotNull(m.horizontalVelocity)
    }

    @Test
    fun Skate_IdleToPushToRideAndBackToIdle() {
        val b = mounted()
        assertEquals("PUSH", b.step(input(push = true)))
        assertEquals("RIDE", b.step(input(speed = cfg.push.maxSpeed, push = true)))
        assertEquals("RIDE", b.step(input(speed = 3f)))
        assertEquals("PUSH", b.step(input(speed = 3f, push = true)))
        assertEquals("RIDE", b.step(input(speed = 3f)))
        assertEquals("IDLE", b.step(input(speed = 0.1f)))
    }

    @Test
    fun Skate_PushAcceleratesTowardMaxSpeed() {
        val b = mounted()
        val m = b.update(input(speed = 1f, push = true), 0.5f)
        assertEquals("PUSH", m.state)
        assertEquals(1f + cfg.push.acceleration * 0.5f, m.horizontalVelocity!!.length(), 1e-3f)
    }

    @Test
    fun Skate_OllieLeavesGroundWithImpulseThenLandsInRide() {
        val b = mounted()
        val m = b.update(input(speed = 3f, jump = true), dt)
        assertEquals("AIR", m.state)
        assertEquals(cfg.air.ollieImpulse, m.impulse!!.y)
        assertEquals("AIR", b.step(input(speed = 3f, grounded = true), 0.01f)) // ground probe lag
        assertEquals("RIDE", b.step(input(speed = 3f, grounded = true), 0.2f))
    }

    @Test
    fun Skate_FallingOffLedgeBecomesAirAfterCoyoteTime() {
        val b = mounted()
        b.step(input(speed = 3f))
        assertEquals("RIDE", b.step(input(speed = 3f, grounded = false), 0.05f))
        assertEquals("AIR", b.step(input(speed = 3f, grounded = false), 0.1f))
    }

    @Test
    fun Skate_LandingSidewaysBails() {
        val b = mounted()
        b.step(input(speed = 3f, jump = true))
        val sideways = input(speed = 3f, velocityYaw = Math.toRadians(120.0).toFloat())
        assertEquals("BAIL", b.step(sideways, 0.2f))
        assertEquals("BAIL", b.step(input(), 0.5f))
        assertEquals("IDLE", b.step(input(), cfg.bail.recoverySec))
    }

    @Test
    fun Skate_GrindNeedsSensorAndSpeedAndEndsOnJump() {
        val b = mounted()
        b.step(input(speed = 3f))
        assertEquals("RIDE", b.step(input(speed = 3f)))
        b.grindSensor = { true }
        assertEquals("RIDE", b.step(input(speed = 1f)))
        assertEquals("GRIND", b.step(input(speed = 3f)))
        assertEquals("AIR", b.step(input(speed = 3f, jump = true)))
    }

    @Test
    fun Skate_GrindBailsWhenTooSlow() {
        val b = mounted()
        b.step(input(speed = 3f))
        b.grindSensor = { true }
        b.step(input(speed = 3f))
        assertEquals("BAIL", b.step(input(speed = 0.2f)))
    }

    @Test
    fun Skate_ManualRequiresSpeedAndBailsWhenUnbalanced() {
        val b = mounted()
        b.step(input(speed = 3f))
        assertEquals("RIDE", b.step(input(speed = 1f, manual = true)))
        assertEquals("MANUAL", b.step(input(speed = 3f, manual = true)))
        var state = "MANUAL"
        var t = 0f
        while (state == "MANUAL" && t < 10f) {
            state = b.step(input(speed = 3f, manual = true))
            t += dt
        }
        assertEquals("BAIL", state)
    }

    @Test
    fun Skate_ManualStickCorrectionKeepsBalance() {
        val b = mounted()
        b.step(input(speed = 3f))
        b.step(input(speed = 3f, manual = true))
        repeat(300) {
            val correction = if (b.manualAngle > 0f) 1f else -1f
            b.step(input(speed = 3f, manual = true, stick = Vector2f(0f, correction)))
        }
        assertEquals("MANUAL", b.state)
    }

    @Test
    fun Skate_ReleasingManualReturnsToRide() {
        val b = mounted()
        b.step(input(speed = 3f))
        b.step(input(speed = 3f, manual = true))
        assertEquals("RIDE", b.step(input(speed = 3f)))
    }

    @Test
    fun Skate_SteeringTurnsHeadingAndBrakingSlowsDown() {
        val b = mounted()
        b.step(input(speed = 3f))
        val turn = b.update(input(speed = 3f, stick = Vector2f(1f, 0f)), 0.1f)
        assertTrue(turn.targetYaw < 0f)
        val brake = b.update(input(speed = 3f, stick = Vector2f(0f, -1f)), 0.1f)
        assertTrue(brake.horizontalVelocity!!.length() < 3f - cfg.ride.rollingDrag * 0.1f)
    }
}
