package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.editor.PropertiesWindow
import com.pafoid.skate.engine.scenes.GameObject

open class Gizmo(protected val propertiesWindow: PropertiesWindow) : Component() {

    protected var xAxisActive = false
    protected var yAxisActive = false
    protected var zAxisActive = false

    protected var activeGameObject: GameObject? = null
    private var inUse = false

    override fun update(dt: Float) {
        if (inUse) setInactive()
    }

    override fun editorUpdate(dt: Float) {
        if (!inUse) return
        activeGameObject = propertiesWindow.getActiveObject()
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
