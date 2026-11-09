package com.pafoid.skate.engine.input

/**
 * [RawInput] backed by the GLFW listeners in [InputProvider], following the pointer contract in
 * docs/design/INP-09.md: positions in render-target pixels, mouse buttons owned by the game view, deltas in cursor units.
 */
class ProviderRawInput(private val provider: InputProvider) : RawInput {
    private fun jid(pad: Int) = provider.joystickForPad(pad)

    override fun isKeyDown(key: Int) = provider.isKeyPressed(key)
    override fun wasKeyPressed(key: Int) = provider.keyBeginPress(key)
    override fun wasMouseButtonPressed(button: Int) = provider.mouseListener.viewButtonBeginPress(button)
    override fun isMouseButtonDown(button: Int) = provider.mouseListener.isViewButtonDown(button)
    override fun mouseX() = provider.mouseListener.viewTarget().first
    override fun mouseY() = provider.mouseListener.viewTarget().second
    override fun mouseDeltaX() = provider.getMouseDx()
    override fun mouseDeltaY() = provider.getMouseDy()
    override fun scrollY() = provider.getMouseScrollY()
    override fun isGamepadConnected(pad: Int) = provider.isJoystickPresent(jid(pad))
    override fun gamepadAxis(axis: Int, pad: Int) = provider.getAxes(jid(pad))?.getOrNull(axis) ?: 0f
    override fun isGamepadButtonDown(button: Int, pad: Int) = provider.getButtons(jid(pad))?.getOrNull(button) ?: false
    override fun wasGamepadButtonPressed(button: Int, pad: Int) = provider.buttonBeginPress(jid(pad), button)
    override fun isCursorCaptured() = provider.isCursorDisabled()
}
