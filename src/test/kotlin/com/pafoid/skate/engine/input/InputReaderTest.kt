package com.pafoid.skate.engine.input

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText

class InputReaderTest {

    private class FakeRaw : RawInput {
        val keys = mutableSetOf<Int>()
        val pads = mutableSetOf<Int>()
        val axes = FloatArray(6)
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
        override fun gamepadAxis(axis: Int, pad: Int) = axes[axis]
        override fun isGamepadButtonDown(button: Int, pad: Int) = button in pads
        override fun wasGamepadButtonPressed(button: Int, pad: Int) = button in pads
    }

    private fun reader(raw: FakeRaw, vararg defs: ActionDefinition): ActionInputReader {
        val map = ActionMap()
        defs.forEach { map.define(it) }
        return ActionInputReader(raw, map)
    }

    private fun def(id: String, vararg b: ActionBinding, context: InputContext = InputContext.GAMEPLAY) =
        ActionDefinition(id, context, "Test", defaults = b.toList())

    @Test
    fun InputReader_ActionReadsKeyAndGamepadSources() {
        val raw = FakeRaw()
        val reader = reader(raw, def("jump", ActionBinding.Key(Keys.SPACE), ActionBinding.GamepadButton(0)))
        assertFalse(reader.isActionDown("jump"))
        raw.keys.add(Keys.SPACE)
        assertTrue(reader.isActionDown("jump"))
        raw.keys.clear(); raw.pads.add(0)
        assertEquals(1f, reader.actionValue("jump"))
    }

    @Test
    fun InputReader_AxisAppliesDeadZoneAndScale() {
        val raw = FakeRaw()
        val reader = reader(raw, def("moveY", ActionBinding.GamepadAxis(1, scale = -1f)))
        raw.axes[1] = 0.1f
        assertEquals(0f, reader.actionValue("moveY"))
        raw.axes[1] = -1f
        assertEquals(1f, reader.actionValue("moveY"), 1e-6f)
    }

    @Test
    fun InputReader_StrongestBindingWinsAndUnknownActionIsZero() {
        val raw = FakeRaw()
        val reader = reader(raw, def("moveX", ActionBinding.Key(Keys.D), ActionBinding.GamepadAxis(0)))
        raw.keys.add(Keys.D); raw.axes[0] = -0.4f
        assertEquals(1f, reader.actionValue("moveX"))
        assertEquals(0f, reader.actionValue("nope"))
        assertFalse(reader.isActionDown("nope"))
    }

    @Test
    fun InputReader_WasActionPressedFiresOncePerPress() {
        val raw = FakeRaw()
        val reader = reader(raw, def("jump", ActionBinding.Key(Keys.SPACE)))
        reader.update()
        assertFalse(reader.wasActionPressed("jump"))
        raw.keys.add(Keys.SPACE)
        reader.update()
        assertTrue(reader.wasActionPressed("jump"))
        reader.update()
        assertFalse(reader.wasActionPressed("jump"))
        raw.keys.clear(); reader.update()
        raw.keys.add(Keys.SPACE); reader.update()
        assertTrue(reader.wasActionPressed("jump"))
    }

    @Test
    fun InputReader_InactiveContextReadsZero() {
        val raw = FakeRaw()
        val reader = reader(raw, def("gizmo", ActionBinding.Key(Keys.W), context = InputContext.EDITOR))
        raw.keys.add(Keys.W)
        assertFalse(reader.isActionDown("gizmo"))
        assertFalse(reader.isContextActive(InputContext.EDITOR))
        reader.contexts.push(InputContext.EDITOR, priority = 50)
        assertTrue(reader.isActionDown("gizmo"))
        assertTrue(reader.isContextActive(InputContext.EDITOR))
    }

    @Test
    fun InputReader_HigherContextConsumesSharedSourceUntilPopped() {
        val raw = FakeRaw()
        val vehicle = InputContext("vehicle")
        val reader = reader(raw,
            def("jump", ActionBinding.Key(Keys.SPACE), ActionBinding.GamepadButton(0)),
            def("horn", ActionBinding.GamepadButton(0), context = vehicle))
        raw.pads.add(0)
        val h = reader.contexts.push(vehicle, priority = 10)
        assertTrue(reader.isActionDown("horn"))
        assertFalse(reader.isActionDown("jump"), "pad A belongs to the vehicle")
        raw.pads.clear(); raw.keys.add(Keys.SPACE)
        assertTrue(reader.isActionDown("jump"), "unshared sources still reach gameplay")
        reader.contexts.pop(h)
        raw.keys.clear(); raw.pads.add(0)
        assertTrue(reader.isActionDown("jump"))
        assertFalse(reader.isActionDown("horn"))
    }

    @Test
    fun InputReader_ConsumedAxisHidesBothDirections() {
        val raw = FakeRaw()
        val menu = InputContext("menu")
        val reader = reader(raw,
            def("moveX", ActionBinding.GamepadAxis(0)),
            def("menu.left", ActionBinding.GamepadAxis(0, -1f), context = menu))
        raw.axes[0] = 1f
        reader.contexts.push(menu, priority = 10)
        assertEquals(0f, reader.actionValue("moveX"))
    }

    @Test
    fun InputReader_ModalContextSilencesGameplay() {
        val raw = FakeRaw()
        val reader = reader(raw, def("jump", ActionBinding.Key(Keys.SPACE)))
        raw.keys.add(Keys.SPACE)
        val h = reader.contexts.push(InputContext("pause"), priority = 100, blocking = ContextBlocking.ALL)
        assertFalse(reader.isActionDown("jump"))
        reader.contexts.pop(h)
        assertTrue(reader.isActionDown("jump"))
    }

    @Test
    fun InputReader_PressHeldAcrossContextChangeNeedsNewPress() {
        val raw = FakeRaw()
        val reader = reader(raw, def("jump", ActionBinding.Key(Keys.SPACE)))
        reader.update(); reader.wasActionPressed("jump"); reader.update()
        val h = reader.contexts.push(InputContext("pause"), priority = 100, blocking = ContextBlocking.ALL)
        raw.keys.add(Keys.SPACE) // pressed while the modal context is up
        reader.update()
        assertFalse(reader.wasActionPressed("jump"))
        reader.contexts.pop(h)
        reader.update()
        assertTrue(reader.isActionDown("jump"), "the held press carries over")
        assertFalse(reader.wasActionPressed("jump"), "but is not a new press")
        raw.keys.clear(); reader.update()
        raw.keys.add(Keys.SPACE); reader.update()
        assertTrue(reader.wasActionPressed("jump"))
    }

    @Test
    fun InputReader_DefaultActionsKeyboardAndTrigger() {
        val raw = FakeRaw()
        val map = DefaultActions.register(ActionMap())
        val reader = ActionInputReader(raw, map)
        raw.keys.add(Keys.A)
        assertEquals(-1f, reader.actionValue(DefaultActions.MOVE_X))
        raw.keys.clear(); raw.axes[1] = -1f // stick up
        assertEquals(1f, reader.actionValue(DefaultActions.MOVE_Y), 0.01f)
        raw.axes[1] = 0f; raw.axes[4] = 0.9f
        assertTrue(reader.isActionDown(DefaultActions.SPRINT))
    }

    @Test
    fun InputReader_RebindTakesEffectImmediately() {
        val raw = FakeRaw()
        val map = DefaultActions.register(ActionMap())
        val reader = ActionInputReader(raw, map)
        map.rebind(DefaultActions.JUMP, 0, ActionBinding.Key(Keys.Q))
        raw.keys.add(Keys.SPACE)
        assertFalse(reader.isActionDown(DefaultActions.JUMP))
        raw.keys.clear(); raw.keys.add(Keys.Q)
        assertTrue(reader.isActionDown(DefaultActions.JUMP))
    }

    @Test
    fun InputReader_ApiFileHasNoEditorOrBackendImports() {
        val src = Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/kotlin/com/pafoid/skate/engine/input").let { dir ->
                listOf("InputReader.kt", "ActionMap.kt").map { dir.resolve(it).readText() }
            }
        listOf("org.lwjgl", "imgui", "com.pafoid.skate.editor").forEach { banned ->
            src.forEach { assertFalse(it.contains("import $banned"), "Action API must not import $banned") }
        }
    }
}
