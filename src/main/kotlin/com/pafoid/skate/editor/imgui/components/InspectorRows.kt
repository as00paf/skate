package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/**
 * One inspector row of the water and wind sections (EDT-90) through the shared EDT-100 row layout ([MImGui.row]): the
 * label column adapts to the width and the row stacks in a narrow dock. [id] must not be translated text.
 */
internal inline fun inspectorRow(strings: StringManager, id: String, labelKey: String, crossinline widget: () -> Unit) {
    MImGui.row(strings.getString(labelKey), id, labelExtra = { Tooltips.showIfPresent(strings, labelKey + Tooltips.SUFFIX) }) {
        widget()
        Tooltips.showIfPresent(strings, labelKey + Tooltips.SUFFIX)
    }
}

/** A greyed hint that wraps at the inspector's width. */
internal fun wrappedHint(text: String) {
    ImGui.beginDisabled()
    ImGui.textWrapped(text)
    ImGui.endDisabled()
}

/**
 * A section title that wraps, over a separator. `ImGui.separatorText` doesn't wrap, so a title such as "Flow and
 * Material" made the narrow right dock wider than itself (EDT-100 guard).
 */
internal fun sectionHeader(title: String) {
    ImGui.spacing()
    ImGui.pushTextWrapPos(0f)
    ImGui.textUnformatted(title)
    ImGui.popTextWrapPos()
    ImGui.separator()
}
