package com.pafoid.skate.engine.scenes.components

import com.pafoid.skate.engine.controls.listeners.KeyListener
import com.pafoid.skate.engine.controls.listeners.MouseListener
import com.pafoid.skate.engine.editor.UndoRedoManager
import com.pafoid.skate.engine.render.DebugDraw
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.utils.SettingsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class GizmoSystem: Component(), KoinComponent {

    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val sceneManager: SceneManager by inject()
    private val settingsManager: SettingsManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val debugDraw: DebugDraw by inject()

    var usingGizmo = NONE

    private val translateGizmo = TranslateGizmo(sceneManager, mouseListener, undoRedoManager, debugDraw)
    private val rotationGizmo = RotationGizmo(sceneManager, mouseListener, undoRedoManager, debugDraw)
    private val scaleGizmo = ScaleGizmo(sceneManager, mouseListener, undoRedoManager, debugDraw)
    private val selectionGizmo = SelectionGizmo(sceneManager, mouseListener, undoRedoManager)
    private val measureGizmo = MeasureTool(sceneManager, mouseListener, undoRedoManager, debugDraw, settingsManager)

    override fun init(gameObject: GameObject) {
        super.init(gameObject)

        this.gameObject.addComponent(translateGizmo)
        this.gameObject.addComponent(rotationGizmo)
        this.gameObject.addComponent(scaleGizmo)
        this.gameObject.addComponent(selectionGizmo)
        this.gameObject.addComponent(measureGizmo)
    }

    override fun editorUpdate(dt: Float) {
        translateGizmo.setNotInUse()
        rotationGizmo.setNotInUse()
        scaleGizmo.setNotInUse()
        selectionGizmo.setNotInUse()
        measureGizmo.setNotInUse()

        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.setInUse()
            ROTATION_GIZMO -> rotationGizmo.setInUse()
            SCALE_GIZMO -> scaleGizmo.setInUse()
            SELECTION_GIZMO -> selectionGizmo.setInUse()
            MEASURE_GIZMO -> measureGizmo.setInUse()
        }

        val bindings = settingsManager.settings.keyBindings

        if (keyListener.isKeyPressed(bindings.gizmoTranslate)) {
            usingGizmo = TRANSLATE_GIZMO
        } else if (keyListener.isKeyPressed(bindings.gizmoRotate)) {
            usingGizmo = ROTATION_GIZMO
        } else if (keyListener.isKeyPressed(bindings.gizmoScale)) {
            usingGizmo = SCALE_GIZMO
        } else if (keyListener.isKeyPressed(bindings.gizmoSelect)) {
            usingGizmo = SELECTION_GIZMO
        } else if (keyListener.isKeyPressed(bindings.gizmoMeasure)) {
            usingGizmo = MEASURE_GIZMO
        }

        if (keyListener.keyBeginPress(bindings.deselect)) {
            sceneManager.currentScene?.setSelectedGameObject(null)
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

    fun toggleGizmo(gizmo:Int) {
        if(usingGizmo == gizmo) {
            usingGizmo = NONE
        } else {
          usingGizmo = gizmo
        }
    }

    companion object {
        const val NONE = -1
        const val TRANSLATE_GIZMO = 0
        const val ROTATION_GIZMO = 1
        const val SCALE_GIZMO = 2
        const val SELECTION_GIZMO = 3
        const val MEASURE_GIZMO = 4
    }
}
