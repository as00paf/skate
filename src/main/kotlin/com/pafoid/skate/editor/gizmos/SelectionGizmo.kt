package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.Renderer
import org.lwjgl.glfw.GLFW

class SelectionGizmo(
    mouseListener: MouseListener,
    undoRedoManager: UndoRedoManager,
    private val renderer: Renderer,
    private val eventSystem: EventSystem,
    private val gameObjectManager: GameObjectManager,
) : Gizmo(mouseListener, undoRedoManager) {

    fun getHoveredObject(x: Float, y: Float): GameObject? {
        val id = renderer.readPixel(x, y)
        return gameObjectManager.getGameObject(id)
    }

    var hoveredGameObjectUid: Int = -1
        private set

    var hoveredGameObject: GameObject? = null
        private set

    fun update(scene: Scene) {
        if (!isInUse()) {
            hoveredGameObjectUid = -1
            scene.hoveredGameObject = null
            return
        }

        if (mouseListener.isInsideViewport()) {
            val pickingX = mouseListener.getNormalizedX()
            val pickingY = mouseListener.getNormalizedY()

            val hovered = getHoveredObject(pickingX, pickingY)
            scene.hoveredGameObject = hovered
            hoveredGameObjectUid = hovered?.getUid() ?: -1

            if (mouseListener.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT, true)) {
                val event = if (hovered != null) ViewportAction.GameObjectSelected(hovered) else ViewportAction.SelectionCleared
                eventSystem.publish(event)
            }
        } else {
            hoveredGameObjectUid = -1
        }
    }
}
