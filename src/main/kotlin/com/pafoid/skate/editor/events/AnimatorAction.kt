package com.pafoid.skate.editor.events

import com.pafoid.skate.engine.animation.graph.AnimatorMaskDef
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.events.Event

/** Animator inspector edits (EDT-16), turned into undoable commands by AnimatorActionHandler. */
sealed class AnimatorAction(eventName: String) : Event(eventName) {
    /** [path] null removes the controller. */
    data class SetController(val animator: Animator, val path: String?) : AnimatorAction("animator.set_controller")
    data class SetMask(val animator: Animator, val mask: AnimatorMaskDef) : AnimatorAction("animator.set_mask")
}
