package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.ComponentValues
import com.pafoid.skate.editor.commands.objects.EditComponentFieldsCommand.Change
import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import org.joml.Vector3f
import java.lang.reflect.Field
import java.util.IdentityHashMap

/**
 * EDT-74: editing a multi-selection in Properties. The window shows the primary object's components whose type every
 * selected object has ([shared]) and registers the matching components of the other objects as its mirrors
 * ([inspect]). A finished inspector edit on a primary component is then copied to its mirrors, field by field (and
 * axis by axis for a Transform), so each object keeps the values the user did not touch. One undo step covers all.
 *
 * Only edits that go through the edit trackers are mirrored; inspectors that run their own command (text fields,
 * materials, drafts) still edit the primary object only.
 */
object MultiEdit {
    private val mirrors = IdentityHashMap<Component, List<Component>>()

    /** The primary's components (last of [objects]) whose type every other object also has, sorted by type name. */
    fun shared(objects: List<GameObject>): List<Component> {
        val primary = objects.lastOrNull() ?: return emptyList()
        val others = objects.dropLast(1)
        return primary.components.filter { c -> others.all { o -> o.components.any { it.javaClass == c.javaClass } } }
            .distinctBy { it.javaClass }
            .sortedBy { it.javaClass.simpleName }
    }

    /** Registers this frame's mirrors for [objects] (the last is primary); fewer than two objects clears them. */
    fun inspect(objects: List<GameObject>) {
        mirrors.clear()
        if (objects.size < 2) return
        val others = objects.dropLast(1)
        shared(objects).forEach { c -> mirrors[c] = others.mapNotNull { o -> o.components.firstOrNull { it.javaClass == c.javaClass } } }
    }

    fun clear() = mirrors.clear()

    fun mirrorsOf(component: Component): List<Component> = mirrors[component].orEmpty()

    /** The field edits that copy [changes] (made on [component]) to its mirrors; each mirror keeps its own "before". */
    fun mirrorFields(component: Component, changes: Map<Field, Change>): List<Pair<Component, Map<Field, Change>>> =
        mirrorsOf(component).map { other ->
            other to changes.mapValues { (field, change) -> Change(ComponentValues.copy(field.get(other)), change.after) }
        }.filter { (_, c) -> c.values.any { it.before != it.after } }

    /** The Transform edits that copy what [entry] changed (per vector axis) to the mirrors of its Transform. */
    fun mirrorTransform(entry: TransformCommand.Entry): List<TransformCommand.Entry> {
        val primary = entry.gameObject.components.firstOrNull { it is Transform } ?: return emptyList()
        return mirrorsOf(primary).filterIsInstance<Transform>().mapNotNull { other ->
            val go = other.gameObject ?: return@mapNotNull null
            val updated = TransformCommand.snapshot(other)
            copyChangedAxes(entry.old.translation, entry.new.translation, updated.translation)
            copyChangedAxes(entry.old.rotation, entry.new.rotation, updated.rotation)
            copyChangedAxes(entry.old.scale, entry.new.scale, updated.scale)
            if (updated == other) null else TransformCommand.Entry(go, other, updated)
        }
    }

    private fun copyChangedAxes(old: Vector3f, new: Vector3f, target: Vector3f) {
        if (old.x != new.x) target.x = new.x
        if (old.y != new.y) target.y = new.y
        if (old.z != new.z) target.z = new.z
    }
}
