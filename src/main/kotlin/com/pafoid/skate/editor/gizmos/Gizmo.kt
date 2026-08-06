package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.utils.Ray
import org.joml.Vector3f

open class Gizmo(
    protected val inputProvider: InputProvider,
    protected val undoRedoManager: UndoRedoManager,
) {
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

}
