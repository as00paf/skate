package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

/** Requests to the editor's Play state machine ([com.pafoid.skate.editor.systems.PlayModeController], EDT-45). */
sealed class PlayModeAction(eventName: String) : Event(eventName) {
    object Play : PlayModeAction("play_mode.play")
    object Pause : PlayModeAction("play_mode.pause")
    object TogglePause : PlayModeAction("play_mode.toggle_pause")
    object Stop : PlayModeAction("play_mode.stop")
    object TogglePlay : PlayModeAction("play_mode.toggle_play")
    object Reset : PlayModeAction("play_mode.reset")

    /**
     * Stops Play (restoring every scene, EDT-45), then runs [then]: a scene change the user asked for during Play
     * (EDT-63). Like the other actions it runs at the start of the next frame, outside ImGui drawing.
     */
    class StopThen(val then: () -> Unit) : PlayModeAction("play_mode.stop_then")
}
