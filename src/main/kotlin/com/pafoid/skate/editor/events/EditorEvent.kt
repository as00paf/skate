package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class EditorEvent(eventName: String) : Event(eventName) {
    // Windows
    object OpenSearch : EditorEvent("editor.open_search")
    object Exit : EditorEvent("editor.open_search")
}