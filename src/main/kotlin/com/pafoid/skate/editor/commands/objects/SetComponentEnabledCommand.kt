package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.Component

class SetComponentEnabledCommand(
    private val component: Component,
    private val enabled: Boolean,
) : Command {
    private val previousValue = component.enabled

    override fun execute() {
        component.enabled = enabled
    }

    override fun undo() {
        component.enabled = previousValue
    }

    override fun getDisplayName(): String = "Set Enabled"

    override fun getTargetName(): String = component.name
}
