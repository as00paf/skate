package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

sealed class WindowAction(eventName: String) : Event(eventName) {
    data class Show(val name: String) : WindowAction("window.show")
    data class Hide(val name: String) : WindowAction("window.hide")
    object ShowDefault : WindowAction("window.showDefault")
}