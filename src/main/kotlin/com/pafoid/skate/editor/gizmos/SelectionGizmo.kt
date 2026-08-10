package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.systems.GameObjectManager
import com.pafoid.skate.engine.render.renderer.Renderer

class SelectionGizmo(
    undoRedoManager: UndoRedoManager,
    engine: Engine,
) : Gizmo(engine, undoRedoManager) {
    private val eventSystem: EventSystem = engine.eventSystem
    private val gameObjectManager: GameObjectManager = engine.gameObjectManager
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
            val mousePos = inputProvider.getNormalizedMousePos()

            val hovered = getHoveredObject(mousePos.x, mousePos.y)
            scene.hoveredGameObject = hovered
            hoveredGameObjectUid = hovered?.uId ?: -1

            if (inputProvider.leftMouseButtonBeginPress()) {
                val event = if (hovered != null) ViewportAction.GameObjectSelected(hovered) else ViewportAction.SelectionCleared
                eventSystem.publish(event)
            }
        } else {
            hoveredGameObjectUid = -1
        }
    }
}
