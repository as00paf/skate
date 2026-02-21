package com.pafoid.skate.engine.ecs.components

import com.pafoid.skate.editor.gizmos.PoseGizmo
import com.pafoid.skate.editor.systems.LogLevel
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.engine.assets.data.models.animations.Bone
import com.pafoid.skate.engine.assets.serialization.Serializer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EngineState
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.scene.addGameObjectToScene
import com.pafoid.skate.engine.ecs.scene.getGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.ecs.systems.System
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.Renderer
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT
import kotlin.math.floor

class MouseControls : System() {
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val serializer: Serializer by inject()
    private val logger: LoggerService by inject()
    private val renderer: Renderer by inject()
    private val engine: Engine by inject()

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
            val x = mouseListener.getScreenX().toInt()
            val y = mouseListener.getScreenY().toInt()

            val pickedId = getPickedId(x, y)
            val selectedObject = getObjectById(pickedId)

            if (selectedObject != null && selectedObject.getComponent<NonPickable>() == null) {
                scene.setSelectedGameObject(selectedObject)
            } else {
                scene.setSelectedGameObject(null)
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

    private fun getPickedId(x: Int, y: Int): Int {
        if (engine.engineState.get() != EngineState.RUNNING) return -1
        return renderer.readPixel(x, y)
    }

    private fun getObjectById(id: Int): GameObject? {
        if (engine.engineState.get() != EngineState.RUNNING) return null
        return scene.getGameObject(id)
    }

    private fun getBoneById(id: Int): Bone? {
        if (engine.engineState.get() != EngineState.RUNNING) return null
        scene.gameObjectManager.gameObjects.forEach { go ->
            go.getComponent<PoseGizmo>()?.let { pg ->
                val bone = pg.getBoneById(id)
                if (bone != null) return bone
            }
        }
        return null
    }
}