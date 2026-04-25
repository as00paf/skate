package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.data.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.ModularTile
import com.pafoid.skate.engine.ecs.components.NonPickable
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.getGameObject
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SelectionCleared
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.Renderer
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import kotlin.math.floor

class MouseControls(
    private val keyListener: KeyListener,
    private val mouseListener: MouseListener,
    private val serializer: Serializer,
    private val logger: LoggerService,
    private val renderer: Renderer,
    private val engine: Engine,
    private val eventSystem: EventSystem
) : System(priority = ExecutionPriority.EARLY) {  // Early system - runs first for input

    private var holdingObject: GameObject? = null
    private val debounceTime = 0.2f
    private var debounce = debounceTime
    
    private val gridWidth = 0.25f
    private val gridHeight = 0.25f

    private fun place() {
        holdingObject?.copy(serializer)?.let { newObj ->
            newObj.removeComponent<NonPickable>()
            scene.addGameObjectToScene(newObj)
        } ?: run { logger.logEngine("Could not place object", LogLevel.ERROR) }
    }

    override fun editorUpdate(dt: Float) {
        debounce -= dt

        val go = holdingObject
        if(go != null){
            handleGameObject(go)
        } else if (!mouseListener.isDragging() && mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT) && debounce < 0) {
            val x = mouseListener.getNormalizedX()
            val y = mouseListener.getNormalizedY()

            val pickedId = getPickedId(x, y)
            val selectedObject = getObjectById(pickedId)

            if (selectedObject != null && selectedObject.getComponent<NonPickable>() == null) {
                eventSystem.publish(GameObjectSelected(selectedObject))
            } else {
                eventSystem.publish(SelectionCleared)
            }

            debounce = debounceTime
        }
    }

    private fun handleGameObject(go: GameObject) {
        val tile = go.getComponent<ModularTile>()
        val worldPos = mouseListener.getWorld()

        val snapX = tile?.size?.x ?: gridWidth
        val snapY = tile?.size?.y ?: gridHeight

        val x = (floor(worldPos.x / snapX) * snapX) + snapX / 2f
        val y = (floor(worldPos.y / snapY) * snapY) + snapY / 2f

        go.getComponent<Transform>()?.let{ transform ->
            transform.translation.x = x
            transform.translation.y = y
        }

        if (mouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT) && debounce < 0) {
            place()
            debounce = debounceTime
        }

        if (keyListener.isKeyPressed(GLFW_KEY_ESCAPE)) {
            go.destroy()
            holdingObject = null
        }
    }

    private fun getPickedId(x: Float, y: Float): Int {
        if (engine.engineState.get() != EngineState.RUNNING) return -1
        return renderer.readPixel(x, y)
    }

    private fun getObjectById(id: Int): GameObject? {
        if (engine.engineState.get() != EngineState.RUNNING) return null
        return scene.getGameObject(id)
    }
}