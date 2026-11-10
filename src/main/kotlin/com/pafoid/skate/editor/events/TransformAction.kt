package com.pafoid.skate.editor.events

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.engine.events.Event

sealed class TransformAction(eventName: String) : Event(eventName) {
    /**
     * A finished inspector edit (already applied to the object), to record for undo/redo. [mirrors] copy it to the
     * other objects of a multi-selection (EDT-74); they are applied by the same command.
     */
    data class Edited(val entry: TransformCommand.Entry, val mirrors: List<TransformCommand.Entry> = emptyList()) : TransformAction("transform.edited")
}
