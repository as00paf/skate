package com.pafoid.skate.editor.systems

import com.pafoid.skate.editor.gizmos.EditorCamera
import com.pafoid.skate.editor.gizmos.EditorVisuals
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

    /** UI authoring gizmo; while it claims the mouse the 3D gizmos stay idle. */
    var uiGizmo: com.pafoid.skate.editor.gizmos.UiRectGizmo? = null

    var localSpace = false
    /** The toolbar's Snap toggle; the steps come from Editor Settings › Scene view (EDT-57), read live. */
    var snapEnabled = false
        private set

    /** Translation snap step in world units; 0 while snapping is off. */
    val translateSnap: Float get() = if (snapEnabled) settingsManager.editorSettings.gizmoSnap.translate else 0f
    val rotateSnapDegrees: Float get() = if (snapEnabled) settingsManager.editorSettings.gizmoSnap.rotateDegrees else 0f
    val scaleSnap: Float get() = if (snapEnabled) settingsManager.editorSettings.gizmoSnap.scale else 0f
    /** UI rect gizmo snap step in canvas units; 0 while snapping is off (EDT-75). */
    val uiSnap: Float get() = if (snapEnabled) settingsManager.editorSettings.gizmoSnap.ui else 0f

    fun toggleSnap() {
        snapEnabled = !snapEnabled
    }

    fun toggleSpace() {
        localSpace = !localSpace
    }

    /**
     * Objects the gizmos act on: the whole selection (EDT-74), without objects whose ancestor is also selected, which
     * already move with it. The gizmo sits at their centre.
     */
    private fun selection(scene: com.pafoid.skate.engine.ecs.Scene) = SelectionLogic.roots(scene.selectedGameObjects)

    // Gizmos are owned directly by this system, not registered as separate systems
    private val debugRenderer by lazy { engine.renderer.renderResources.renderers.debug }
    private val translateGizmo by lazy { TranslateGizmo(engine, undoRedoManager, debugRenderer) }
    private val rotationGizmo by lazy { RotationGizmo(engine, undoRedoManager, debugRenderer) }
    private val scaleGizmo by lazy { ScaleGizmo(engine, undoRedoManager, debugRenderer) }
    private val selectionGizmo by lazy { SelectionGizmo(undoRedoManager, engine) }
    val sceneVisuals by lazy { SceneVisualsGizmo(debugRenderer) }

    /** Editor camera axes for this frame, taken from its transform (EDT-44: `camera.view` is one frame behind). */
    private var viewBasis = EditorVisuals.viewBasis(editorCamera.transform.rotation)

    /** Icon size for a constant screen size: perspective scales with view depth, not straight-line distance (EDT-44). */
    private fun iconSizeAt(position: org.joml.Vector3f): Float {
        val cam = editorCamera.camera
        val depth = EditorVisuals.viewDepth(editorCamera.transform.translation, viewBasis.forward, position, cam.nearPlane)
        return GizmoMath.gizmoSize(cam.isOrthographic, depth, cam.fov, cam.zoom, cam.viewportHeight, ICON_SCREEN_FRACTION)
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
        viewBasis = EditorVisuals.viewBasis(editorCamera.transform.rotation)
        sceneVisuals.draw(scene0, editorCamera.camera, editorCamera.transform.translation, ::iconSizeAt, viewBasis)
        selectionGizmo.fallbackPick = {
            sceneVisuals.pick(scene0, engine.inputProvider.screenToRay(editorCamera.camera), ::iconSizeAt)
        }

        if (uiGizmo?.claimsMouse == true) return

        // Activate the selected gizmo
        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.inUse = true
            ROTATION_GIZMO -> rotationGizmo.inUse = true
            SCALE_GIZMO -> scaleGizmo.inUse = true
            SELECTION_GIZMO -> selectionGizmo.inUse = true
            MEASURE_GIZMO -> measureGizmo.inUse = true
        }

        // Tool keys (EDT-10: not while Ctrl is held, so Ctrl+<key> shortcuts do not also switch tools).
        val bindings = settingsManager.editorSettings.editorInputMappings
        val input = engine.inputProvider
        if (!input.isControlKeyDown()) {
            if (input.isKeyPressed(bindings.gizmoTranslate.keyboardKey)) {
                usingGizmo = TRANSLATE_GIZMO
            } else if (input.isKeyPressed(bindings.gizmoRotate.keyboardKey)) {
                usingGizmo = ROTATION_GIZMO
            } else if (input.isKeyPressed(bindings.gizmoScale.keyboardKey)) {
                usingGizmo = SCALE_GIZMO
            } else if (input.isKeyPressed(bindings.gizmoSelect.keyboardKey)) {
                usingGizmo = SELECTION_GIZMO
            } else if (input.isKeyPressed(bindings.measureTool.keyboardKey)) {
                usingGizmo = MEASURE_GIZMO
            }
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
        const val NONE = -1
        const val TRANSLATE_GIZMO = 0
        const val ROTATION_GIZMO = 1
        const val SCALE_GIZMO = 2
        const val SELECTION_GIZMO = 3
        const val MEASURE_GIZMO = 4
    }
}