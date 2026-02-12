package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.setSelectedGameObject

class SelectionGizmo(
    sceneManager: SceneManager,
    mouseListener: MouseListener,
    undoRedoManager: UndoRedoManager,
) : Gizmo(sceneManager, mouseListener, undoRedoManager) {

    var hoveredGameObjectUid: Int = -1
        private set

    var hoveredGameObject: GameObject? = null
        private set

    override fun editorUpdate(dt: Float) {
        if (!isInUse() || sceneManager.runtimePlaying) {
            hoveredGameObjectUid = -1
            hoveredGameObject = null
            return
        }

        if (mouseListener.isInsideViewport()) {
            val pickingX = mouseListener.getScreenX().toInt()
            val pickingY = mouseListener.getScreenY().toInt()

            val hovered = sceneManager.getHoveredObject(pickingX, pickingY)
            hoveredGameObject = hovered
            hoveredGameObjectUid = hovered?.getUid() ?: -1

            if (mouseListener.mouseButtonBeginPress(0)) {
                // If we are in Selection Mode (which implies this gizmo is active),
                // we handle selection.
                // Note: GizmoSystem ensures only one gizmo is active,
                // but we should verify if this logic conflicts with anything else.
                // Since this is the "Selection" tool, clicking should select.
                sceneManager.currentScene?.setSelectedGameObject(hovered)
            }
        } else {
            hoveredGameObjectUid = -1
        }
    }
}
