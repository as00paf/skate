package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent

/**
 * Sets the transforms of one or more objects (a gizmo drag on a multi-selection is one undo step).
 * Old and new states are deep copies taken at construction: `Transform` is a data class whose `copy()` shares
 * the live vectors, so a shallow copy would follow later edits and leave redo nothing to reapply.
 */
class TransformCommand(entries: List<Entry>) : Command {

    /** One object's transform before and after the edit. */
    class Entry(val gameObject: GameObject, oldTransform: Transform, newTransform: Transform) {
        val old: Transform = snapshot(oldTransform)
        val new: Transform = snapshot(newTransform)
    }

    constructor(gameObject: GameObject, oldTransform: Transform, newTransform: Transform) :
        this(listOf(Entry(gameObject, oldTransform, newTransform)))

    private val entries: List<Entry> = entries.toList()

    override fun execute() {
        entries.forEach { it.gameObject.getComponent<Transform>()?.copyFrom(it.new) }
    }

    override fun undo() {
        entries.asReversed().forEach { it.gameObject.getComponent<Transform>()?.copyFrom(it.old) }
    }

    override fun getDisplayName(): String = "Transform"
    override fun getTargetName(): String? =
        entries.singleOrNull()?.gameObject?.name ?: "${entries.size} objects"

    companion object {
        /** A transform that shares no vectors with [source]. */
        fun snapshot(source: Transform): Transform = Transform().apply { copyFrom(source) }
    }
}
