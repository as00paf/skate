package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.imgui.data.UiConstants.STATUS_BAR_HEIGHT
import com.pafoid.skate.editor.ui.windows.vcs.BranchStatusButton
import com.pafoid.skate.editor.ui.windows.vcs.VcsModals
import com.pafoid.skate.engine.core.Engine
import imgui.ImGui
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiWindowFlags

/**
 * The bottom status bar: FPS, memory and the latest toast on the left; the scene name and, as the last item, the Git
 * branch button (EDT-79 Q1) on the right. The VCS confirmation modals are drawn after it, once per frame.
 */
class EditorStatusBar(
    engine: Engine,
    private val branchButton: BranchStatusButton? = null,
    private val vcsModals: VcsModals? = null,
) {
    private val stringManager = engine.stringManager
    private val sceneManager = engine.sceneManager

    fun render() {
        branchButton?.update()
        val currentScene = sceneManager.currentScene
        val viewport = ImGui.getMainViewport()
        ImGui.setNextWindowPos(
            viewport.workPosX,
            viewport.workPosY + viewport.workSizeY - STATUS_BAR_HEIGHT,
            ImGuiCond.Always
        )
        ImGui.setNextWindowSize(viewport.workSizeX, STATUS_BAR_HEIGHT, ImGuiCond.Always)
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

            maximizedStatus()

            // Latest notice, e.g. where a screenshot was saved (EDT-42).
            EditorToast.current()?.let { notice ->
                ImGui.sameLine()
                ImGui.textDisabled(" | ")
                ImGui.sameLine()
                ImGui.textColored(0.55f, 0.85f, 0.55f, 1f, notice)
            }

            val sceneName = currentScene?.name ?: stringManager.getString("lbl.status_bar.no_scene")
            val sceneText = stringManager.getString("lbl.status_bar.scene").format(sceneName)
            val textSize = ImGui.calcTextSize(sceneText)
            val windowWidth = ImGui.getWindowWidth()
            val buttonWidth = branchButton?.width() ?: 0f
            val gap = if (buttonWidth > 0f) ImGui.getStyle().itemSpacingX * 2f else 0f

            ImGui.sameLine(windowWidth - textSize.x - buttonWidth - gap - 16f)
            ImGui.text(sceneText)
            if (buttonWidth > 0f) {
                ImGui.sameLine(windowWidth - buttonWidth - 16f)
                branchButton?.render()
            }
        }
        ImGui.end()

        ImGui.popStyleVar(3)
        vcsModals?.render()
    }

    /**
     * EDT-88: "<window> maximized" and a Restore button, always reachable even when the window's own header has
     * scrolled away.
     */
    private fun maximizedStatus() {
        val window = MaximizeSession.model?.window ?: return
        ImGui.sameLine()
        ImGui.textDisabled(" | ")
        ImGui.sameLine()
        ImGui.text("${Icons.WINDOW_MAXIMIZE} " + stringManager.getString("lbl.status_bar.maximized", stringManager.getString(window)))
        ImGui.sameLine()
        val label = "${Icons.WINDOW_RESTORE} ${stringManager.getString("btn.status_bar.restore")}##status_restore"
        if (stringManager.tipped(ImGui.smallButton(label), "btn.status_bar.restore", EditorShortcuts.MAXIMIZE_WINDOW)) {
            MaximizeSession.requestRestore()
        }
    }
}
