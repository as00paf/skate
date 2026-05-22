package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent

class TransformCommand(
    private val gameObject: GameObject,
    oldTransform: Transform,
    newTransform: Transform
) : Command {
    private val oldT = Transform().apply { copyFrom(oldTransform) }
    private val newT = Transform().apply { copyFrom(newTransform) }

    override fun execute() {
        gameObject.getComponent<Transform>()?.copyFrom(newT)
    }

    override fun undo() {
        gameObject.getComponent<Transform>()?.copyFrom(oldT)
    }

    override fun getDisplayName(): String = "Transform"
    override fun getTargetName(): String? = gameObject.name
}