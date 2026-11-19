package com.pafoid.skate.editor.imgui

/**
 * How a labelled inspector row fits the space it has (EDT-100). Every value row of the inspectors and settings goes
 * through [MImGui.row], which asks this; no ImGui calls here.
 *
 * Before, the label column was a fixed 220 px whatever the window's width or UI scale: in a window docked on the
 * right (about 170 px wide at the editor's minimum size) the values started past the edge or were squeezed to nothing.
 * Now the label column takes [LABEL_FRACTION] of the width (between [MIN_LABEL_EMS] and [MAX_LABEL_EMS] font sizes),
 * and when the values would get less than [MIN_CONTROL_EMS] the row stacks: label on top, values full width below.
 */
object InspectorLayout {
    const val LABEL_FRACTION = 0.4f
    const val MIN_LABEL_EMS = 4f
    const val MAX_LABEL_EMS = 15f

    /** The narrowest the value side of a two-column row may be; narrower stacks the row. */
    const val MIN_CONTROL_EMS = 9f

    /** The narrowest a single number field may be (about 4 digits); narrower puts each axis on its own line. */
    const val MIN_FIELD_EMS = 3f

    data class Row(val stacked: Boolean, val labelWidth: Float)

    /** A row [available] pixels wide at [fontSize]. */
    fun row(available: Float, fontSize: Float): Row {
        val label = (available * LABEL_FRACTION).coerceIn(fontSize * MIN_LABEL_EMS, fontSize * MAX_LABEL_EMS)
        val stacked = available - label < fontSize * MIN_CONTROL_EMS
        return Row(stacked, if (stacked) 0f else label)
    }

    /**
     * [count] axis fields (a reset button then a number field each, [spacing] between axes) in [available] pixels:
     * on one line when each field gets at least [MIN_FIELD_EMS], else one axis per line.
     */
    data class Axes(val oneLine: Boolean, val fieldWidth: Float)

    fun axes(available: Float, count: Int, buttonWidth: Float, spacing: Float, fontSize: Float): Axes {
        val minField = fontSize * MIN_FIELD_EMS
        val oneLine = (available - count * buttonWidth - (count - 1) * spacing) / count
        return if (oneLine >= minField) Axes(true, oneLine) else Axes(false, (available - buttonWidth).coerceAtLeast(minField))
    }

    /** [label] shortened with "..." to fit [maxWidth] as measured by [width] (unchanged when it fits). */
    fun fit(label: String, maxWidth: Float, width: (String) -> Float): String {
        if (width(label) <= maxWidth) return label
        var end = label.length
        while (end > 0 && width(label.substring(0, end) + ELLIPSIS) > maxWidth) end--
        return if (end == 0) ELLIPSIS else label.substring(0, end).trimEnd() + ELLIPSIS
    }

    const val ELLIPSIS = "..."

    /** One field drawn this frame, in screen pixels, with the right edge of the area it had to fit in. */
    data class Field(val id: String, val minX: Float, val maxX: Float, val areaMinX: Float, val areaMaxX: Float)

    /** Test hook: when set, every row and axis field reports where it was drawn (the EDT-100 guard). */
    @Volatile
    var probe: ((Field) -> Unit)? = null

    /**
     * Test hook: every standard window reports, right after `begin`, how much wider than itself its content was last
     * frame (`getScrollMaxX`). Windows have no horizontal scrollbar, so anything above 0 is out of reach (EDT-100).
     */
    @Volatile
    var windowProbe: ((window: String, overflowX: Float) -> Unit)? = null
}
