package com.pafoid.skate.editor.imgui.components

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags

/**
 * One inspector row that fits any width (EDT-90): a wrapped label on the left, [widget] filling the right column.
 * Long translated labels wrap instead of pushing the inspector wider. [id] must not be translated text.
 */
internal inline fun inspectorRow(strings: StringManager, id: String, labelKey: String, widget: () -> Unit) {
    if (!ImGui.beginTable("##row.$id", 2, ImGuiTableFlags.SizingStretchProp)) return
    ImGui.tableSetupColumn("##label", ImGuiTableColumnFlags.WidthStretch, LABEL_WEIGHT)
    ImGui.tableSetupColumn("##value", ImGuiTableColumnFlags.WidthStretch, 1f - LABEL_WEIGHT)
    ImGui.tableNextRow()
    ImGui.tableSetColumnIndex(0)
    ImGui.alignTextToFramePadding()
    ImGui.textWrapped(strings.getString(labelKey))
    Tooltips.showIfPresent(strings, labelKey + Tooltips.SUFFIX)
    ImGui.tableSetColumnIndex(1)
    ImGui.setNextItemWidth(-1f)
    widget()
    Tooltips.showIfPresent(strings, labelKey + Tooltips.SUFFIX)
    ImGui.endTable()
}

/** A greyed hint that wraps at the inspector's width. */
internal fun wrappedHint(text: String) {
    ImGui.beginDisabled()
    ImGui.textWrapped(text)
    ImGui.endDisabled()
}

private const val LABEL_WEIGHT = 0.42f
