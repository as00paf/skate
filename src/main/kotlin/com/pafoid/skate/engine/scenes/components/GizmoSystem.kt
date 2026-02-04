package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.*

class GizmoSystem: Component(), KoinComponent {

    private val keyListener: KeyListener by inject()
    private val sceneManager: SceneManager by inject()

    var usingGizmo = TRANSLATE_GIZMO

    private val translateGizmo = TranslateGizmo(sceneManager)
    private val rotationGizmo = RotationGizmo(sceneManager)
    private val scaleGizmo = ScaleGizmo(sceneManager)
    private val selectionGizmo = SelectionGizmo(sceneManager)

    override fun init(gameObject: GameObject) {
        super.init(gameObject)

        this.gameObject.addComponent(translateGizmo)
        this.gameObject.addComponent(rotationGizmo)
        this.gameObject.addComponent(scaleGizmo)
        this.gameObject.addComponent(selectionGizmo)
    }

    override fun editorUpdate(dt: Float) {
        translateGizmo.setNotInUse()
        rotationGizmo.setNotInUse()
        scaleGizmo.setNotInUse()
        selectionGizmo.setNotInUse()

        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.setInUse()
            ROTATION_GIZMO -> rotationGizmo.setInUse()
            SCALE_GIZMO -> scaleGizmo.setInUse()
            SELECTION_GIZMO -> selectionGizmo.setInUse()
        }

        if (keyListener.isKeyPressed(GLFW_KEY_W)) {
            usingGizmo = TRANSLATE_GIZMO
        } else if (keyListener.isKeyPressed(GLFW_KEY_E)) {
            usingGizmo = ROTATION_GIZMO
        } else if (keyListener.isKeyPressed(GLFW_KEY_R)) {
            usingGizmo = SCALE_GIZMO
        } else if (keyListener.isKeyPressed(GLFW_KEY_Q)) {
            usingGizmo = SELECTION_GIZMO
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
        const val SELECTION_GIZMO = 3
    }
}
