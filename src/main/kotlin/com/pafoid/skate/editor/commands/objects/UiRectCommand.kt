package com.pafoid.skate.editor.commands.objects

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ui.UIElementComponent

/** Undoable move/resize of a UI element (its x, y, width and height). */
class UiRectCommand(
    private val gameObject: GameObject,
    private val element: UIElementComponent,
    private val before: Rect,
    private val after: Rect,
) : Command {
    data class Rect(val x: Float, val y: Float, val width: Float, val height: Float) {
        fun applyTo(e: UIElementComponent) {
            e.x = x; e.y = y; e.width = width; e.height = height
        }

        companion object {
            fun of(e: UIElementComponent) = Rect(e.x, e.y, e.width, e.height)
        }
    }

    override fun execute() = after.applyTo(element)
    override fun undo() = before.applyTo(element)
    override fun getDisplayName(): String = "Edit UI rect"
    override fun getTargetName(): String? = gameObject.name
}
