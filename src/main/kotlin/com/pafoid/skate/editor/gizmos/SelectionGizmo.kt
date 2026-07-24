package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.input.InputProvider
import com.pafoid.skate.engine.render.renderer.Renderer
import org.lwjgl.glfw.GLFW

class SelectionGizmo(
    inputProvider: InputProvider,
    undoRedoManager: UndoRedoManager,
    engine: Engine,
    private val eventSystem: EventSystem,
    private val gameObjectManager: GameObjectManager,
) : Gizmo(inputProvider, undoRedoManager) {
    private val renderer: Renderer = engine.renderer

    fun getHoveredObject(x: Float, y: Float): GameObject? {
        val id = renderer.readPixel(x, y)
        return gameObjectManager.getGameObject(id)
    }

    var hoveredGameObjectUid: Int = -1
        private set

    fun update(scene: Scene) {
        if (!inUse) {
            hoveredGameObjectUid = -1
            scene.hoveredGameObject = null
            return
        }

        if (inputProvider.isInsideViewport()) {
            val pickingX = inputProvider.getMouseScreenX()
            val pickingY = inputProvider.getMouseScreenY()

            val hovered = getHoveredObject(pickingX, pickingY)
            scene.hoveredGameObject = hovered
            hoveredGameObjectUid = hovered?.uId ?: -1

            if (inputProvider.isMouseButtonDown(GLFW.GLFW_MOUSE_BUTTON_LEFT, true)) {
                val event = if (hovered != null) ViewportAction.GameObjectSelected(hovered) else ViewportAction.SelectionCleared
                eventSystem.publish(event)
            }
        } else {
            hoveredGameObjectUid = -1
        }
    }
}
