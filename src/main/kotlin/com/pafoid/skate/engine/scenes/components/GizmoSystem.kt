package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.Window

import com.pafoid.skate.engine.controls.listeners.KeyListener

import com.pafoid.skate.engine.scenes.GameObject

import org.lwjgl.glfw.GLFW.*



class GizmoSystem: Component() {



    private var usingGizmo = TRANSLATE_GIZMO



    override fun init(gameObject: GameObject) {

        super.init(gameObject)

        this.gameObject.addComponent(TranslateGizmo(Window.getImGuiLayer().propertiesWindow))

        this.gameObject.addComponent(RotationGizmo(Window.getImGuiLayer().propertiesWindow))

        this.gameObject.addComponent(ScaleGizmo(Window.getImGuiLayer().propertiesWindow))

    }



    override fun editorUpdate(dt: Float) {

        val translateGizmo = gameObject.getComponent<TranslateGizmo>()!!

        val rotationGizmo = gameObject.getComponent<RotationGizmo>()!!

        val scaleGizmo = gameObject.getComponent<ScaleGizmo>()!!



        translateGizmo.setNotInUse()

        rotationGizmo.setNotInUse()

        scaleGizmo.setNotInUse()



        when (usingGizmo) {

            TRANSLATE_GIZMO -> translateGizmo.setInUse()

            ROTATION_GIZMO -> rotationGizmo.setInUse()

            SCALE_GIZMO -> scaleGizmo.setInUse()

        }



        if(KeyListener.isKeyPressed(GLFW_KEY_W)) {

            usingGizmo = TRANSLATE_GIZMO

        } else if(KeyListener.isKeyPressed(GLFW_KEY_E)) {

            usingGizmo = ROTATION_GIZMO

        } else if(KeyListener.isKeyPressed(GLFW_KEY_R)) {

            usingGizmo = SCALE_GIZMO

        } else if(KeyListener.isKeyPressed(GLFW_KEY_Q)) {

            usingGizmo = -1 // Selection mode (no gizmo)

        }

    }



    fun isInteracting(): Boolean {

        val tg = gameObject.getComponent<TranslateGizmo>()

        val rg = gameObject.getComponent<RotationGizmo>()

        val sg = gameObject.getComponent<ScaleGizmo>()

        return tg?.isHot() == true || tg?.anyAxisActive() == true || 

               rg?.isHot() == true || rg?.anyAxisActive() == true ||

               sg?.isHot() == true || sg?.anyAxisActive() == true

    }



    companion object {

        const val TRANSLATE_GIZMO = 0

        const val ROTATION_GIZMO = 1

        const val SCALE_GIZMO = 2

    }

}
