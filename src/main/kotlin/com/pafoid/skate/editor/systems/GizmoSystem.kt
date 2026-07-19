package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.gizmos.MeasureTool
import com.pafoid.skate.editor.gizmos.RotationGizmo
import com.pafoid.skate.editor.gizmos.ScaleGizmo
import com.pafoid.skate.editor.gizmos.SelectionGizmo
import com.pafoid.skate.editor.gizmos.TranslateGizmo
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.Scene

class GizmoSystem(
    private val settingsManager: SettingsManager,
    private val undoRedoManager: UndoRedoManager,
    private val engine: Engine,
    private val eventSystem: EventSystem,
    private val editorCamera: EditorCamera,
) {
    private val gameObjectManager = engine.gameObjectManager

    var usingGizmo = NONE

    // Gizmos are owned directly by this system, not registered as separate systems
    private val debugRenderer by lazy { engine.renderer.renderResources.renderers.debug }
    private val mouseListener by lazy { engine.inputProvider.mouseListener }
    private val keyListener by lazy { engine.inputProvider.keyListener }
    private val translateGizmo by lazy { TranslateGizmo(mouseListener, undoRedoManager, debugRenderer) }
    private val rotationGizmo by lazy { RotationGizmo(mouseListener, undoRedoManager, debugRenderer) }
    private val scaleGizmo by lazy { ScaleGizmo(mouseListener, undoRedoManager, debugRenderer) }
    private val selectionGizmo by lazy {
        SelectionGizmo(mouseListener, undoRedoManager, engine, eventSystem, gameObjectManager)
    }
    val measureGizmo by lazy { MeasureTool(mouseListener, undoRedoManager, debugRenderer, settingsManager) }

    fun update(dt: Float, scene: Scene) {
        editorCamera.update(dt)

        // Reset all gizmos to inactive state
        translateGizmo.setNotInUse()
        rotationGizmo.setNotInUse()
        scaleGizmo.setNotInUse()
        selectionGizmo.setNotInUse()
        measureGizmo.setNotInUse()

        if (scene.isRunning) {
            return
        }

        // Activate the selected gizmo
        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.setInUse()
            ROTATION_GIZMO -> rotationGizmo.setInUse()
            SCALE_GIZMO -> scaleGizmo.setInUse()
            SELECTION_GIZMO -> selectionGizmo.setInUse()
            MEASURE_GIZMO -> measureGizmo.setInUse()
        }

        // Handle gizmo selection key bindings
        val bindings = settingsManager.engine.editor.editorInputMappings

        if (keyListener.isKeyPressed(bindings.gizmoTranslate.keyboardKey)) {
            usingGizmo = TRANSLATE_GIZMO
        } else if (keyListener.isKeyPressed(bindings.gizmoRotate.keyboardKey)) {
            usingGizmo = ROTATION_GIZMO
        } else if (keyListener.isKeyPressed(bindings.gizmoScale.keyboardKey)) {
            usingGizmo = SCALE_GIZMO
        } else if (keyListener.isKeyPressed(bindings.gizmoSelect.keyboardKey)) {
            usingGizmo = SELECTION_GIZMO
        } else if (keyListener.isKeyPressed(bindings.measureTool.keyboardKey)) {
            usingGizmo = MEASURE_GIZMO
        }

        // Update only the active gizmo
        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.update(editorCamera.camera)
            ROTATION_GIZMO -> rotationGizmo.update(editorCamera.camera)
            SCALE_GIZMO -> scaleGizmo.update(editorCamera.camera)
            SELECTION_GIZMO -> selectionGizmo.update(scene)
            MEASURE_GIZMO -> measureGizmo.update(editorCamera.camera)
        }
    }

    fun isInteracting(): Boolean {
        return translateGizmo.isHot() || translateGizmo.anyAxisActive() ||
                rotationGizmo.isHot() || rotationGizmo.anyAxisActive() ||
                scaleGizmo.isHot() || scaleGizmo.anyAxisActive()
    }

    fun toggleGizmo(gizmo: Int) {
        if (usingGizmo == gizmo) {
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