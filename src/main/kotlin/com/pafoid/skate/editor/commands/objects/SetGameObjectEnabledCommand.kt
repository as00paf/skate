package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject

class SetGameObjectEnabledCommand(
    private val gameObject: GameObject,
    private val enabled: Boolean,
) : Command {
    private val previousValue = gameObject.isEnabled

    override fun execute() {
        gameObject.isEnabled = enabled
    }

    override fun undo() {
        gameObject.isEnabled = previousValue
    }

    override fun getDisplayName(): String = "Set Enabled"

    override fun getTargetName(): String = gameObject.name
}
