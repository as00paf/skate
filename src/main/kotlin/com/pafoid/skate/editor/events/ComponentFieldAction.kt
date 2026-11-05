package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.events.Event
import java.lang.reflect.Field

/** Edits from the generic (reflected) component inspector; handled with undoable commands. */
sealed class ComponentFieldAction(eventName: String) : Event(eventName) {
    data class Set(val component: Component, val field: Field, val value: Any?) : ComponentFieldAction("component.field.set")
}
