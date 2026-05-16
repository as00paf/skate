package com.pafoid.skate.engine.controls

class MockGamepad(val jid: Int) {
    private val axes = FloatArray(6) { 0f }
    private val buttons = BooleanArray(15) { false }

    fun setAxis(axis: Int, value: Float) {
        if (axis in axes.indices) {
            axes[axis] = value
        }
    }

    fun setButton(button: Int, pressed: Boolean) {
        if (button in buttons.indices) {
            buttons[button] = pressed
        }
    }

    fun apply() {
        // Since JoystickListener uses static state, we'll need to mock it in tests
        // But this class provides a convenient way to hold the mock data
    }

    fun getAxes(): FloatArray = axes
    fun getButtons(): BooleanArray = buttons
}
