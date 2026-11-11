package com.pafoid.skate.editor.animation.controllereditor

/**
 * Sizes in the Animator window (ANI-25), from the font size instead of fixed pixels, so the window holds up at every
 * UI scale and EDT-76 density. Pure: the window passes the current font size and style.
 */
object AnimatorLayout {
    /** The parameters/states panel wants this many font sizes, the inspector [RIGHT_EMS]. */
    const val LEFT_EMS = 17f
    const val RIGHT_EMS = 22f
    /** ...but neither takes more than this share of the window, so the graph keeps the rest. */
    const val LEFT_SHARE = 0.3f
    const val RIGHT_SHARE = 0.36f
    /** The panels are never shorter than this (the window scrolls instead). */
    const val MIN_PANEL_HEIGHT_EMS = 8f
    /** Width of a number field (a threshold, an event time, a default value). */
    const val MIN_VALUE_EMS = 4.5f
    /** Text that would be narrower than this after other items goes to the next line. */
    const val MIN_TEXT_EMS = 4f

    data class PanelWidths(val left: Float, val canvas: Float, val right: Float)

    /** The three panels for a row [available] pixels wide with [spacing] between them; they add up to [available]. */
    fun panelWidths(available: Float, fontSize: Float, spacing: Float): PanelWidths {
        val room = (available - spacing * 2f).coerceAtLeast(3f)
        // Whole pixels: ImGui rounds item edges, so fractional widths can end a pixel past the panel.
        val left = kotlin.math.floor(minOf(fontSize * LEFT_EMS, available * LEFT_SHARE)).coerceAtLeast(1f)
        val right = kotlin.math.floor(minOf(fontSize * RIGHT_EMS, available * RIGHT_SHARE)).coerceAtLeast(1f)
        val canvas = kotlin.math.floor(room - left - right).coerceAtLeast(1f)
        return PanelWidths(left, canvas, right)
    }

    /** A field that would like [preferred] pixels in a row [available] wide: never wider than the row. */
    fun share(available: Float, preferred: Float): Float = kotlin.math.floor(minOf(preferred, available)).coerceAtLeast(1f)

    /**
     * [text] broken at spaces into lines no wider than [maxWidth] (as measured by [width]), for a button label that
     * would not fit on one line: ImGui draws a label's line breaks, it never wraps one. A single word wider than
     * [maxWidth] stays whole.
     */
    fun wrapWords(text: String, maxWidth: Float, width: (String) -> Float): String {
        if (width(text) <= maxWidth || ' ' !in text) return text
        val lines = mutableListOf<String>()
        var line = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (line.isEmpty() || width(candidate) <= maxWidth) line = candidate
            else { lines += line; line = word }
        }
        if (line.isNotEmpty()) lines += line
        return lines.joinToString("\n")
    }
}
