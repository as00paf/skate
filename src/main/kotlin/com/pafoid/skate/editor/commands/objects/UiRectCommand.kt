package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ui.UIElementComponent

/** Undoable viewport edit of a UI element: move/resize (x, y, width, height), pivot and anchors (EDT-75). */
class UiRectCommand(
    private val gameObject: GameObject,
    private val element: UIElementComponent,
    private val before: Rect,
    private val after: Rect,
) : Command {
    data class Rect(
        val x: Float, val y: Float, val width: Float, val height: Float,
        val pivotX: Float = 0f, val pivotY: Float = 0f,
        val anchorMinX: Float = 0f, val anchorMinY: Float = 0f, val anchorMaxX: Float = 0f, val anchorMaxY: Float = 0f,
    ) {
        fun applyTo(e: UIElementComponent) {
            e.x = x; e.y = y; e.width = width; e.height = height
            e.pivotX = pivotX; e.pivotY = pivotY
            e.anchors(anchorMinX, anchorMinY, anchorMaxX, anchorMaxY)
        }

        companion object {
            fun of(e: UIElementComponent) = Rect(
                e.x, e.y, e.width, e.height, e.pivotX, e.pivotY, e.anchorMinX, e.anchorMinY, e.anchorMaxX, e.anchorMaxY,
            )
        }
    }

    override fun execute() = after.applyTo(element)
    override fun undo() = before.applyTo(element)
    override fun getDisplayName(): String = "Edit UI rect"
    override fun getTargetName(): String? = gameObject.name
}
