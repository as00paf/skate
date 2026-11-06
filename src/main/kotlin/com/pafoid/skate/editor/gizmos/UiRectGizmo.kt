package com.pafoid.skate.editor.gizmos

import com.pafoid.skate.editor.commands.objects.UiRectCommand
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.editor.ui.windows.viewport.ViewportRenderer
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.ui.UICanvasComponent
import com.pafoid.skate.engine.ui.UIElementComponent
import com.pafoid.skate.engine.ui.UiEditing
import com.pafoid.skate.engine.ui.UiHandle
import imgui.ImGui

/**
 * Viewport editing of the selected UI element: outline, resize handles, drag to move/resize (undoable) and
 * click-to-select among UI elements. Active only while a UI object is selected and the scene is not running.
 * [claimsMouse] tells the 3D gizmos to leave the mouse alone.
 */
class UiRectGizmo(
    private val engine: Engine,
    private val undoRedoManager: UndoRedoManager,
    private val viewport: ViewportRenderer,
) {
    var claimsMouse = false
        private set

    private var dragHandle: UiHandle? = null
    private var dragStart: UiRectCommand.Rect? = null

    /** Call inside the viewport window after the viewport image is drawn. */
    fun render(scene: Scene?) {
        claimsMouse = false
        val selected = scene?.selectedGameObject
        val element = selected?.getComponent<UIElementComponent>()
        val canvasGo = selected?.let { UiEditing.canvasOf(it) }
        val canvas = canvasGo?.getComponent<UICanvasComponent>()
        if (scene == null || scene.isRunning || selected == null || canvas == null || canvas.scale <= 0f) {
            dragHandle = null
            return
        }

        val dpi = viewport.dpiScale
        val k = canvas.scale / dpi
        val overImage = ImGui.isWindowHovered() && !ImGui.isPopupOpen("", imgui.flag.ImGuiPopupFlags.AnyPopupId) &&
            ImGui.getMousePosX() in viewport.imageScreenPosX..(viewport.imageScreenPosX + viewport.imageSizeX) &&
            ImGui.getMousePosY() in viewport.imageScreenPosY..(viewport.imageScreenPosY + viewport.imageSizeY)
        val mx = (ImGui.getMousePosX() - viewport.imageScreenPosX) / k
        val my = (ImGui.getMousePosY() - viewport.imageScreenPosY) / k
        val tolerance = HANDLE_PX / k

        if (element != null) drawOutline(element, k)

        val drag = dragHandle
        if (drag != null && element != null) {
            claimsMouse = true
            if (ImGui.isMouseDown(0)) {
                val io = ImGui.getIO()
                UiEditing.resize(element, drag, io.mouseDeltaX / k, io.mouseDeltaY / k)
            } else {
                val start = dragStart
                val end = UiRectCommand.Rect.of(element)
                if (start != null && start != end) {
                    undoRedoManager.pushCommand(UiRectCommand(selected, element, start, end))
                    scene.isDirty = true
                }
                dragHandle = null
                dragStart = null
            }
            return
        }

        if (!overImage) return
        val handle = element?.let { UiEditing.hitHandle(it.rect, mx, my, tolerance) }
        val pickedElsewhere = if (handle == null) UiEditing.pick(canvasGo, mx, my) else null
        claimsMouse = handle != null || pickedElsewhere != null

        if (ImGui.isMouseClicked(0)) {
            if (handle != null) {
                dragHandle = handle
                dragStart = UiRectCommand.Rect.of(element)
            } else if (pickedElsewhere != null) {
                engine.eventSystem.publish(ViewportAction.GameObjectSelected(pickedElsewhere))
            }
        }
    }

    private fun drawOutline(e: UIElementComponent, k: Float) {
        val r = e.rect
        val x0 = viewport.imageScreenPosX + r.x * k
        val y0 = viewport.imageScreenPosY + r.y * k
        val x1 = x0 + r.w * k
        val y1 = y0 + r.h * k
        val list = ImGui.getWindowDrawList()
        val line = ImGui.getColorU32(0.25f, 0.65f, 1f, 1f)
        list.addRect(x0, y0, x1, y1, line, 0f, 0, 2f)
        val cx = (x0 + x1) / 2f
        val cy = (y0 + y1) / 2f
        listOf(x0 to y0, cx to y0, x1 to y0, x0 to cy, x1 to cy, x0 to y1, cx to y1, x1 to y1).forEach { (hx, hy) ->
            list.addRectFilled(hx - 4f, hy - 4f, hx + 4f, hy + 4f, line)
        }
    }

    companion object {
        const val HANDLE_PX = 6f
    }
}
