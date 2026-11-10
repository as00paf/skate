package com.pafoid.skate.engine.events

sealed class InputEvent(eventName: String) : Event(eventName)

/** Edge of an action, published only for actions defined with `publishEvents = true`. */
enum class ActionPhase { STARTED, CANCELED }

/** [action] went down ([ActionPhase.STARTED], with its value) or up ([ActionPhase.CANCELED], value 0). */
data class ActionEvent(val action: String, val phase: ActionPhase, val value: Float) : InputEvent("input.action")

data class GamepadConnected(val jid: Int, val name: String?) : InputEvent("input.gamepad_connected")
data class GamepadDisconnected(val jid: Int) : InputEvent("input.gamepad_disconnected")
