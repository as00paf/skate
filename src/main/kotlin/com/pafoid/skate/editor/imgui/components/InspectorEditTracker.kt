package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.commands.objects.ComponentValues
import com.pafoid.skate.editor.commands.objects.EditComponentFieldsCommand.Change
import com.pafoid.skate.editor.systems.CommandClock
import com.pafoid.skate.engine.ecs.components.Animator
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.Transform
import java.lang.reflect.Field
import java.util.IdentityHashMap

/**
 * Undo for every inspector (EDT-10), free of ImGui so it can be tested.
 *
 * Many inspectors write widget values straight into the component so the viewport updates while a slider moves.
 * [track] snapshots the component's saved value fields around its inspector call and remembers, per field, the
 * value from before the gesture. [settle] turns each finished gesture (no widget active any more) into one edit,
 * which is published and run as an undoable command.
 *
 * - A command run during the call (string fields, drafts, materials, controllers) is already undoable. The call's
 *   changes are then taken as the new baseline, so nothing is recorded twice.
 * - [Transform] has its own gesture tracker (TransformEditTracker). Animator playback (time, play state) is preview
 *   state. Neither is tracked.
 */
class InspectorEditTracker(private val clock: () -> Long = { CommandClock.value }) {

    data class Edit(val component: Component, val changes: Map<Field, Change>)

    private val pending = IdentityHashMap<Component, MutableMap<Field, Change>>()

    fun <T> track(component: Component, inspector: () -> T): T {
        if (component is Transform) return inspector()
        val ignored = IGNORED[component.javaClass].orEmpty()
        val before = ComponentValues.snapshot(component, ignored)
        val tick = clock()
        val result = inspector()
        if (clock() != tick) return result
        val after = ComponentValues.snapshot(component, ignored)
        for ((field, old) in before) {
            val new = after[field]
            if (old == new) continue
            val changes = pending.getOrPut(component) { LinkedHashMap() }
            val first = changes[field]?.before ?: old
            changes[field] = Change(first, new)
        }
        return result
    }

    /** Ends the gestures once no widget is active; returns the edits that still change something. */
    fun settle(anyItemActive: Boolean): List<Edit> {
        if (anyItemActive || pending.isEmpty()) return emptyList()
        val edits = pending.mapNotNull { (component, changes) ->
            val real = changes.filterValues { it.before != it.after }
            if (real.isEmpty()) null else Edit(component, real)
        }
        pending.clear()
        return edits
    }

    companion object {
        /** Fields that are saved but are preview state, not edits. */
        val IGNORED: Map<Class<*>, Set<String>> = mapOf(
            Animator::class.java to setOf("currentTime", "previousTime", "isPlaying", "blendTime"),
        )

        /** The tracker the editor's inspectors use; settled by [InspectorDrafts.endFrame]. */
        val shared = InspectorEditTracker()
    }
}
