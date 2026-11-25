package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.assets.data.models.`3dModel`
import com.pafoid.skate.engine.ecs.components.RenderComponent

/**
 * Changes the model of an existing [RenderComponent] (REN-47). The material override and its `.skmat` link are
 * untouched: the override applies to every mesh part, so it carries over to the new model whatever its slot names.
 */
class SetRenderModelCommand(
    private val component: RenderComponent,
    private val model: `3dModel`?,
) : Command {
    private val before = component.model

    val isNoOp: Boolean get() = before === model || (before != null && model != null && before.path == model.path)

    override fun execute() {
        component.model = model
    }

    override fun undo() {
        component.model = before
    }

    override fun getDisplayName(): String = "Change Model"
    override fun getTargetName(): String? = component.gameObject?.name
}
