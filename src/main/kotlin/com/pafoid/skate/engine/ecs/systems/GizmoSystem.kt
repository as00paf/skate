package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.gizmos.*
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.addSystem
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.Renderer
import org.koin.core.component.inject

class GizmoSystem : System() {

    private val keyListener: KeyListener by inject()
    private val mouseListener: MouseListener by inject()
    private val sceneManager: SceneManager by inject()
    private val settingsManager: SettingsManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val debugRenderer: DebugRenderer by inject()
    private val renderer: Renderer by inject()
    private val engine: Engine by inject()

    var usingGizmo = NONE

    private val translateGizmo = TranslateGizmo(mouseListener, undoRedoManager, debugRenderer)
    private val rotationGizmo = RotationGizmo(mouseListener, undoRedoManager, debugRenderer)
    private val scaleGizmo = ScaleGizmo(mouseListener, undoRedoManager, debugRenderer)
    private val selectionGizmo = SelectionGizmo(mouseListener, undoRedoManager, renderer, engine)
    private val measureGizmo = MeasureTool(mouseListener, undoRedoManager, debugRenderer, settingsManager)

    override fun init(scene: Scene) {
        super.init(scene)
        listOf(translateGizmo, rotationGizmo, scaleGizmo, selectionGizmo, measureGizmo).forEach { gizmo ->
            scene.addSystem(gizmo)
            gizmo.init(scene)
        }
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
        // Check the individual gizmos directly since they're now owned by this system
        return translateGizmo.isHot() == true || translateGizmo.anyAxisActive() == true ||
                rotationGizmo.isHot() == true || rotationGizmo.anyAxisActive() == true ||
                scaleGizmo.isHot() == true || scaleGizmo.anyAxisActive() == true
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