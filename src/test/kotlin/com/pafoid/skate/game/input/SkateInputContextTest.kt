package com.pafoid.skate.game.input

import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.ecs.systems.CameraManager
import com.pafoid.skate.engine.ecs.systems.PlayerMotionSystem
import com.pafoid.skate.engine.gameplay.ActionPresets
import com.pafoid.skate.engine.gameplay.ActionQuery
import com.pafoid.skate.engine.gameplay.CharacterActions
import com.pafoid.skate.engine.gameplay.CharacterInput
import com.pafoid.skate.engine.gameplay.CharacterInputSampler
import com.pafoid.skate.engine.input.ActionInputReader
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.ContextBlocking
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.RawInput
import com.pafoid.skate.engine.input.UiNavigationActions
import com.pafoid.skate.engine.input.listeners.GamepadConstants
import com.pafoid.skate.game.player.SkateBehavior
import com.pafoid.skate.game.player.SkateControllerConfig
import com.pafoid.skate.game.player.SkateGameplay
import org.joml.Vector2f
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * INP-10 step 5 on the real input path (action map, context stack, reader, character sampler) with a scripted
 * keyboard: on the board the skate context takes Shift/Ctrl/Space over, on foot they are sprint/crouch/jump again,
 * and a ride plays the same states as before INP-10.
 */
class SkateInputContextTest {

    private class FakeRaw : RawInput {
        val keys = mutableSetOf<Int>()
        val buttons = mutableSetOf<Int>()
        override fun isKeyDown(key: Int) = key in keys
        override fun wasKeyPressed(key: Int) = key in keys
        override fun isMouseButtonDown(button: Int) = false
        override fun wasMouseButtonPressed(button: Int) = false
        override fun mouseX() = 0f
        override fun mouseY() = 0f
        override fun mouseDeltaX() = 0f
        override fun mouseDeltaY() = 0f
        override fun scrollY() = 0f
        override fun isGamepadConnected(pad: Int) = true
        override fun gamepadAxis(axis: Int, pad: Int) = 0f
        override fun isGamepadButtonDown(button: Int, pad: Int) = button in buttons
        override fun wasGamepadButtonPressed(button: Int, pad: Int) = false
    }

    private val dt = 1f / 60f
    private val config = SkateControllerConfig()

    /** One player on the engine's input path, as PlayerMotionSystem drives it. */
    private inner class Rig {
        val raw = FakeRaw()
        val map = SkateActions.register(CharacterActions.register(UiNavigationActions.register(ActionMap())))
        val reader = ActionInputReader(raw, map)
        val sampler = CharacterInputSampler(reader, contexts = reader.contexts, actionMap = map)
        val actions = sampler.actionsFor(this)
        val state = InputStateComponent()
        val behavior = SkateBehavior(config)
        var speed = 0f

        fun frame(vararg keys: Int): String {
            raw.keys.clear(); raw.keys += keys.toList()
            reader.update()
            sampler.sample(state)
            val motion = behavior.update(characterInput(state, actions, speed), dt)
            speed = motion.horizontalVelocity?.length() ?: speed
            return motion.state
        }

        val skateActive get() = reader.isContextActive(SkateActions.CONTEXT)
    }

    private fun characterInput(state: InputStateComponent, actions: ActionQuery, speed: Float) = CharacterInput(
        move = Vector2f(state.moveDirection), sprint = state.sprintPressed, crouch = state.crouchPressed,
        jumpPressed = state.jumpPressed, jumpHeld = state.jumpHeld, mountPressed = state.mountPressed,
        mountDistance = 0.5f, horizontalSpeed = speed, actions = actions,
    )

    @Test
    fun onFoot_TheKeysAreSprintCrouchJump_AndNoSkateContextIsHeld() {
        val rig = Rig()

        rig.frame(Keys.LEFT_SHIFT, Keys.LEFT_CONTROL)

        assertFalse(rig.skateActive)
        assertTrue(rig.state.sprintPressed)
        assertTrue(rig.state.crouchPressed)
        assertFalse(rig.reader.isActionDown(SkateActions.PUSH))
    }

    @Test
    fun mounting_HoldsSkateAboveGameplay_AndTheSameKeysBecomeSkateActions() {
        val rig = Rig()
        rig.frame(Keys.E)
        assertTrue(rig.behavior.mounted)

        val skate = rig.reader.contexts.entries().first { it.context == SkateActions.CONTEXT }
        assertEquals(SkateInputContext.PRIORITY, skate.priority)
        assertEquals(ContextBlocking.CONSUME_BOUND, skate.blocking)
        // Directly above gameplay, under ui.
        assertEquals(listOf(InputContext.UI, SkateActions.CONTEXT, InputContext.GAMEPLAY), rig.reader.contexts.entries().map { it.context })

        assertEquals("PUSH", rig.frame(Keys.LEFT_SHIFT, Keys.W))
        assertFalse(rig.state.sprintPressed, "sprint is consumed by skate.push")
        assertTrue(rig.reader.isActionDown(SkateActions.PUSH))
        // Sources skate does not bind pass through: steering and the mount toggle still work.
        assertEquals(1f, rig.state.moveDirection.y)
        assertTrue(rig.reader.isContextActive(InputContext.GAMEPLAY))
    }

    @Test
    fun riding_DoesNotTakeMenuInputAway() {
        val rig = Rig()
        rig.frame(Keys.E)
        assertTrue(rig.skateActive)

        // skate.ollie and ui.confirm share gamepad A; skate sits under ui, so the pause menu still gets it.
        val top = rig.reader.contexts.entries().first()
        assertEquals(InputContext.UI, top.context)
        assertTrue(rig.reader.isContextActive(InputContext.UI))
        rig.raw.buttons += GamepadConstants.BUTTON_A
        assertTrue(rig.reader.isActionDown(UiNavigationActions.CONFIRM), "A still confirms in menus")
        assertTrue(rig.reader.isActionDown(SkateActions.OLLIE), "and still ollies")
    }

    @Test
    fun dismounting_ReleasesSkate_AndShiftSprintsAgain() {
        val rig = Rig()
        rig.frame(Keys.E)
        rig.frame()
        rig.frame(Keys.E)
        assertFalse(rig.behavior.mounted)
        assertFalse(rig.skateActive)

        rig.frame(Keys.LEFT_SHIFT)

        assertTrue(rig.state.sprintPressed)
    }

    @Test
    fun spaceHeldWhileSteppingOn_IsNotAnOllie_ButTheNextPressIs() {
        val rig = Rig()
        rig.frame(Keys.SPACE)
        rig.frame(Keys.SPACE, Keys.E)
        assertTrue(rig.behavior.mounted)

        assertNotEquals("AIR", rig.frame(Keys.SPACE))
        assertNotEquals("AIR", rig.frame(Keys.SPACE))
        rig.frame()
        assertEquals("AIR", rig.frame(Keys.SPACE))
    }

    @Test
    fun aRide_PlaysTheSameStatesAsTheKeysDidBeforeInp10() {
        // Frame -> held keys: push, roll, ollie, roll, manual, push and ollie.
        val script = (0 until 320).map { f ->
            when (f) {
                in 0 until 60 -> setOf(Keys.LEFT_SHIFT)
                120, 280 -> setOf(Keys.SPACE)
                in 181 until 261 -> setOf(Keys.LEFT_CONTROL)
                in 261 until 320 -> setOf(Keys.LEFT_SHIFT)
                else -> emptySet()
            }
        }
        val rig = Rig().also { it.behavior.mounted = true }
        val now = script.map { keys -> rig.frame(*keys.toIntArray()) }

        // Before INP-10 the rider read Shift as sprint (push), Ctrl as crouch (manual) and the Space edge as jump.
        val legacy = SkateBehavior(config).also { it.mounted = true }
        var speed = 0f
        var spaceBefore = false
        val before = script.map { keys ->
            val space = Keys.SPACE in keys
            val actions = FakeActions.skate(push = Keys.LEFT_SHIFT in keys, manual = Keys.LEFT_CONTROL in keys, ollie = space && !spaceBefore)
            spaceBefore = space
            val motion = legacy.update(CharacterInput(horizontalSpeed = speed, actions = actions), dt)
            speed = motion.horizontalVelocity?.length() ?: speed
            motion.state
        }

        assertEquals(before, now)
        assertTrue(now.toSet().containsAll(listOf("PUSH", "RIDE", "AIR", "MANUAL")), "script covers ${now.toSet()}")
    }

    @Test
    fun skateDefaults_AreTheKeysThatPushedManualledAndOlliedBefore() {
        val map = SkateActions.register(CharacterActions.register(ActionMap()))
        listOf(
            SkateActions.PUSH to CharacterActions.SPRINT,
            SkateActions.MANUAL to CharacterActions.CROUCH,
            SkateActions.OLLIE to CharacterActions.JUMP,
        ).forEach { (skate, before) ->
            assertEquals(map.definition(before)!!.defaults, map.definition(skate)!!.defaults, skate)
            assertEquals(SkateActions.CONTEXT, map.definition(skate)!!.context)
        }
    }

    @Test
    fun skateGameplayRegister_AddsTheSkatePreset_AppliedOnceWithoutTouchingBindings() {
        SkateGameplay.register()
        val map = CharacterActions.register(ActionMap())

        ActionPresets.applyTo(map)
        val revision = map.revision
        ActionPresets.applyTo(map)

        assertTrue(SkateActions.ids.all { map.definition(it) != null })
        assertEquals(revision, map.revision)
    }

    @Test
    fun playerMotionSystem_ReleasesHeldContexts_OnSceneLoadAndWhenPlayStops() {
        SkateGameplay.register()
        val rig = Rig()
        val system = PlayerMotionSystem(CameraManager(EventSystem()), rig.sampler)
        val scene = Scene("Level")
        rig.frame(Keys.E)
        assertTrue(rig.skateActive)

        system.init(scene)
        assertFalse(rig.skateActive, "a new scene starts with no character context")

        rig.frame()
        assertTrue(rig.skateActive)
        scene.isRunning = false
        system.update(dt)
        assertFalse(rig.skateActive, "Stop releases the rider's context")
    }
}
