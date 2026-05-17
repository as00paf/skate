package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.events.SceneAction
import com.pafoid.skate.editor.events.SceneAction.*
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import imgui.ImGui
import imgui.flag.ImGuiTabBarFlags
import imgui.flag.ImGuiTabItemFlags
import imgui.type.ImBoolean

class EditorScenesTabBar(
    private val eventSystem: EventSystem,
    private val stringManager: StringManager
) {
    fun render(sceneManager: SceneManager) {
        if (ImGui.beginTabBar("##EditorScenesTabBar", ImGuiTabBarFlags.Reorderable or ImGuiTabBarFlags.AutoSelectNewTabs)) {
            val openScenes = sceneManager.openScenes.toList()
            openScenes.forEachIndexed { index, scene ->
                val open = ImBoolean(true)
                var flags = 0
                if (scene.isDirty) flags = flags or ImGuiTabItemFlags.UnsavedDocument
                if (sceneManager.activeSceneIndex == index) flags = flags or ImGuiTabItemFlags.SetSelected

                val displayName = scene.name.replace(".scene", "", ignoreCase = true)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

                if (ImGui.beginTabItem("$displayName###sceneTab_${scene.getUid()}", open, flags)) {
                    eventSystem.publish(ViewportAction.TabSelected(scene))

                    // Scene tab context menu
                    if (ImGui.beginPopupContextItem()) {
                        if (ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.scene_tab.rename")}")) {
                            eventSystem.publish(RenameRequested(scene, scene.name))
                        }
                        ImGui.separator()
                        if (ImGui.menuItem("${Icons.SAVE} ${stringManager.getString("context.scene_tab.save")}")) {
                            eventSystem.publish(SaveRequested(scene))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.save_as"))) {
                            eventSystem.publish(SaveAsRequested(scene))
                        }
                        ImGui.separator()
                        val canClose = sceneManager.openScenes.size > 1
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close"), null, false, canClose)) {
                            eventSystem.publish(CloseRequested(scene))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close_others"), null, false, canClose)) {
                            eventSystem.publish(CloseOthersRequested(scene))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close_all"), null, false, canClose)) {
                            eventSystem.publish(CloseAllRequested)
                        }
                        ImGui.separator()
                        if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.scene_tab.delete")}")) {
                            eventSystem.publish(DeleteRequested(scene))
                        }
                        ImGui.endPopup()
                    }

                    ImGui.endTabItem()
                }

                if (!open.get()) {
                    eventSystem.publish(CloseRequested(scene))
                }
            }

            val addTabFlags = ImGuiTabItemFlags.Trailing or ImGuiTabItemFlags.NoTooltip
            if (ImGui.beginTabItem(Icons.PLUS, addTabFlags)) {
                eventSystem.publish(CreateRequested)
                ImGui.endTabItem()
            }

            ImGui.endTabBar()
        }
    }
}
