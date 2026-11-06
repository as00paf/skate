package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.render.rhi.GraphicsBackend
import imgui.ImGui

/**
 * The graphics backend picker of New Project and Project Settings (EDT-43). Its tooltip says that only exported games
 * use the choice and that the editor stays on OpenGL; an experimental backend shows a warning under it.
 */
object GraphicsBackendField {
    const val LABEL_KEY = "lbl.graphics_backend"
    const val TOOLTIP_KEY = "lbl.graphics_backend.tooltip"
    const val WARNING_KEY = "lbl.graphics_backend.experimental_warning"

    /** Draws the combo for [current]; returns the backend the user picked this frame, or null. */
    fun render(strings: StringManager, current: GraphicsBackend, width: Float = 260f): GraphicsBackend? {
        var picked: GraphicsBackend? = null
        ImGui.setNextItemWidth(width)
        if (ImGui.beginCombo(strings.label(LABEL_KEY), strings.getString(current.labelKey))) {
            for (backend in GraphicsBackend.entries) {
                if (ImGui.selectable(strings.getString(backend.labelKey), backend == current) && backend != current) picked = backend
                Tooltips.show(strings, TOOLTIP_KEY)
            }
            ImGui.endCombo()
        }
        Tooltips.show(strings, TOOLTIP_KEY)
        if ((picked ?: current).experimental) MImGui.warningText(strings.getString(WARNING_KEY))
        return picked
    }
}
