package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/**
 * Tooltips for buttons and menu items (EDT-05). A control's tooltip key is derived from its label key:
 * `<labelKey>.tooltip` (e.g. `menu.file.save` -> `menu.file.save.tooltip`), in the same per-area string file.
 *
 * Wrap the control call so the tooltip belongs to that item (the call runs first, so it is ImGui's last item):
 * ```
 * if (strings.tipped(ImGui.button(strings.label("btn.export.start")), "btn.export.start")) start()
 * if (strings.tippedWith(ImGui.button(Icons.PLAY), "tooltip.viewport_toolbar.play_simulation")) play()
 * ```
 * `TooltipGuardTest` fails on buttons and menu items with a translated or icon label and no tooltip.
 */
object Tooltips {
    const val SUFFIX = ".tooltip"

    fun keyFor(labelKey: String): String = labelKey + SUFFIX

    /**
     * Property tooltips: shows [tooltipKey] for the last item only when the tables have it (fields of components
     * and UI elements; `StringTablesTest` requires one for every `component.<Type>.<field>` and `ui.inspector.*`).
     */
    fun showIfPresent(strings: StringManager, tooltipKey: String) {
        if (ImGui.isItemHovered() && strings.hasString(tooltipKey)) ImGui.setTooltip(strings.getString(tooltipKey))
    }

    /** Shows [tooltipKey]'s text while the last item is hovered. */
    fun show(strings: StringManager, tooltipKey: String) {
        if (ImGui.isItemHovered()) ImGui.setTooltip(strings.getString(tooltipKey))
    }
}

/** Returns [clicked] after attaching the tooltip derived from [labelKey] to the item just drawn. */
fun StringManager.tipped(clicked: Boolean, labelKey: String): Boolean {
    Tooltips.show(this, Tooltips.keyFor(labelKey))
    return clicked
}

/** Like [tipped] with an explicit [tooltipKey] (icon-only buttons, labels built from several keys). */
fun StringManager.tippedWith(clicked: Boolean, tooltipKey: String): Boolean {
    Tooltips.show(this, tooltipKey)
    return clicked
}
