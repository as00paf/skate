package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform

/**
 * Groups a Transform inspector edit (a drag, typed value or reset button, possibly over many frames) into one
 * undo entry. Before drawing, the inspector calls [beforeDraw]. After drawing, it calls [afterDraw] with whether
 * the user is still interacting (a widget active or the mouse held). The tracker keeps the state from the first
 * changed frame and returns the finished edit once the interaction ends.
 */
class TransformEditTracker {
    private var target: GameObject? = null
    private var start: Transform? = null
    private val frameStart = Transform()

    /** Remembers [transform] as it is before this frame's widgets run. */
    fun beforeDraw(transform: Transform) {
        frameStart.copyFrom(transform)
    }

    /**
     * Returns the finished edits to record (usually none or one). An edit still pending on another object
     * (selection changed mid-edit) is finished first.
     */
    fun afterDraw(gameObject: GameObject, transform: Transform, interacting: Boolean): List<TransformCommand.Entry> {
        val finished = mutableListOf<TransformCommand.Entry>()
        val pendingTarget = target
        if (pendingTarget != null && pendingTarget !== gameObject) flush()?.let(finished::add)

        if (start == null && frameStart != transform) {
            target = gameObject
            start = TransformCommand.snapshot(frameStart)
        }
        if (start != null && !interacting) flush()?.let(finished::add)
        return finished
    }

    private fun flush(): TransformCommand.Entry? {
        val go = target
        val old = start
        target = null
        start = null
        val current = go?.components?.filterIsInstance<Transform>()?.firstOrNull() ?: return null
        if (old == null || old == current) return null
        return TransformCommand.Entry(go, old, current)
    }
}
