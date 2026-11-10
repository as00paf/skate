package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.events.Event

/** Render Graph window requests (EDT-54), handled by `RenderGraphActionHandler`. */
sealed class RenderGraphAction(eventName: String) : Event(eventName) {
    /** Switches pass [passId], or its stage [stageId] (a post effect), on or off. */
    data class SetPassEnabled(val passId: String, val enabled: Boolean, val stageId: String? = null) :
        RenderGraphAction("render_graph.set_pass_enabled")

    /** Switches every pass that can be disabled back on. */
    object EnableAll : RenderGraphAction("render_graph.enable_all")
}
