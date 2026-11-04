package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.commands.objects.TransformCommand
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.utils.Ray
import org.joml.Vector3f

open class Gizmo(
    engine: Engine,
    protected val undoRedoManager: UndoRedoManager
) {
    protected val inputProvider: InputProvider = engine.inputProvider
    protected val sceneManager: SceneManager = engine.sceneManager

    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    var inUse = false

    protected fun rayToLineDist(ray: Ray, origin: Vector3f, direction: Vector3f, length: Float): Float {
        var minDist = Float.MAX_VALUE
        for (i in 0..10) {
            val p = Vector3f(origin).add(Vector3f(direction).mul(length * (i / 10f)))
            val dist = ray.distanceToPoint(p)
            if (dist < minDist) minDist = dist
        }
        return minDist
    }

    /** Transform of every target at drag start, used for relative edits and one undo command per object. */
    private var snapshots: List<Pair<GameObject, Transform>> = emptyList()

    protected fun beginSnapshot(targets: List<GameObject>) {
        snapshots = targets.mapNotNull { go -> go.getComponent<Transform>()?.let { go to TransformCommand.snapshot(it) } }
    }

    protected val snapshotTargets: List<Pair<GameObject, Transform>> get() = snapshots

    /** Records the drag as one undoable command covering every target that changed. */
    protected fun commitSnapshot() {
        buildCommand(snapshots)?.let {
            undoRedoManager.pushCommand(it)
            sceneManager.currentScene?.isDirty = true
        }
        snapshots = emptyList()
    }

    companion object {
        /** Command for the targets whose transform differs from its drag-start [snapshots], or null if none did. */
        fun buildCommand(snapshots: List<Pair<GameObject, Transform>>): TransformCommand? {
            val entries = snapshots.mapNotNull { (go, old) ->
                val current = go.getComponent<Transform>() ?: return@mapNotNull null
                if (old == current) null else TransformCommand.Entry(go, old, current)
            }
            return if (entries.isEmpty()) null else TransformCommand(entries)
        }
    }
}
