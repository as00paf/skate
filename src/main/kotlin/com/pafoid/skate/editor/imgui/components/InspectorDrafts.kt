package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.engine.core.EventSystem

/**
 * End-of-frame commit of inspector edits (EDT-40). Inspectors commit a text or drag edit when the widget is released,
 * which they only notice while drawn; selecting another object mid-edit, collapsing a header or closing the window
 * used to drop the edit. [WindowManager][com.pafoid.skate.editor.imgui.WindowManager] calls [endFrame] once per frame
 * after every window drew, so each pending edit still becomes exactly one command.
 */
object InspectorDrafts {
    fun endFrame(events: EventSystem, frame: Int, anyItemActive: Boolean) {
        StringFields.endFrame(events, frame)
        flushAudioDrafts(events, frame)
        settleMaterialEdits(events, anyItemActive)
        InspectorEditTracker.shared.settle(anyItemActive).forEach { events.publish(ComponentFieldAction.Edited(it.component, it.changes)) }
    }
}
