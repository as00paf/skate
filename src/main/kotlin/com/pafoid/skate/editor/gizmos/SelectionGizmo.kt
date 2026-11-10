package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.SelectionLogic
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.render.renderer.Renderer
import imgui.ImGui
import imgui.flag.ImGuiPopupFlags

class SelectionGizmo(
    undoRedoManager: UndoRedoManager,
    engine: Engine,
) : Gizmo(engine, undoRedoManager) {
    private val eventSystem: EventSystem = engine.eventSystem
    private val renderer: Renderer = engine.renderer

    /** The object a click at normalized ([x], [y]) would select; see [PickResolver] for the child/prefab convention. */
    fun getHoveredObject(scene: Scene, x: Float, y: Float): GameObject? =
        PickResolver.resolve(scene, renderer.readPixel(x, y), scene.selectedGameObject)

    /** Fallback for objects without geometry (cameras, lights), consulted when the pixel pick finds nothing. */
    var fallbackPick: (() -> GameObject?)? = null

    var hoveredGameObjectUid: Int = -1
        private set

    fun update(scene: Scene) {
        if (!inUse) {
            hoveredGameObjectUid = -1
            scene.hoveredGameObject = null
            return
        }

        if (inputProvider.isInsideViewport()) {
            val mousePos = inputProvider.getNormalizedMousePos()

            val hovered = getHoveredObject(scene, mousePos.x, mousePos.y) ?: fallbackPick?.invoke()
            scene.hoveredGameObject = hovered
            hoveredGameObjectUid = hovered?.uId ?: -1

            if (inputProvider.leftMouseButtonBeginPress()) {
                // EDT-74: Ctrl toggles the clicked object, Shift adds it; a plain click replaces the selection.
                val io = ImGui.getIO()
                val ctrl = io.keyCtrl
                val shift = io.keyShift
                when {
                    hovered != null && (ctrl || shift) -> eventSystem.publish(
                        ViewportAction.MultiSelectionChanged(SelectionLogic.viewportClick(scene.selectedGameObjects, hovered, ctrl, shift)),
                    )
                    hovered != null -> eventSystem.publish(ViewportAction.GameObjectSelected(hovered))
                    ctrl || shift -> Unit
                    !ImGui.isPopupOpen("add_component_popup", ImGuiPopupFlags.AnyPopupId) ->
                        eventSystem.publish(ViewportAction.SelectionCleared)
                }
            }
        } else {
            hoveredGameObjectUid = -1
        }
    }
}
