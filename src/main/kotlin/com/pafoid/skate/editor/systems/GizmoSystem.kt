package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.gizmos.MeasureTool
import com.pafoid.skate.editor.gizmos.RotationGizmo
import com.pafoid.skate.editor.gizmos.ScaleGizmo
import com.pafoid.skate.editor.gizmos.SelectionGizmo
import com.pafoid.skate.editor.gizmos.TranslateGizmo
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene

class GizmoSystem(
// TODO: should use editor input state
    private val engine: Engine,
    private val settingsManager: SettingsManager,
    private val undoRedoManager: UndoRedoManager,
    private val editorCamera: EditorCamera,
) {
    private val eventSystem = engine.eventSystem
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
        editorCamera.update(dt)// TODO: check if done elsewhere

        // Reset all gizmos to inactive state
        translateGizmo.inUse = false
        rotationGizmo.inUse = false
        scaleGizmo.inUse = false
        selectionGizmo.inUse = false
        measureGizmo.inUse = false

        if (scene.isRunning) {
            return
        }

        // Activate the selected gizmo
        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.inUse = true
            ROTATION_GIZMO -> rotationGizmo.inUse = true
            SCALE_GIZMO -> scaleGizmo.inUse = true
            SELECTION_GIZMO -> selectionGizmo.inUse = true
            MEASURE_GIZMO -> measureGizmo.inUse = true
        }

        // Handle gizmo selection key bindings
        val bindings = settingsManager.editor.editorInputMappings

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
            SELECTION_GIZMO -> selectionGizmo.update(scene)
            TRANSLATE_GIZMO -> translateGizmo.update(scene.selectedGameObject, editorCamera.camera)
            ROTATION_GIZMO -> rotationGizmo.update(scene.selectedGameObject, editorCamera.camera)
            SCALE_GIZMO -> scaleGizmo.update(scene.selectedGameObject, editorCamera.camera)
            MEASURE_GIZMO -> measureGizmo.update(editorCamera.camera)
        }
    }

    fun toggleGizmo(gizmo: Int) {
        usingGizmo = if (usingGizmo == gizmo) {
            NONE
        } else {
            gizmo
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