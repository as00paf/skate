package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import org.joml.Vector2f
import org.lwjgl.glfw.GLFW.*
import kotlin.math.floor

class MouseControls : Component() {
    private var holdingObject: GameObject? = null
    private val debounceTime = 0.2f
    private var debounce = debounceTime
    
    private val gridWidth = 0.25f
    private val gridHeight = 0.25f

    fun pickUpObject(go: GameObject) {
        holdingObject?.destroy()
        holdingObject = go
        holdingObject?.addComponent(NonPickable())
        SceneManager.getCurrentScene()?.addGameObjectToScene(go)
    }

    private fun place() {
        val scene = SceneManager.getCurrentScene() ?: return
        val newObj = holdingObject?.copy()
        newObj?.removeComponent(NonPickable::class.java)
        scene.addGameObjectToScene(newObj!!)
    }

    override fun editorUpdate(dt: Float) {
        debounce -= dt

        holdingObject?.let { go ->
            val tile = go.getComponent<ModularTile>()
            val worldPos = MouseListener.getWorld()
            
            val snapX = tile?.size?.x ?: gridWidth
            val snapY = tile?.size?.y ?: gridHeight
            
            val x = (floor(worldPos.x / snapX) * snapX) + snapX / 2f
            val y = (floor(worldPos.y / snapY) * snapY) + snapY / 2f
            
            go.transform.translation.x = x
            go.transform.translation.y = y

            if (MouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT) && debounce < 0) {
                place()
                debounce = debounceTime
            }

            if (KeyListener.isKeyPressed(GLFW_KEY_ESCAPE)) {
                go.destroy()
                holdingObject = null
            }
        } ?: run {
            if (!MouseListener.isDragging() && MouseListener.isMouseButtonDown(GLFW_MOUSE_BUTTON_LEFT) && debounce < 0) {
                val x = MouseListener.getScreenX().toInt()
                val y = MouseListener.getScreenY().toInt()
                
                val selectedObject = SceneManager.get().getPickedObject(x, y)
                
                if (selectedObject != null && selectedObject.getComponent<NonPickable>() == null) {
                    Window.getImGuiLayer().propertiesWindow.setActiveObject(selectedObject)
                } else if (selectedObject == null && !MouseListener.isDragging()) {
                    Window.getImGuiLayer().propertiesWindow.setActiveObject(null)
                }
                
                debounce = debounceTime
            }
        }
    }
}