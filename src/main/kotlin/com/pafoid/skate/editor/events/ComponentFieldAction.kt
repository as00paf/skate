package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.EditComponentFieldsCommand
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.events.Event
import java.lang.reflect.Field

/** Edits from the generic (reflected) component inspector; handled with undoable commands. */
sealed class ComponentFieldAction(eventName: String) : Event(eventName) {
    /** A finished inspector gesture on [component] (EDT-10). */
    data class Edited(val component: Component, val changes: Map<Field, EditComponentFieldsCommand.Change>) : ComponentFieldAction("component.fields.edited")

    /** Reset to default from the inspector's context menu. */
    data class Reset(val component: Component) : ComponentFieldAction("component.reset")

    data class Set(val component: Component, val field: Field, val value: Any?) : ComponentFieldAction("component.field.set")
}
