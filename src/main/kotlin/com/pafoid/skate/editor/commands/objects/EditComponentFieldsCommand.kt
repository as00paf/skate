package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.components.Component
import java.lang.reflect.Field

/** One finished inspector gesture on [component] (EDT-10): each field goes from its before to its after value. */
class EditComponentFieldsCommand(
    private val component: Component,
    private val changes: Map<Field, Change>,
) : Command {
    data class Change(val before: Any?, val after: Any?)

    val isNoOp: Boolean get() = changes.values.all { it.before == it.after }

    override fun execute() = apply { it.after }

    override fun undo() = apply { it.before }

    private fun apply(pick: (Change) -> Any?) {
        changes.forEach { (field, change) -> ComponentValues.write(component, field, pick(change)) }
        ComponentValues.sceneOf(component)?.isDirty = true
    }

    override fun getDisplayName(): String = "Edit ${component.javaClass.simpleName} ${changes.keys.joinToString { it.name }}"
    override fun getTargetName(): String? = component.gameObject?.name ?: component.name
}

/** Reset to default (EDT-10): runs [Component.reset]; undo puts the previous values back. */
class ResetComponentCommand(private val component: Component) : Command {
    private val before = ComponentValues.snapshot(component)
    private var after: Map<Field, Any?> = emptyMap()

    override fun execute() {
        if (after.isEmpty()) {
            component.reset()
            after = ComponentValues.snapshot(component)
        } else {
            after.forEach { (f, v) -> ComponentValues.write(component, f, v) }
        }
        ComponentValues.sceneOf(component)?.isDirty = true
    }

    override fun undo() {
        before.forEach { (f, v) -> ComponentValues.write(component, f, v) }
        ComponentValues.sceneOf(component)?.isDirty = true
    }

    override fun getDisplayName(): String = "Reset ${component.javaClass.simpleName}"
    override fun getTargetName(): String? = component.gameObject?.name ?: component.name
}
