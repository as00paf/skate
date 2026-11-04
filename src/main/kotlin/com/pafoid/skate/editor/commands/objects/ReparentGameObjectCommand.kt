package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene

/**
 * Moves [gameObject] under [newParent]; pass the [Scene] to make it top-level. Undo restores the previous
 * parent and sibling position. Keeps the local transform.
 */
class ReparentGameObjectCommand(
    private val gameObject: GameObject,
    private val newParent: GameObject,
    scene: Scene,
) : Command {
    private val oldParent: GameObject = gameObject.parent ?: scene
    private val oldIndex: Int = oldParent.children.indexOf(gameObject)

    override fun execute() {
        newParent.addChild(gameObject)
    }

    override fun undo() {
        oldParent.addChild(gameObject, oldIndex)
    }

    override fun getDisplayName(): String = "Reparent ${gameObject.name}"
    override fun getTargetName(): String? = gameObject.name

    companion object {
        /**
         * False when the move would do nothing ([newParent] is already the parent) or create a cycle
         * ([newParent] is the object itself or one of its descendants).
         */
        fun isValid(gameObject: GameObject, newParent: GameObject): Boolean =
            newParent !== gameObject && newParent !== gameObject.parent && gameObject !in newParent.ancestors()
    }
}
