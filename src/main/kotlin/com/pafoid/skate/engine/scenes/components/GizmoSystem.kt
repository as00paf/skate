package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Window
import com.pafoid.skate.engine.assets.SpriteSheet
import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.scenes.GameObject
import org.lwjgl.glfw.GLFW.GLFW_KEY_E
import org.lwjgl.glfw.GLFW.GLFW_KEY_R

class GizmoSystem: Component() {

    private var usingGizmo = TRANSLATE_GIZMO

    override fun init(gameObject: GameObject) {
        super.init(gameObject)
        this.gameObject.addComponent(TranslateGizmo(Window.getImGuiLayer().propertiesWindow))
        this.gameObject.addComponent(ScaleGizmo(Window.getImGuiLayer().propertiesWindow))
    }

    override fun editorUpdate(dt: Float) {
        val translateGizmo = gameObject.getComponent<TranslateGizmo>()!!
        val scaleGizmo = gameObject.getComponent<ScaleGizmo>()!!

        if (usingGizmo == TRANSLATE_GIZMO) {
            translateGizmo.setInUse()
            scaleGizmo.setNotInUse()
        } else if (usingGizmo == SCALE_GIZMO) {
            scaleGizmo.setInUse()
            translateGizmo.setNotInUse()
        }

        if(KeyListener.isKeyPressed(GLFW_KEY_E)) {
            usingGizmo = TRANSLATE_GIZMO
        } else if(KeyListener.isKeyPressed(GLFW_KEY_R)) {
            usingGizmo = SCALE_GIZMO
        }
    }

    companion object {
        const val TRANSLATE_GIZMO = 0
        const val SCALE_GIZMO = 1
    }
}