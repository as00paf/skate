package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.animation.PoseGizmo
import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.logs.LogLevel
import com.pafoid.skate.engine.editor.logs.LoggerService
import com.pafoid.skate.engine.imgui.ImGuiLayer
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.serialization.Serializer
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.*
import kotlin.math.floor

class MouseControls : Component(), KoinComponent {
    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val sceneManager: SceneManager by inject()
    private val serializer: Serializer by inject()
    private val imguiLayer: ImGuiLayer by inject()
    private val logger: LoggerService by inject()

    private var holdingObject: GameObject? = null
    private val debounceTime = 0.2f
    private var debounce = debounceTime
    
    private val gridWidth = 0.25f
    private val gridHeight = 0.25f

    private fun place() {
        val scene = sceneManager.currentScene ?: return
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

            val pickedId = sceneManager.getPickedId(x, y)
            val selectedObject = sceneManager.getObjectById(pickedId)

            if (selectedObject != null && selectedObject.getComponent<NonPickable>() == null) {
                sceneManager.setSelectedGameObject(selectedObject)
            } else {
                sceneManager.setSelectedGameObject(null)
                val bone = sceneManager.getJointById(pickedId)
                if (bone != null) {
                    // A bone was selected, find which GO it belongs to
                    val skater = sceneManager.currentScene?.gameObjects?.find { it.getComponent<PoseGizmo>() != null }
                    sceneManager.setSelectedGameObject(skater)
                    imguiLayer.boneTreeWindow.setSelectedBone(bone)
                } else {
                    sceneManager.setSelectedGameObject(null)
                }
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
}