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
        this.gameObject.addComponent(RotationGizmo(Window.getImGuiLayer().propertiesWindow))
    }

    override fun editorUpdate(dt: Float) {
        val translateGizmo = gameObject.getComponent<TranslateGizmo>()!!
        val rotationGizmo = gameObject.getComponent<RotationGizmo>()!!

        if (usingGizmo == TRANSLATE_GIZMO) {
            translateGizmo.setInUse()
            rotationGizmo.setNotInUse()
        } else if (usingGizmo == ROTATION_GIZMO) {
            rotationGizmo.setInUse()
            translateGizmo.setNotInUse()
        }

        if(KeyListener.isKeyPressed(GLFW_KEY_E)) {
            usingGizmo = TRANSLATE_GIZMO
        } else if(KeyListener.isKeyPressed(GLFW_KEY_R)) {
            usingGizmo = ROTATION_GIZMO
        }
    }

    fun isInteracting(): Boolean {
        val tg = gameObject.getComponent<TranslateGizmo>()
        val rg = gameObject.getComponent<RotationGizmo>()
        return tg?.isHot() == true || tg?.anyAxisActive() == true || rg?.isHot() == true || rg?.anyAxisActive() == true
    }

    companion object {
        const val TRANSLATE_GIZMO = 0
        const val ROTATION_GIZMO = 1
    }
}