package com.pafoid.skate.editor.commands.`object`

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject

class RenameGameObjectCommand(
    private val gameObject: GameObject,
    private val newName: String,
    private val oldName: String
) : Command {
    override fun execute() {
        gameObject.name = newName
    }

    override fun undo() {
        gameObject.name = oldName
    }

    override fun getDisplayName(): String = "Rename ${gameObject.name}"
    override fun getTargetName(): String? = gameObject.name
}
