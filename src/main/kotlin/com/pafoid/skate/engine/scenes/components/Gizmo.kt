package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.getSelectedGameObject
import org.koin.core.component.KoinComponent

open class Gizmo(
    protected val sceneManager: SceneManager,
    protected val mouseListener: MouseListener,
    protected val undoRedoManager: UndoRedoManager,
) : Component(), KoinComponent {

    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    protected var activeGameObject: GameObject? = null
    protected var oldTransform: Transform? = null
    private var inUse = false

    override fun update(dt: Float) {
        if (inUse) setInactive()
    }

    override fun editorUpdate(dt: Float) {
        if (!inUse) return
        activeGameObject = sceneManager.currentScene?.getSelectedGameObject()
    }

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
