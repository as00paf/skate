package com.pafoid.skate.engine.ecs.systems

import com.pafoid.skate.editor.gizmos.MeasureTool
import com.pafoid.skate.editor.gizmos.RotationGizmo
import com.pafoid.skate.editor.gizmos.ScaleGizmo
import com.pafoid.skate.editor.gizmos.SelectionGizmo
import com.pafoid.skate.editor.gizmos.TranslateGizmo
import com.pafoid.skate.editor.systems.SettingsManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.input.listeners.KeyListener
import com.pafoid.skate.engine.input.listeners.MouseListener
import com.pafoid.skate.engine.render.renderer.DebugRenderer
import com.pafoid.skate.engine.render.renderer.Renderer

/**
 * System responsible for managing and updating editor gizmos.
 *
 * Owns all gizmo instances directly and updates only the active gizmo each frame.
 * This is more efficient than registering each gizmo as a separate system.
 */
class GizmoSystem(
    keyListener: KeyListener,
    mouseListener: MouseListener,
    private val settingsManager: SettingsManager,
    private val undoRedoManager: UndoRedoManager,
    debugRenderer: DebugRenderer,
    renderer: Renderer,
    engine: Engine
) : System(priority = 100) {  // Late system - runs after input/physics

    private val keyListener: KeyListener = keyListener
    private val mouseListener: MouseListener = mouseListener
    private val renderer: Renderer = renderer
    private val engine: Engine = engine

    var usingGizmo = NONE

    // Gizmos are owned directly by this system, not registered as separate systems
    private val translateGizmo = TranslateGizmo(mouseListener, undoRedoManager, debugRenderer)
    private val rotationGizmo = RotationGizmo(mouseListener, undoRedoManager, debugRenderer)
    private val scaleGizmo = ScaleGizmo(mouseListener, undoRedoManager, debugRenderer)
    private val selectionGizmo = SelectionGizmo(mouseListener, undoRedoManager, renderer, engine)
    private val measureGizmo = MeasureTool(mouseListener, undoRedoManager, debugRenderer, settingsManager)

    override fun init(scene: Scene) {
        super.init(scene)
        // Initialize gizmos with the scene, but don't register them as separate systems
        listOf(translateGizmo, rotationGizmo, scaleGizmo, selectionGizmo, measureGizmo).forEach { gizmo ->
            gizmo.init(scene)
        }
    }

    override fun editorUpdate(dt: Float) {
        // Reset all gizmos to inactive state
        translateGizmo.setNotInUse()
        rotationGizmo.setNotInUse()
        scaleGizmo.setNotInUse()
        selectionGizmo.setNotInUse()
        measureGizmo.setNotInUse()

        // Activate the selected gizmo
        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.setInUse()
            ROTATION_GIZMO -> rotationGizmo.setInUse()
            SCALE_GIZMO -> scaleGizmo.setInUse()
            SELECTION_GIZMO -> selectionGizmo.setInUse()
            MEASURE_GIZMO -> measureGizmo.setInUse()
        }

        // Handle gizmo selection key bindings
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
            scene.setSelectedGameObject(null)
        }

        // Update only the active gizmo
        when (usingGizmo) {
            TRANSLATE_GIZMO -> translateGizmo.editorUpdate(dt)
            ROTATION_GIZMO -> rotationGizmo.editorUpdate(dt)
            SCALE_GIZMO -> scaleGizmo.editorUpdate(dt)
            SELECTION_GIZMO -> selectionGizmo.editorUpdate(dt)
            MEASURE_GIZMO -> measureGizmo.editorUpdate(dt)
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