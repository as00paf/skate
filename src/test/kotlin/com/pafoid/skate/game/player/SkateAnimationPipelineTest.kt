package com.pafoid.skate.game.player

import com.pafoid.skate.game.input.FakeActions

import com.pafoid.skate.engine.animation.graph.AnimationControllerAsset
import com.pafoid.skate.engine.animation.graph.ConditionOp
import com.pafoid.skate.engine.animation.graph.StateDef
import com.pafoid.skate.engine.animation.graph.TransitionCondition
import com.pafoid.skate.engine.animation.graph.TransitionDef
import com.pafoid.skate.engine.animation.procedural.HumanoidPoseDef
import com.pafoid.skate.engine.animation.procedural.PoseKeyDef
import com.pafoid.skate.engine.animation.procedural.ProceduralClipDef
import com.pafoid.skate.engine.animation.procedural.TestHumanoid
import com.pafoid.skate.engine.assets.PrefabsGenerator
import com.pafoid.skate.engine.assets.data.models.animations.Animation
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.systems.CharacterAnimationDriver
import com.pafoid.skate.engine.gameplay.CharacterBehavior
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.game.prefabs.Skater
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * GPL-06: gameplay inputs through the real [SkateBehavior], [CharacterAnimationDriver] and the bundled skater
 * controller (ANI-09) on a humanoid rig, the same chain PlayerMotionSystem runs each frame.
 */
class SkateAnimationPipelineTest {

    private val dt = 1f / 60f
    private val cfg = SkateControllerConfig()

    /** One rider: behavior, animator with the skater controller, and the last state the driver remembered. */
    class Rider(val behavior: CharacterBehavior, val animator: Animator) {
        var state = ""
    }

    companion object {
        fun animator(): Animator = Animator(
            animations = listOf("idle_0", "walking", "running", "jump", "falling idle", "skate riding")
                .map { Animation(it, emptyList(), 1f, "$it.fbx") }.toMutableList(),
        ).apply {
            controllerPath = Skater.CONTROLLER_PATH
            check(resolveController(LoggerService(), TestHumanoid.skeleton()))
        }
    }

    private fun rider(behavior: CharacterBehavior = SkateBehavior(cfg).also { it.mounted = true }) = Rider(behavior, animator())

    /** Runs [frames] frames of [input]; returns the controller state it settles in. */
    private fun Rider.ride(input: CharacterInput, frames: Int = 30): String {
        repeat(frames) {
            val motion = behavior.update(input, dt)
            state = CharacterAnimationDriver.drive(behavior, motion, state, animator)
            animator.stateMachine!!.update(dt)
        }
        return animator.stateMachine!!.let { it.nextState ?: it.currentState }
    }

    private fun on(speed: Float, push: Boolean = false, manual: Boolean = false, rail: Boolean = false, jump: Boolean = false) =
        CharacterInput(
            actions = FakeActions.skate(push = push, manual = manual, ollie = jump), horizontalSpeed = speed,
            groundTag = if (rail) PrefabsGenerator.GRINDABLE_TAG else null,
        )

    private fun air(speed: Float, vertical: Float) = CharacterInput(grounded = false, horizontalSpeed = speed, verticalSpeed = vertical)

    @Test
    fun SkateAnimation_ControllerFollowsTheRidingStates() {
        val r = rider()

        assertEquals("ride_idle", r.ride(on(0f)))
        assertEquals("push", r.ride(on(1f, push = true)))
        assertEquals("ride", r.ride(on(cfg.push.maxSpeed)))
        assertEquals("ollie", r.ride(on(4f, jump = true), frames = 1))
        assertEquals("ollie", r.ride(air(4f, -2f), frames = 20))
        // Touchdown: the controller plays its land state, then hands over to ride.
        assertEquals("land", r.ride(on(4f), frames = 3))
        assertEquals("ride", r.ride(on(4f), frames = 60))
        assertEquals("manual", r.ride(on(4f, manual = true), frames = 5))
        assertEquals("ride", r.ride(on(4f)))
        assertEquals("grind", r.ride(on(4f, rail = true)))
        assertEquals("bail", r.ride(on(0.3f, rail = true), frames = 5))
        assertEquals("ride_idle", r.ride(on(0f), frames = (cfg.bail.recoverySec / dt).toInt() + 30))
    }

    @Test
    fun SkateAnimation_HardLandingBailsInsteadOfLanding() {
        val r = rider()
        r.ride(on(4f))
        r.ride(on(4f, jump = true), frames = 1)
        r.ride(air(4f, -(cfg.air.bailImpactSpeed + 1f)), frames = 20)

        assertEquals("bail", r.ride(on(4f), frames = 3))
    }

    @Test
    fun SkateAnimation_ConfigBlendTimesReplaceTheControllerFades() {
        val slow = cfg.copy(blendTimes = cfg.blendTimes + ("push" to 1f))
        val r = rider(SkateBehavior(slow).also { it.mounted = true })
        r.ride(on(0f))

        assertEquals(slow.blendTimes, r.animator.stateMachine!!.blendOverrides)
        // A 1 s fade into push is still running after half a second.
        r.ride(on(1f, push = true), frames = 30)
        assertEquals("push", r.animator.stateMachine!!.nextState)
        r.ride(on(1f, push = true), frames = 40)
        assertEquals("push", r.animator.stateMachine!!.currentState)
    }

    @Test
    fun SkateAnimation_MountingSwitchesFromOnFootToRidingStates() {
        val behavior = SkateBehavior(cfg)
        val r = rider(behavior)
        assertEquals("walking", r.ride(CharacterInput(move = Vector2f(0f, 0.4f), horizontalSpeed = 1f)))

        behavior.mounted = true
        r.state = "" // what a mount request does: the driver re-requests even if the state name is unchanged
        assertEquals("ride_idle", r.ride(on(0f)))
    }

    @Test
    fun AnimationStateMachine_BlendOverrideReplacesTheTransitionDuration() {
        val pose = mapOf("x" to ProceduralClipDef(listOf(PoseKeyDef(0f, HumanoidPoseDef()))))
        val asset = AnimationControllerAsset(
            "c", "a", listOf(StateDef("a", "x"), StateDef("b", "x")),
            listOf(TransitionDef(null, "b", listOf(TransitionCondition("go", ConditionOp.TRUE)), duration = 1f)),
            poses = pose,
        )
        val machine = Animator().useController(asset, TestHumanoid.skeleton())
        machine.blendOverrides["b"] = 0f
        machine.parameters.setBool("go", true)

        machine.update(0.01f)

        assertEquals("b", machine.currentState)
    }
}
