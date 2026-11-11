package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.commands.objects.UiRectCommand
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.SelectionLogic
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UiDragStart
import com.pafoid.skate.engine.ui.UiEditing
import com.pafoid.skate.engine.ui.UiHandle
import com.pafoid.skate.engine.ui.UiRectF
import imgui.ImGui

/**
 * Viewport editing of the selected UI element (EDT-75): outline with resize handles, a pivot marker and anchor
 * markers, drag to move/resize/re-pivot/re-anchor with snapping (one undo step per drag), and click-to-select of UI
 * elements over the 3D scene ([UiViewportPick], EDT-81). Inactive while the scene is running. [claimsMouse] tells the 3D
 * gizmos to leave the mouse alone. All maths lives in [UiEditing].
 */
class UiRectGizmo(
    private val engine: Engine,
    private val undoRedoManager: UndoRedoManager,
    private val viewport: ViewportRenderer,
    /** Rect snap step in canvas units; 0 while the toolbar's Snap is off. */
    private val snap: () -> Float = { 0f },
    /** Whether the Select tool is active: then a click on any UI element selects it (EDT-81). */
    private val selectTool: () -> Boolean = { true },
) {
    var claimsMouse = false
        private set

    private var dragHandle: UiHandle? = null
    private var dragStart: UiDragStart? = null
    private var dragBefore: UiRectCommand.Rect? = null

    /** Call inside the viewport window after the viewport image is drawn. */
    fun render(scene: Scene?) {
        claimsMouse = false
        if (scene == null || scene.isRunning) {
            dragHandle = null
            return
        }
        val selected = scene.selectedGameObject
        val element = selected?.getComponent<UIElementComponent>()
        val canvasGo = selected?.let { UiEditing.canvasOf(it) }
        val canvas = canvasGo?.getComponent<UICanvasComponent>()
        // The selected UI object, when its canvas is laid out: only then are there handles to draw and drag.
        val editing = selected != null && canvas != null && canvas.scale > 0f

        val dpi = viewport.dpiScale
        val overImage = ImGui.isWindowHovered() && !ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId) &&
            ImGui.getMousePosX() in viewport.imageScreenPosX..(viewport.imageScreenPosX + viewport.imageSizeX) &&
            ImGui.getMousePosY() in viewport.imageScreenPosY..(viewport.imageScreenPosY + viewport.imageSizeY)
        // Pointer in logical pixels from the image's top-left corner.
        val imageX = ImGui.getMousePosX() - viewport.imageScreenPosX
        val imageY = ImGui.getMousePosY() - viewport.imageScreenPosY

        var handle: UiHandle? = null
        var mx = 0f
        var my = 0f
        if (editing) {
            val k = canvas!!.scale / dpi
            mx = imageX / k
            my = imageY / k
            val editAnchors = !UiEditing.isLayoutManaged(selected!!)
            val parent = UiEditing.parentRect(selected)

            if (element != null) draw(element, if (editAnchors) parent else null, k)

            val drag = dragHandle
            val start = dragStart
            if (drag != null && start != null && element != null) {
                claimsMouse = true
                if (ImGui.isMouseDown(0)) {
                    UiEditing.drag(element, start, drag, mx, my, snap())
                } else {
                    val before = dragBefore
                    val after = UiRectCommand.Rect.of(element)
                    if (before != null && before != after) {
                        undoRedoManager.pushCommand(UiRectCommand(selected, element, before, after))
                        scene.isDirty = true
                    }
                    dragHandle = null
                    dragStart = null
                    dragBefore = null
                }
                return
            }
            if (overImage) handle = element?.let { UiEditing.hitGizmo(it, parent, mx, my, HANDLE_PX / k, editAnchors) }
        } else {
            dragHandle = null
        }

        if (!overImage) return
        // EDT-81: UI is drawn over the 3D scene, so it is hit-tested first, whatever is selected.
        val picked = if (handle == null && UiViewportPick.picksOnClick(selectTool(), editing)) {
            UiViewportPick.pick(UiViewportPick.canvases(scene), imageX, imageY, dpi)
        } else null
        claimsMouse = handle != null || picked != null
        if (picked != null) scene.hoveredGameObject = picked

        if (ImGui.isMouseClicked(0)) {
            if (handle != null) {
                dragStart = UiEditing.beginDrag(selected!!, element!!, mx, my)
                dragHandle = handle.takeIf { dragStart != null }
                dragBefore = UiRectCommand.Rect.of(element)
            } else if (picked != null) {
                val io = ImGui.getIO()
                engine.eventSystem.publish(
                    if (io.keyCtrl || io.keyShift) {
                        ViewportAction.MultiSelectionChanged(SelectionLogic.viewportClick(scene.selectedGameObjects, picked, io.keyCtrl, io.keyShift))
                    } else {
                        ViewportAction.GameObjectSelected(picked)
                    },
                )
            }
        }
    }

    private fun sx(x: Float, k: Float) = viewport.imageScreenPosX + x * k
    private fun sy(y: Float, k: Float) = viewport.imageScreenPosY + y * k

    private fun draw(e: UIElementComponent, parent: UiRectF?, k: Float) {
        val r = e.rect
        val x0 = sx(r.x, k)
        val y0 = sy(r.y, k)
        val x1 = sx(r.right, k)
        val y1 = sy(r.bottom, k)
        val list = ImGui.getWindowDrawList()
        val line = ImGui.getColorU32(0.25f, 0.65f, 1f, 1f)
        list.addRect(x0, y0, x1, y1, line, 0f, 0, 2f)
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        listOf(x0 to y0, cx to y0, x1 to y0, x0 to cy, x1 to cy, x0 to y1, cx to y1, x1 to y1).forEach { (hx, hy) ->
            list.addRectFilled(hx - 4f, hy - 4f, hx + 4f, hy + 4f, line)
        }
        if (parent == null) return

        val anchorColor = ImGui.getColorU32(1f, 0.8f, 0.2f, 1f)
        val (minX, minY) = UiEditing.anchorPoint(e, parent, UiHandle.ANCHOR_MIN)
        val (maxX, maxY) = UiEditing.anchorPoint(e, parent, UiHandle.ANCHOR_MAX)
        if (minX != maxX || minY != maxY) list.addRect(sx(minX, k), sy(minY, k), sx(maxX, k), sy(maxY, k), anchorColor, 0f, 0, 1f)
        listOf(minX to minY, maxX to maxY).forEach { (ax, ay) -> diamond(sx(ax, k), sy(ay, k), anchorColor) }

        val (px, py) = UiEditing.pivotPoint(e, r)
        list.addCircle(sx(px, k), sy(py, k), 5f, ImGui.getColorU32(1f, 1f, 1f, 1f), 16, 2f)
    }

    private fun diamond(x: Float, y: Float, color: Int) {
        val d = 6f
        ImGui.getWindowDrawList().addQuadFilled(x, y - d, x + d, y, x, y + d, x - d, y, color)
    }

    companion object {
        const val HANDLE_PX = 6f
    }
}
