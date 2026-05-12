package com.pafoid.skate.editor.commands.`object`

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject

class ReparentGameObjectCommand(
    private val gameObject: GameObject,
    private val newParent: GameObject?
) : Command {
    private val oldParent = gameObject.parent

    override fun execute() {
        oldParent?.children?.remove(gameObject)
        gameObject.parent = newParent
        newParent?.children?.add(gameObject)
    }

    override fun undo() {
        newParent?.children?.remove(gameObject)
        gameObject.parent = oldParent
        oldParent?.children?.add(gameObject)
    }

    override fun getDisplayName(): String = "Reparent ${gameObject.name}"
    override fun getTargetName(): String? = gameObject.name
}
