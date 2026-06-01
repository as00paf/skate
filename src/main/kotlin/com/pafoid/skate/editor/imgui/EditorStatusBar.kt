package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.UiConstants
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.Scene
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags

class EditorStatusBar(private val stringManager: StringManager) {
    val height = UiConstants.STATUS_BAR_HEIGHT

    fun render(currentScene: Scene?) {
        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(viewport.workPosX, viewport.workPosY + viewport.workSizeY - height, ImGuiCond.Always)
        ImGui.setNextWindowSize(viewport.workSizeX, height, ImGuiCond.Always)
        ImGui.setNextWindowViewport(viewport.id)

        val windowFlags = ImGuiWindowFlags.NoDecoration or
                ImGuiWindowFlags.NoDocking or
                ImGuiWindowFlags.NoSavedSettings or
                ImGuiWindowFlags.NoFocusOnAppearing or
                ImGuiWindowFlags.NoNav

        ImGui.pushStyleVar(ImGuiStyleVar.WindowPadding, 8f, 6f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowRounding, 0f)
        ImGui.pushStyleVar(ImGuiStyleVar.WindowBorderSize, 0f)

        if (ImGui.begin("##StatusBar", windowFlags)) {
            val fps = ImGui.getIO().framerate
            ImGui.text(stringManager.getString("lbl.status_bar.fps").format(fps))

            ImGui.sameLine()
            ImGui.textDisabled(" | ")
            ImGui.sameLine()

            val rt = Runtime.getRuntime()
            val usedMB = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
            val totalMB = rt.totalMemory() / (1024 * 1024)
            ImGui.text(stringManager.getString("lbl.status_bar.memory").format(usedMB, totalMB))

            val sceneName = currentScene?.name ?: stringManager.getString("lbl.status_bar.no_scene")
            val sceneText = stringManager.getString("lbl.status_bar.scene").format(sceneName)
            val textSize = ImGui.calcTextSize(sceneText)
            val windowWidth = ImGui.getWindowWidth()

            ImGui.sameLine(windowWidth - textSize.x - 16f)
            ImGui.text(sceneText)
        }
        ImGui.end()

        ImGui.popStyleVar(3)
    }
}
