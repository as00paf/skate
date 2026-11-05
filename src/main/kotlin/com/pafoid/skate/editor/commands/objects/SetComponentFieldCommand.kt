package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
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

    /** Scene that owns the component (top of its parent chain), marked dirty on execute, undo and redo. */
    private fun scene(): Scene? = generateSequence<GameObject>(component.gameObject) { it.parent }.filterIsInstance<Scene>().firstOrNull()

    override fun execute() {
        write(newValue)
        scene()?.isDirty = true
    }

    override fun undo() {
        write(oldValue)
        scene()?.isDirty = true
    }
    override fun getDisplayName(): String = "Edit ${component.javaClass.simpleName}.${field.name}"
    override fun getTargetName(): String? = component.gameObject?.name ?: component.name
}
