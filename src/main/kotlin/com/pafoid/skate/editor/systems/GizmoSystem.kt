package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.gizmos.GizmoMath
import com.pafoid.skate.editor.gizmos.MeasureTool
import com.pafoid.skate.editor.gizmos.SceneVisualsGizmo
import com.pafoid.skate.editor.gizmos.RotationGizmo
import com.pafoid.skate.editor.gizmos.ScaleGizmo
import com.pafoid.skate.editor.gizmos.SelectionGizmo
import com.pafoid.skate.editor.gizmos.TranslateGizmo
import com.pafoid.skate.engine.core.Engine

class GizmoSystem(
// TODO: should use editor input state
    private val engine: Engine,
    private val settingsManager: EditorSettingsManager,
    private val undoRedoManager: UndoRedoManager,
    private val editorCamera: EditorCamera,
) {
    var usingGizmo = NONE

    var localSpace = false
    /** Translation snap step in world units; 0 disables snapping. */
    var translateSnap = 0f
    var rotateSnapDegrees = 0f
    var scaleSnap = 0f

    val snapEnabled get() = translateSnap > 0f || rotateSnapDegrees > 0f || scaleSnap > 0f

    fun toggleSnap() {
        val on = !snapEnabled
        translateSnap = if (on) DEFAULT_TRANSLATE_SNAP else 0f
        rotateSnapDegrees = if (on) DEFAULT_ROTATE_SNAP else 0f
        scaleSnap = if (on) DEFAULT_SCALE_SNAP else 0f
    }

    fun toggleSpace() {
        localSpace = !localSpace
    }

    /** Objects the gizmos act on. The scene currently tracks a single selection; multi-select plugs in here. */
    private fun selection(scene: com.pafoid.skate.engine.ecs.Scene) = scene.selectedGameObjects

    // Gizmos are owned directly by this system, not registered as separate systems
    private val debugRenderer by lazy { engine.renderer.renderResources.renderers.debug }
    private val translateGizmo by lazy { TranslateGizmo(engine, undoRedoManager, debugRenderer) }
    private val rotationGizmo by lazy { RotationGizmo(engine, undoRedoManager, debugRenderer) }
    private val scaleGizmo by lazy { ScaleGizmo(engine, undoRedoManager, debugRenderer) }
    private val selectionGizmo by lazy { SelectionGizmo(undoRedoManager, engine) }
    val sceneVisuals by lazy { SceneVisualsGizmo(debugRenderer) }

    private fun iconSizeAt(position: org.joml.Vector3f): Float {
        val cam = editorCamera.camera
        return GizmoMath.gizmoSize(
            cam.isOrthographic, editorCamera.transform.translation.distance(position), cam.fov, cam.zoom,
            cam.viewportHeight, ICON_SCREEN_FRACTION,
        )
    }

    val measureGizmo by lazy { MeasureTool(engine, undoRedoManager, debugRenderer, settingsManager) }

    fun update(dt: Float) {
        val scene = engine.sceneManager.currentScene ?: return
        // Reset all gizmos to inactive state
        translateGizmo.inUse = false
        rotationGizmo.inUse = false
        scaleGizmo.inUse = false
        selectionGizmo.inUse = false
        measureGizmo.inUse = false

        if (scene.isRunning) {
            return
        }

        val scene0 = scene
        sceneVisuals.draw(scene0, editorCamera.camera, editorCamera.transform.translation, ::iconSizeAt)
        selectionGizmo.fallbackPick = {
            sceneVisuals.pick(scene0, engine.inputProvider.screenToRay(editorCamera.camera), ::iconSizeAt)
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
        val bindings = settingsManager.editorSettings.editorInputMappings

        if (engine.inputProvider.isKeyPressed(bindings.gizmoTranslate.keyboardKey)) {
            usingGizmo = TRANSLATE_GIZMO
        } else if (engine.inputProvider.isKeyPressed(bindings.gizmoRotate.keyboardKey)) {
            usingGizmo = ROTATION_GIZMO
        } else if (engine.inputProvider.isKeyPressed(bindings.gizmoScale.keyboardKey)) {
            usingGizmo = SCALE_GIZMO
        } else if (engine.inputProvider.isKeyPressed(bindings.gizmoSelect.keyboardKey)) {
            usingGizmo = SELECTION_GIZMO
        } else if (engine.inputProvider.isKeyPressed(bindings.measureTool.keyboardKey)) {
            usingGizmo = MEASURE_GIZMO
        }

        // Update only the active gizmo
        when (usingGizmo) {
            SELECTION_GIZMO -> selectionGizmo.update(scene)
            TRANSLATE_GIZMO -> translateGizmo.also {
                it.localSpace = localSpace
                it.snapStep = translateSnap
            }.update(selection(scene), editorCamera.camera, editorCamera.transform.translation)

            ROTATION_GIZMO -> rotationGizmo.also {
                it.localSpace = localSpace
                it.snapDegrees = rotateSnapDegrees
            }.update(selection(scene), editorCamera.camera, editorCamera.transform.translation)

            SCALE_GIZMO -> scaleGizmo.also {
                it.localSpace = localSpace
                it.snapStep = scaleSnap
            }.update(selection(scene), editorCamera.camera, editorCamera.transform.translation)
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
        const val ICON_SCREEN_FRACTION = 0.03f
        const val DEFAULT_TRANSLATE_SNAP = 0.5f
        const val DEFAULT_ROTATE_SNAP = 15f
        const val DEFAULT_SCALE_SNAP = 0.1f
        const val NONE = -1
        const val TRANSLATE_GIZMO = 0
        const val ROTATION_GIZMO = 1
        const val SCALE_GIZMO = 2
        const val SELECTION_GIZMO = 3
        const val MEASURE_GIZMO = 4
    }
}