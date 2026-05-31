package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class EditorEvent(eventName: String) : Event(eventName) {
    // Windows
    object OpenSearch : EditorEvent("editor.open_search")

    // Editor
    object Exit : EditorEvent("editor.exit")
    object Minimize : EditorEvent("editor.minimize")
    object ToggleMaximize : EditorEvent("editor.toggle_maximize")
}