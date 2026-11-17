package com.pafoid.skate.editor.imgui

import imgui.ImGui

/**
 * A field followed by buttons on the same row (EDT-86): a texture combo and its Delete, the Animator's controller
 * combo and its buttons, a list item and its Remove. The field used to take its default or full width and the buttons
 * were placed after it, so they were clipped or pushed out of the window, more so with EDT-76's padding. The buttons'
 * room is measured from the current style (font size and scale, frame padding, item spacing) and the field gets the
 * rest; when that would leave the field narrower than [minFieldWidth], the buttons go on the next line instead.
 */
object TrailingButtons {
    /** The narrowest a field gets before its buttons wrap, in multiples of the font size. */
    const val MIN_FIELD_EMS = 6f

    /** The field's width, and whether the buttons go on the next line. */
    data class Layout(val fieldWidth: Float, val wrapped: Boolean)

    /** A button's width for a label [textWidth] wide: the text plus the frame padding on both sides. */
    fun buttonWidth(textWidth: Float, framePaddingX: Float): Float = textWidth + framePaddingX * 2f

    /**
     * Where the field and its buttons go in a row [available] pixels wide. [labelWidth] is the room the field's own
     * visible label takes on its right (0 for a `##` label). The field is never narrower than [minFieldWidth] unless
     * [available] itself is.
     */
    fun layout(available: Float, buttonWidths: List<Float>, labelWidth: Float, itemSpacingX: Float, minFieldWidth: Float): Layout {
        val buttons = buttonWidths.sumOf { (it + itemSpacingX).toDouble() }.toFloat()
        val oneLine = available - labelWidth - buttons
        return if (buttonWidths.isEmpty() || oneLine >= minFieldWidth) {
            Layout(oneLine.coerceAtLeast(1f), false)
        } else {
            Layout((available - labelWidth).coerceAtLeast(1f), true)
        }
    }

    /**
     * Whether the button [buttonWidth] wide still fits on the current line after an item ending at [lineEndX], with
     * the row's right edge at [rightX]; when not it starts the next line.
     */
    fun fitsOnLine(lineEndX: Float, buttonWidth: Float, itemSpacingX: Float, rightX: Float): Boolean =
        lineEndX + itemSpacingX + buttonWidth <= rightX + 0.5f

    /**
     * Sets the width of the next item (the field) so the buttons labelled [buttonLabels] fit after it, and returns
     * the [Row] to call [Row.next] on before each button. [fieldLabel] is the field's own label when it shows one.
     */
    fun begin(vararg buttonLabels: String, fieldLabel: String? = null): Row {
        val style = ImGui.getStyle()
        val widths = buttonLabels.map { buttonWidth(ImGui.calcTextSize(it, true).x, style.framePaddingX) }
        val labelText = fieldLabel?.let { ImGui.calcTextSize(it, true).x } ?: 0f
        val labelWidth = if (labelText > 0f) labelText + style.itemInnerSpacingX else 0f
        val available = ImGui.getContentRegionAvailX()
        val rightX = ImGui.getCursorScreenPosX() + available
        val layout = layout(available, widths, labelWidth, style.itemSpacingX, ImGui.getFontSize() * MIN_FIELD_EMS)
        ImGui.setNextItemWidth(layout.fieldWidth)
        return Row(widths, layout, rightX)
    }

    /** Places the trailing buttons of one row; see [begin]. */
    class Row internal constructor(private val widths: List<Float>, val layout: Layout, private val rightX: Float) {
        private var index = 0

        /** Call right before drawing the next button: keeps it on the field's line, or wraps it, so it is never cut. */
        fun next() {
            val width = widths.getOrElse(index) { 0f }
            val first = index == 0
            index++
            // The first button of a wrapped row starts the next line; the others follow while they fit.
            val sameLine = !(first && layout.wrapped) && fitsOnLine(ImGui.getItemRectMaxX(), width, ImGui.getStyle().itemSpacingX, rightX)
            if (sameLine) ImGui.sameLine()
            placed?.let { record ->
                val x = ImGui.getCursorScreenPosX()
                val clip = ImGui.getWindowDrawList()
                record(Placed(x, x + width, clip.clipRectMinX, clip.clipRectMaxX))
            }
        }
    }

    /** Where a trailing button went, against the clip rectangle it is drawn into; read by tests. */
    data class Placed(val minX: Float, val maxX: Float, val clipMinX: Float, val clipMaxX: Float)

    /** Test hook: receives every placed button. */
    @Volatile
    var placed: ((Placed) -> Unit)? = null
}
