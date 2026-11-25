package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.CloseGuard
import com.pafoid.skate.editor.systems.CloseTarget
import com.pafoid.skate.engine.core.StringManager
import imgui.ImGui

/**
 * The editor-wide "unsaved assets" prompt (EDT-82): shown when the project or the editor closes while scenes,
 * animation clips or animator controllers have unsaved changes. Save all, Discard or Cancel; a save that fails keeps
 * the prompt up with what is still unsaved. The state lives in [CloseGuard].
 */
class UnsavedAssetsModal(strings: StringManager, private val guard: CloseGuard) : EditorModal(strings, Icons.SAVE, minWidth = 420f) {
    override val titleKey = "dialog.unsaved_assets.title"

    override val wantsOpen: Boolean get() = guard.pending != null

    override fun content() {
        val target = guard.pending ?: return
        ImGui.pushTextWrapPos(ImGui.getFontSize() * 30f)
        ImGui.textWrapped(strings.getString(if (target == CloseTarget.EDITOR) "dialog.unsaved_assets.message_editor" else "dialog.unsaved_assets.message_project"))
        ImGui.popTextWrapPos()
        ImGui.spacing()
        val failed = guard.failed.map { it.kind to it.id }.toSet()
        for (asset in guard.unsaved()) {
            val kind = strings.getString("lbl.unsaved_assets.kind.${asset.kind.key}")
            ImGui.bulletText("${asset.name} ($kind)")
            Tooltips.always(asset.id)
            if ((asset.kind to asset.id) in failed) {
                ImGui.sameLine()
                MImGui.errorText(strings.getString("lbl.unsaved_assets.not_saved"))
            }
        }
        if (guard.failed.isNotEmpty()) {
            ImGui.spacing()
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 30f)
            MImGui.errorText(strings.getString("dialog.unsaved_assets.save_failed"))
            ImGui.popTextWrapPos()
        }
    }

    override fun buttons() = listOf(
        FooterButton("btn.cancel", FooterButton.Role.CANCEL) { guard.cancel() },
        // Leftmost and never the Enter action: Enter saves, it never throws work away.
        FooterButton("btn.unsaved_assets.discard", FooterButton.Role.SECONDARY) { guard.discard() },
        // Stays up when a save fails, showing what is still unsaved.
        FooterButton("btn.unsaved_assets.save_all", FooterButton.Role.PRIMARY, closes = false) {
            if (guard.saveAll()) ImGui.closeCurrentPopup()
        },
    )
}
