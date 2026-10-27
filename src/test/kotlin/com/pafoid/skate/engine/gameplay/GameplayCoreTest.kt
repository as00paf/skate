package com.pafoid.skate.engine.gameplay

import org.joml.Vector2f
import org.joml.Vector3f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.math.PI

class StateMachineTest {
    private enum class S { A, B, C }

    @Test
    fun StateMachine_HighestPriorityTransitionWins() {
        val m = StateMachine<S, Unit>(S.A)
            .transition(S.A, S.B, 0) { true }
            .transition(S.A, S.C, 5) { true }
        assertTrue(m.update(Unit, 0.1f))
        assertEquals(S.C, m.current)
        assertEquals(S.A, m.previous)
    }

    @Test
    fun StateMachine_RunsHooksInOrderAndResetsTime() {
        val log = mutableListOf<String>()
        val m = StateMachine<S, Unit>(S.A)
            .transition(null, S.B) { true }
            .onExit(S.A) { log += "exit A" }
            .onEnter(S.B) { log += "enter B" }
        m.update(Unit, 0.5f)
        assertEquals(listOf("exit A", "enter B"), log)
        assertEquals(0f, m.timeInState)
        m.update(Unit, 0.25f)
        assertEquals(0.25f, m.timeInState)
    }

    @Test
    fun StateMachine_NoTransitionWhenGuardsFalseOrTargetIsCurrent() {
        val m = StateMachine<S, Unit>(S.A)
            .transition(S.A, S.A) { true }
            .transition(S.A, S.B) { false }
        assertFalse(m.update(Unit, 0.1f))
        assertEquals(S.A, m.current)
    }
}

class MotionMathTest {
    @Test
    fun MotionMath_ApproachNeverOvershoots() {
        assertEquals(1f, MotionMath.approach(0f, 1f, 5f))
        assertEquals(0.5f, MotionMath.approach(0f, 1f, 0.5f))
        assertEquals(0f, MotionMath.approach(1f, 0f, 5f))
    }

    @Test
    fun MotionMath_CameraRelativeDirectionIsHorizontalAndNormalized() {
        val dir = MotionMath.cameraRelativeDirection(Vector3f(0f, 0.5f, -1f), Vector3f(1f, 0f, 0f), Vector2f(1f, 1f))
        assertEquals(0f, dir.y)
        assertEquals(1f, dir.length(), 1e-4f)
        assertTrue(dir.x > 0f && dir.z < 0f)
    }

    @Test
    fun MotionMath_ZeroOrNaNInputGivesZeroDirection() {
        val f = Vector3f(0f, 0f, -1f)
        val r = Vector3f(1f, 0f, 0f)
        assertEquals(0f, MotionMath.cameraRelativeDirection(f, r, Vector2f(0f, 0f)).length())
        assertEquals(0f, MotionMath.cameraRelativeDirection(f, r, Vector2f(Float.NaN, 0f)).length())
    }

    @Test
    fun MotionMath_SmoothAngleTakesShortestArc() {
        val result = MotionMath.smoothAngle((PI - 0.1).toFloat(), (-PI + 0.1).toFloat(), 1f, 0.5f)
        assertTrue(result > PI.toFloat() - 0.1f) // moved toward +PI side, not back through 0
    }

    @Test
    fun MotionMath_YawRoundTrips() {
        val yaw = 1.1f
        assertEquals(yaw, MotionMath.yawOfDirection(MotionMath.directionOfYaw(yaw)), 1e-5f)
        val q = org.joml.Quaternionf().rotateY(yaw)
        assertEquals(yaw, MotionMath.yawOfRotation(q), 1e-5f)
    }
}

class LocomotionBehaviorTest {
    private val config = LocomotionConfig()
    private val dt = 0.016f

    private fun stick(x: Float, y: Float, grounded: Boolean = true, jump: Boolean = false, sprint: Boolean = false) =
        CharacterInput(move = Vector2f(x, y), grounded = grounded, jumpPressed = jump, sprint = sprint)

    @Test
    fun Locomotion_StickBelowDeadZoneStaysIdle() {
        val b = LocomotionBehavior(config)
        val m = b.update(stick(0.1f, 0.05f), dt)
        assertEquals("IDLE", m.state)
        assertNull(m.horizontalVelocity)
    }

    @Test
    fun Locomotion_GamepadStickWalksTowardCameraRelativeDirection() {
        val b = LocomotionBehavior(config)
        var m = b.update(stick(0f, 0.4f), dt)
        repeat(30) { m = b.update(stick(0f, 0.4f), dt) }
        assertEquals("WALK", m.state)
        val v = m.horizontalVelocity!!
        assertTrue(v.z < 0f) // camera forward is -Z
        assertEquals(config.walkSpeed, v.length(), 1e-3f)
        assertEquals("walking", m.animation)
    }

    @Test
    fun Locomotion_FullStickOrSprintRuns() {
        val b = LocomotionBehavior(config)
        assertEquals("RUN", b.update(stick(0f, 1f), dt).state)
        val b2 = LocomotionBehavior(config)
        assertEquals("RUN", b2.update(stick(0f, 0.3f, sprint = true), dt).state)
    }

    @Test
    fun Locomotion_JumpWindsUpThenAppliesImpulseOnce() {
        val b = LocomotionBehavior(config)
        assertEquals("TAKEOFF", b.update(stick(0f, 0f, jump = true), dt).state)
        var impulses = 0
        var t = 0f
        while (t < config.takeOffTime + 0.1f) {
            val m = b.update(stick(0f, 0f), dt)
            if (m.impulse != null) {
                impulses++
                assertEquals(config.jumpImpulse, m.impulse!!.y)
                assertEquals("AIR", m.state)
            }
            t += dt
        }
        assertEquals(1, impulses)
    }

    @Test
    fun Locomotion_LandingRequiresMinAirTime() {
        val b = LocomotionBehavior(config)
        b.update(stick(0f, 0f, grounded = false), 0.2f) // leaves ground past coyote time
        assertEquals("AIR", b.state)
        assertEquals("AIR", b.update(stick(0f, 0f, grounded = true), 0.01f).state) // too soon
        assertEquals("IDLE", b.update(stick(0f, 0f, grounded = true), 0.2f).state)
    }
}

class ConfigPropertiesTest {
    @Test
    fun ConfigProperties_FlattenListsEveryLeafAndSetUpdatesOne() {
        val root = Json.parseToJsonElement("""{"a":1,"b":{"c":2.5,"d":true}}""").jsonObject
        val flat = ConfigProperties.flatten(root)
        assertEquals(setOf("a", "b.c", "b.d"), flat.keys)
        val updated = ConfigProperties.set(root, "b.c", JsonPrimitive(9))
        assertEquals(9, ConfigProperties.flatten(updated).getValue("b.c").content.toInt())
        assertEquals(root, ConfigProperties.set(root, "missing.x", JsonPrimitive(1)))
    }

    @Test
    fun CharacterConfigs_InvalidTextFallsBackToDefaults() {
        val cfg = CharacterConfigs.decode("x", "{not json") { LocomotionConfig(walkSpeed = 99f) }
        assertEquals(99f, cfg.walkSpeed)
        assertEquals(LocomotionConfig(), CharacterConfigs.decode("x", "{}") { LocomotionConfig(walkSpeed = 99f) })
    }

    @Test
    fun CharacterConfigs_PartialFileKeepsDefaultsForMissingFields() {
        val cfg = CharacterConfigs.decode("x", """{"walkSpeed": 1.0}""") { LocomotionConfig() }
        assertEquals(1f, cfg.walkSpeed)
        assertEquals(LocomotionConfig().runSpeed, cfg.runSpeed)
    }

    @Suppress("unused")
    private fun unused(o: JsonObject) = o
}
