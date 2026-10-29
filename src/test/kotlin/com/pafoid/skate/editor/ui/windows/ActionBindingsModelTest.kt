package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.engine.input.ActionBinding
import com.pafoid.skate.engine.input.ActionMap
import com.pafoid.skate.engine.input.DefaultActions
import com.pafoid.skate.engine.input.InputContext
import com.pafoid.skate.engine.input.RawInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ActionBindingsModelTest {
    private val map = DefaultActions.register(ActionMap())

    private fun groups(query: String) = ActionBindingsModel.groups(map, query, { it.id }, { it.category })

    @Test
    fun ActionBindingsModel_GroupsByContextThenCategory() {
        val g = groups("")
        assertEquals(InputContext.GAMEPLAY, g.first().context)
        assertTrue(g.any { it.context == InputContext.UI && it.category == "Navigation" })
        assertEquals(map.definitions().size, g.sumOf { it.rows.size })
    }

    @Test
    fun ActionBindingsModel_SearchMatchesNameCategoryAndBinding() {
        assertEquals(listOf("jump"), groups("jump").flatMap { it.rows }.map { it.name })
        assertTrue(groups("navigation").flatMap { it.rows }.all { it.definition.category == "Navigation" })
        assertTrue(groups("space").flatMap { it.rows }.any { it.name == "jump" })
        assertTrue(groups("zzz").isEmpty())
    }

    @Test
    fun ActionBindingsModel_LabelsDevices() {
        assertEquals("Mouse Left", ActionBindingsModel.label(ActionBinding.MouseButton(0)))
        assertEquals("A", ActionBindingsModel.label(ActionBinding.GamepadButton(0)))
        assertEquals("Left Y (-)", ActionBindingsModel.label(ActionBinding.GamepadAxis(1, -1f)))
    }

    private class FakeRaw : RawInput {
        var pressedKey = -1
        var pressedButton = -1
        val axes = FloatArray(6)
        override fun isKeyDown(key: Int) = key == pressedKey
        override fun wasKeyPressed(key: Int) = key == pressedKey
        override fun isMouseButtonDown(button: Int) = false
        override fun wasMouseButtonPressed(button: Int) = false
        override fun mouseX() = 0f
        override fun mouseY() = 0f
        override fun mouseDeltaX() = 0f
        override fun mouseDeltaY() = 0f
        override fun scrollY() = 0f
        override fun isGamepadConnected(pad: Int) = true
        override fun gamepadAxis(axis: Int, pad: Int) = axes[axis]
        override fun isGamepadButtonDown(button: Int, pad: Int) = button == pressedButton
        override fun wasGamepadButtonPressed(button: Int, pad: Int) = button == pressedButton
    }

    @Test
    fun BindingCapture_KeyboardCapturesPressedKey() {
        val raw = FakeRaw()
        val capture = BindingCapture(raw, BindingCapture.Kind.KEYBOARD)
        assertNull(capture.poll())
        raw.pressedKey = 81
        assertEquals(ActionBinding.Key(81), capture.poll())
    }

    @Test
    fun BindingCapture_GamepadCapturesButtonAndAxisRelativeToIdle() {
        val raw = FakeRaw()
        raw.axes[4] = -1f // trigger idles at -1
        val capture = BindingCapture(raw, BindingCapture.Kind.GAMEPAD)
        assertNull(capture.poll())
        raw.axes[1] = -0.9f
        assertEquals(ActionBinding.GamepadAxis(1, -1f), capture.poll())
        raw.axes[1] = 0f
        raw.pressedButton = 3
        assertEquals(ActionBinding.GamepadButton(3), capture.poll())
    }
}
