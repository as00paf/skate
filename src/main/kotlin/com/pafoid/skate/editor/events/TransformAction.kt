package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.engine.events.Event

sealed class TransformAction(eventName: String) : Event(eventName) {
    /** A finished inspector edit (already applied to the object), to record for undo/redo. */
    data class Edited(val entry: TransformCommand.Entry) : TransformAction("transform.edited")
}
