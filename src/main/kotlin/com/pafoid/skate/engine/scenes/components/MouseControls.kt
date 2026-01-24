package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.controls.MouseListener
import com.pafoid.skate.engine.scenes.SceneManager
import org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT

class MouseControls : Component() {
    private val debounceTime = 0.2f
    private var debounce = debounceTime

    override fun editorUpdate(dt: Float) {
        debounce -= dt

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