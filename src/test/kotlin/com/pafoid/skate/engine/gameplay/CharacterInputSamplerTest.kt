package com.pafoid.skate.engine.gameplay

import com.pafoid.skate.engine.ecs.components.InputStateComponent
import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionInputReader
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.Keys
import com.pafoid.skate.engine.input.RawInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CharacterInputSamplerTest {

    private class FakeRaw : RawInput {
        val keys = mutableSetOf<Int>()
        val axes = FloatArray(6)
        var captured = false
        var dx = 0f
        var dy = 0f
        override fun isKeyDown(key: Int) = key in keys
        override fun wasKeyPressed(key: Int) = key in keys
        override fun isMouseButtonDown(button: Int) = false
        override fun wasMouseButtonPressed(button: Int) = false
        override fun mouseX() = 0f
        override fun mouseY() = 0f
        override fun mouseDeltaX() = dx
        override fun mouseDeltaY() = dy
        override fun scrollY() = 0f
        override fun isGamepadConnected(pad: Int) = true
        override fun gamepadAxis(axis: Int, pad: Int) = axes[axis]
        override fun isGamepadButtonDown(button: Int, pad: Int) = false
        override fun wasGamepadButtonPressed(button: Int, pad: Int) = false
        override fun isCursorCaptured() = captured
    }

    private val raw = FakeRaw()
    private val map = CharacterActions.register(ActionMap())
    private val reader = ActionInputReader(raw, map)
    private val sampler = CharacterInputSampler(reader)

    private fun frame(vararg states: InputStateComponent) {
        reader.update()
        states.forEach(sampler::sample)
    }

    @Test
    fun CharacterSampler_MoveSprintCrouchFromKeys() {
        val s = InputStateComponent()
        raw.keys += listOf(Keys.W, Keys.A, Keys.LEFT_SHIFT, Keys.LEFT_CONTROL)
        frame(s)
        assertEquals(-1f, s.moveDirection.x)
        assertEquals(1f, s.moveDirection.y)
        assertTrue(s.sprintPressed)
        assertTrue(s.crouchPressed)
        raw.keys.clear()
        frame(s)
        assertEquals(0f, s.moveDirection.length())
        assertFalse(s.sprintPressed)
    }

    @Test
    fun CharacterSampler_JumpEdgeIsPerFrameAndSharedByAllCharacters() {
        val a = InputStateComponent()
        val b = InputStateComponent()
        frame(a, b)
        raw.keys += Keys.SPACE
        frame(a, b)
        assertTrue(a.jumpPressed && a.jumpHeld)
        assertTrue(b.jumpPressed, "the second character sees the same press (INP-01 I7)")
        frame(a, b)
        assertFalse(a.jumpPressed || b.jumpPressed)
        assertTrue(a.jumpHeld)
    }

    @Test
    fun CharacterSampler_MountEdge() {
        val s = InputStateComponent()
        frame(s)
        raw.keys += Keys.E
        frame(s)
        assertTrue(s.mountPressed)
        frame(s)
        assertFalse(s.mountPressed)
    }

    @Test
    fun CharacterSampler_StickLookUsesSensitivityAndMouseOnlyWhenCaptured() {
        val s = InputStateComponent()
        raw.axes[2] = 1f
        raw.dx = 4f; raw.dy = -2f
        frame(s)
        assertEquals(2f, s.cameraLook.x, 1e-4f)
        assertEquals(0f, s.cameraLook.y, 1e-4f)
        raw.axes[2] = 0f
        raw.captured = true
        frame(s)
        assertEquals(0.4f, s.cameraLook.x, 1e-4f)
        assertEquals(-0.2f, s.cameraLook.y, 1e-4f)
        reader.update()
        sampler.sample(s, LookTuning(mouseSensitivity = 1f))
        assertEquals(4f, s.cameraLook.x, 1e-4f)
    }

    @Test
    fun LookTuning_ComesFromTheCharactersConfig() {
        val tuned = LocomotionConfig(look = LookTuning(stickSensitivity = 3f, mouseSensitivity = 0.5f))
        assertEquals(tuned.look, LocomotionBehavior(tuned).look)
        assertEquals(LookTuning(), LocomotionBehavior(LocomotionConfig()).look)
        // Saved with the character config, so it can be tuned in the editor and shipped.
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val back = json.decodeFromString(LocomotionConfig.serializer(), json.encodeToString(LocomotionConfig.serializer(), tuned))
        assertEquals(tuned.look, back.look)

        val s = InputStateComponent()
        raw.axes[2] = 1f
        reader.update()
        sampler.sample(s, tuned.look)
        assertEquals(3f, s.cameraLook.x, 1e-4f)
    }

    @Test
    fun CharacterSampler_SilencedGameplayGivesNeutralInput() {
        val s = InputStateComponent()
        raw.keys += listOf(Keys.W, Keys.SPACE)
        reader.contexts.push(InputContext("menu"), priority = 100, blocking = com.pafoid.skate.engine.input.ContextBlocking.ALL)
        frame(s)
        assertEquals(0f, s.moveDirection.y)
        assertFalse(s.jumpHeld)
    }

    @Test
    fun CharacterActions_RegisterKeepsExistingDefinitionsAndStableIds() {
        val m = ActionMap()
        CharacterActions.register(m)
        m.rebind(CharacterActions.JUMP, 0, ActionBinding.Key(Keys.Q))
        CharacterActions.register(m)
        assertEquals(ActionBinding.Key(Keys.Q), m.bindings(CharacterActions.JUMP)[0])
        assertEquals(listOf("moveX", "moveY", "lookX", "lookY", "jump", "sprint", "crouch", "toggle_mount"), CharacterActions.ids)
        assertTrue(CharacterActions.ids.all { m.definition(it)?.context == InputContext.GAMEPLAY })
    }
}
