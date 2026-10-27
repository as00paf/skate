package com.pafoid.skate.engine.input

data class GamepadConnectionEvent(val jid: Int, val connected: Boolean, val name: String?)

/**
 * Tracks which joystick slots are connected and notifies on every real change, no matter whether it
 * came from a GLFW callback or from polling. Pad indices (0 = primary) are mapped to the connected
 * joystick ids in ascending order, so a pad that is replugged into another slot keeps working as pad 0.
 */
class GamepadRegistry(val maxPads: Int = 16) {
    private val present = BooleanArray(maxPads)

    private val listeners = mutableListOf<(GamepadConnectionEvent) -> Unit>()

    fun addListener(listener: (GamepadConnectionEvent) -> Unit) {
        listeners.add(listener)
    }

    fun isPresent(jid: Int): Boolean = jid in present.indices && present[jid]

    /** Returns true if the state changed. [name] is only evaluated on connect. */
    fun setPresent(jid: Int, connected: Boolean, name: () -> String? = { null }): Boolean {
        if (jid !in present.indices || present[jid] == connected) return false
        present[jid] = connected
        val event = GamepadConnectionEvent(jid, connected, if (connected) name() else null)
        listeners.toList().forEach { it(event) }
        return true
    }

    fun connected(): List<Int> = present.indices.filter { present[it] }

    /** Joystick id for logical pad [pad], or -1 if there is no such pad. */
    fun joystickForPad(pad: Int): Int = connected().getOrNull(pad) ?: -1
}
