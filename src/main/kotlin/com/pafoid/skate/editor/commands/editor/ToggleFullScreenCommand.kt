package com.pafoid.skate.editor.commands.editor

import com.pafoid.skate.editor.commands.Command
import com.pafoid.skate.editor.imgui.ImGuiLayer

class ToggleFullScreenCommand(private val imGuiLayer: ImGuiLayer) : Command {
    override fun execute() {
        imGuiLayer.isViewportMaximized = !imGuiLayer.isViewportMaximized
    }

    override fun undo() {
        execute()
    }

    override fun getDisplayName(): String = "Toggle Full Screen"
    override fun getTargetName(): String = "GameViewport"
}