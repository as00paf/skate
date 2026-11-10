package com.pafoid.skate.editor.ui.menus

import com.pafoid.skate.editor.imgui.EditorModal
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.BuildConfig
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/** Help › About Skate (EDT-51 §2.7): a small modal with the engine name and version. */
class AboutModal(strings: StringManager) : EditorModal(strings, Icons.INFO) {
    override val titleKey = "about.title"

    override fun content() {
        ImGui.text(BuildConfig.ENGINE_NAME)
        ImGui.textDisabled(strings.getString("about.version", BuildConfig.ENGINE_VERSION))
        ImGui.spacing()
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 26f)
        ImGui.textWrapped(strings.getString("about.description"))
        ImGui.popTextWrapPos()
    }

    override fun buttons() = listOf(FooterButton("btn.close", FooterButton.Role.PRIMARY))
}
