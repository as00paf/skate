package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.Component
import java.lang.reflect.Field

/** Sets one reflected field of [component] (generic inspector, EDT-36); undo restores the previous value. */
class SetComponentFieldCommand(
    private val component: Component,
    private val field: Field,
    private val newValue: Any?,
) : Command {
    private val oldValue: Any? = read()

    val isNoOp: Boolean get() = oldValue == newValue

    private fun read(): Any? = field.also { it.isAccessible = true }.get(component)
    private fun write(value: Any?) = field.also { it.isAccessible = true }.set(component, value)

    override fun execute() = write(newValue)
    override fun undo() = write(oldValue)
    override fun getDisplayName(): String = "Edit ${component.javaClass.simpleName}.${field.name}"
    override fun getTargetName(): String? = component.gameObject?.name ?: component.name
}
