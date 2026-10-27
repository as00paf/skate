package com.pafoid.skate.engine.input

import org.lwjgl.glfw.GLFW

/** [RawInput] backed by the GLFW listeners in [InputProvider]. */
class ProviderRawInput(private val provider: InputProvider) : RawInput {
    private fun jid(pad: Int) = GLFW.GLFW_JOYSTICK_1 + pad

    override fun isKeyDown(key: Int) = provider.isKeyPressed(key)
    override fun wasKeyPressed(key: Int) = provider.keyBeginPress(key)
    override fun wasMouseButtonPressed(button: Int) = provider.mouseButtonBeginPress(button)
    override fun isMouseButtonDown(button: Int) = provider.isMouseButtonDown(button)
    override fun mouseX() = provider.getMouseX()
    override fun mouseY() = provider.getMouseY()
    override fun mouseDeltaX() = provider.getMouseDx()
    override fun mouseDeltaY() = provider.getMouseDy()
    override fun scrollY() = provider.getMouseScrollY()
    override fun isGamepadConnected(pad: Int) = provider.isJoystickPresent(jid(pad))
    override fun gamepadAxis(axis: Int, pad: Int) = provider.getAxes(jid(pad))?.getOrNull(axis) ?: 0f
    override fun isGamepadButtonDown(button: Int, pad: Int) = provider.getButtons(jid(pad))?.getOrNull(button) ?: false
    override fun wasGamepadButtonPressed(button: Int, pad: Int) = provider.buttonBeginPress(jid(pad), button)
}
