package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.input.listeners.MouseListener
import org.koin.core.component.KoinComponent

open class Gizmo(
    protected val mouseListener: MouseListener,
    protected val undoRedoManager: UndoRedoManager,
) : System(), KoinComponent {

    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    protected var activeGameObject: GameObject? = null
    protected var oldTransform: Transform? = null
    private var inUse = false

    fun setActive() {}
    fun setInactive() { activeGameObject = null }

    fun isInUse(): Boolean = inUse
    fun setNotInUse() {
        inUse = false
        setInactive()
    }

    fun setInUse() {
        inUse = true
    }

    open fun isHot(): Boolean = false
    open fun anyAxisActive(): Boolean = xAxisActive || yAxisActive || zAxisActive
}
