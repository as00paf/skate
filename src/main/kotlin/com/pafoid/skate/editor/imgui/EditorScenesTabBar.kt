package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.events.SceneCloseAllRequested
import com.pafoid.skate.engine.events.SceneCloseOthersRequested
import com.pafoid.skate.engine.events.SceneCloseRequested
import com.pafoid.skate.engine.events.SceneCreateRequested
import com.pafoid.skate.engine.events.SceneDeleteRequested
import com.pafoid.skate.engine.events.SceneRenameRequested
import com.pafoid.skate.engine.events.SceneSaveAsRequested
import com.pafoid.skate.engine.events.SceneSaveRequested
import com.pafoid.skate.engine.events.SceneTabSelected
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

                if (ImGui.beginTabItem("$displayName###${scene.hashCode()}", open, flags)) {
                    eventSystem.publish(SceneTabSelected(index))

                    // Scene tab context menu
                    if (ImGui.beginPopupContextItem()) {
                        if (ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.scene_tab.rename")}")) {
                            eventSystem.publish(SceneRenameRequested(index, scene.name))
                        }
                        ImGui.separator()
                        if (ImGui.menuItem("${Icons.SAVE} ${stringManager.getString("context.scene_tab.save")}")) {
                            eventSystem.publish(SceneSaveRequested(index))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.save_as"))) {
                            eventSystem.publish(SceneSaveAsRequested(index))
                        }
                        ImGui.separator()
                        val canClose = sceneManager.openScenes.size > 1
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close"), null, false, canClose)) {
                            eventSystem.publish(SceneCloseRequested(index))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close_others"), null, false, canClose)) {
                            eventSystem.publish(SceneCloseOthersRequested(index))
                        }
                        if (ImGui.menuItem(stringManager.getString("context.scene_tab.close_all"), null, false, canClose)) {
                            eventSystem.publish(SceneCloseAllRequested)
                        }
                        ImGui.separator()
                        if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.scene_tab.delete")}")) {
                            eventSystem.publish(SceneDeleteRequested(index))
                        }
                        ImGui.endPopup()
                    }

                    ImGui.endTabItem()
                }

                if (!open.get()) {
                    eventSystem.publish(SceneCloseRequested(index))
                }
            }

            val addTabFlags = ImGuiTabItemFlags.Trailing or ImGuiTabItemFlags.NoTooltip
            if (ImGui.beginTabItem(Icons.PLUS, addTabFlags)) {
                eventSystem.publish(SceneCreateRequested)
                ImGui.endTabItem()
            }

            ImGui.endTabBar()
        }
    }
}
