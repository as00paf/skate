package com.pafoid.skate.engine.input

/**
 * Per-joystick button edge tracking, free of GLFW so it can be unit tested.
 * Holds up to [MAX_BUTTONS] buttons, enough for real buttons plus GLFW hat-as-button entries.
 */
class ButtonStates(joysticks: Int, val maxButtons: Int = MAX_BUTTONS) {
    private val current = Array(joysticks) { BooleanArray(maxButtons) }
    private val last = Array(joysticks) { BooleanArray(maxButtons) }

    /** Advances one frame; buttons beyond [pressed]'s length read as released, extras beyond [maxButtons] are ignored. */
    fun update(jid: Int, pressed: (Int) -> Boolean, count: Int) {
        if (jid !in current.indices) return
        for (j in 0 until maxButtons) {
            last[jid][j] = current[jid][j]
            current[jid][j] = j < count && pressed(j)
        }
    }

    fun clear(jid: Int) {
        if (jid !in current.indices) return
        current[jid].fill(false)
        last[jid].fill(false)
    }

    fun buttons(jid: Int): BooleanArray? = current.getOrNull(jid)

    fun isDown(jid: Int, button: Int): Boolean = current.getOrNull(jid)?.getOrNull(button) ?: false

    fun beginPress(jid: Int, button: Int): Boolean = isDown(jid, button) && !(last[jid].getOrNull(button) ?: false)

    fun changed(jid: Int, button: Int): Boolean =
        current.getOrNull(jid)?.getOrNull(button)?.let { it != (last[jid].getOrNull(button) ?: false) } ?: false

    companion object {
        const val MAX_BUTTONS = 32
    }
}
