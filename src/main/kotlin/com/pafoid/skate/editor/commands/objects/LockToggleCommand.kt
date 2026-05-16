package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject

class LockToggleCommand(
    private val gameObject: GameObject,
    private val newLockState: Boolean
) : Command {
    private val oldLockState = gameObject.isLocked

    override fun execute() {
        gameObject.isLocked = newLockState
    }

    override fun undo() {
        gameObject.isLocked = oldLockState
    }

    override fun getDisplayName(): String = "Toggle Lock: ${gameObject.name}"
    override fun getTargetName(): String? = gameObject.name
}