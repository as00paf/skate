package com.pafoid.skate.editor.ui.windows

/**
 * Width of the seed field on Project Settings › Random (EDT-77). The field and its Randomize button share the
 * control column of the settings row; the field takes what the button leaves. When the column is too narrow for a
 * usable field beside the button, the button wraps below and the field takes the whole column, so the row never
 * reaches past the dialog edge.
 */
object SeedRowLayout {
    /** Narrowest field worth keeping beside the button: about eight digits. */
    const val MIN_FIELD_WIDTH = 80f

    data class Layout(val fieldWidth: Float, val buttonOnSameLine: Boolean)

    fun compute(available: Float, buttonWidth: Float, spacing: Float): Layout {
        val beside = available - buttonWidth - spacing
        return if (beside >= MIN_FIELD_WIDTH) Layout(beside, true)
        // ImGui reads 0 as "default width" and negatives as "align right", so never pass less than 1 px.
        else Layout(available.coerceAtLeast(1f), false)
    }
}
