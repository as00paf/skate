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

    @Test
    fun InputReader_ActionReadsKeyAndGamepadSources() {
        val raw = FakeRaw()
        val reader = ActionInputReader(raw)
        reader.actions.bind("jump", ActionBinding.Key(Keys.SPACE)).bind("jump", ActionBinding.GamepadButton(0))
        assertFalse(reader.isActionDown("jump"))
        raw.keys.add(Keys.SPACE)
        assertTrue(reader.isActionDown("jump"))
        raw.keys.clear(); raw.pads.add(0)
        assertEquals(1f, reader.actionValue("jump"))
    }

    @Test
    fun InputReader_AxisAppliesDeadZoneAndScale() {
        val raw = FakeRaw()
        val reader = ActionInputReader(raw)
        reader.actions.bind("moveY", ActionBinding.GamepadAxis(1, scale = -1f, deadZone = 0.2f))
        raw.axes[1] = 0.1f
        assertEquals(0f, reader.actionValue("moveY"))
        raw.axes[1] = -0.8f
        assertEquals(0.8f, reader.actionValue("moveY"), 1e-6f)
    }

    @Test
    fun InputReader_StrongestBindingWinsAndUnknownActionIsZero() {
        val raw = FakeRaw()
        val reader = ActionInputReader(raw)
        reader.actions.bind("moveX", ActionBinding.Key(Keys.D)).bind("moveX", ActionBinding.GamepadAxis(0))
        raw.keys.add(Keys.D); raw.axes[0] = -0.4f
        assertEquals(1f, reader.actionValue("moveX"))
        assertEquals(0f, reader.actionValue("nope"))
        assertFalse(reader.isActionDown("nope"))
    }

    @Test
    fun InputReader_WasActionPressedFiresOncePerPress() {
        val raw = FakeRaw()
        val reader = ActionInputReader(raw)
        reader.actions.bind("jump", ActionBinding.Key(Keys.SPACE))
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
    fun InputReader_ApplyMappingsBindsStandardActions() {
        val raw = FakeRaw()
        val reader = ActionInputReader(raw)
        reader.actions.applyMappings(InputMappings())
        raw.pads.add(0)
        assertTrue(reader.isActionDown("jump"))
        raw.axes[1] = -1f // stick up
        assertEquals(1f, reader.actionValue("moveY"))
        raw.axes[4] = 0.9f
        assertTrue(reader.isActionDown("sprint"))
    }

    @Test
    fun InputReader_ApiFileHasNoEditorOrBackendImports() {
        val src = Paths.get(System.getProperty("user.dir"))
            .resolve("src/main/kotlin/com/pafoid/skate/engine/input/InputReader.kt").readText()
        listOf("org.lwjgl", "imgui", "com.pafoid.skate.editor").forEach {
            assertFalse(src.contains("import $it"), "InputReader.kt must not import $it")
        }
    }
}
