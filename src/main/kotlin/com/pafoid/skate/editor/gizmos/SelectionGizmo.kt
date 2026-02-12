package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.scene.getGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.Renderer

class SelectionGizmo(
    mouseListener: MouseListener,
    undoRedoManager: UndoRedoManager,
    private val renderer: Renderer,
    private val engine: Engine,
) : Gizmo(mouseListener, undoRedoManager) {

    fun getHoveredObject(x: Int, y: Int): GameObject? {
        if (engine.engineState.get() != EngineState.RUNNING) return null
        val id = renderer.readPixel(x, y)
        return scene.getGameObject(id)
    }

    var hoveredGameObjectUid: Int = -1
        private set

    var hoveredGameObject: GameObject? = null
        private set

    override fun editorUpdate(dt: Float) {
        if (!isInUse() || engine.runtimePlaying) {
            hoveredGameObjectUid = -1
            hoveredGameObject = null
            return
        }

        if (mouseListener.isInsideViewport()) {
            val pickingX = mouseListener.getScreenX().toInt()
            val pickingY = mouseListener.getScreenY().toInt()

            val hovered = getHoveredObject(pickingX, pickingY)
            hoveredGameObject = hovered
            hoveredGameObjectUid = hovered?.getUid() ?: -1

            if (mouseListener.mouseButtonBeginPress(0)) {
                scene.setSelectedGameObject(hovered)
            }
        } else {
            hoveredGameObjectUid = -1
        }
    }
}
