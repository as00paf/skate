package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.LevelEditorSceneInitializer
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.systems.EventSystem
import com.pafoid.skate.engine.events.*
import com.pafoid.skate.engine.utils.JobSystem
import imgui.ImGui
import imgui.flag.ImGuiTabBarFlags
import imgui.flag.ImGuiTabItemFlags
import imgui.type.ImBoolean

class EditorScenesTabBar(
    private val sceneInitializer: LevelEditorSceneInitializer,
    private val eventSystem: EventSystem,
    private val stringManager: StringManager
) {
    fun render(sceneManager: SceneManager) {
        if (ImGui.beginTabBar("##EditorScenesTabBar", ImGuiTabBarFlags.Reorderable or ImGuiTabBarFlags.AutoSelectNewTabs)) {

            var tabToClose: Int? = null

            sceneManager.openScenes.forEachIndexed { index, scene ->
                val open = ImBoolean(true)
                var flags = 0
                if (scene.isDirty) flags = flags or ImGuiTabItemFlags.UnsavedDocument
                if (sceneManager.activeSceneIndex == index) flags = flags or ImGuiTabItemFlags.SetSelected

                if (ImGui.beginTabItem("${scene.name}###${scene.hashCode()}", open, flags)) {
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
                        ImGui.endPopup()
                    }

                    if (sceneManager.activeSceneIndex != index) {
                        sceneManager.switchScene(index)
                    }
                    ImGui.endTabItem()
                }

                if (!open.get()) {
                    tabToClose = index
                }
            }

            val addTabFlags = ImGuiTabItemFlags.Trailing or ImGuiTabItemFlags.NoTooltip
            if (ImGui.beginTabItem(Icons.PLUS, addTabFlags)) {
                JobSystem.runOnMain {
                    val newScene = Scene(stringManager.getString("context.scene_tab.new_scene"), sceneInitializer)
                    newScene.init()
                    sceneManager.openScene(newScene)
                }
                ImGui.endTabItem()
            }

            ImGui.endTabBar()

            tabToClose?.let { sceneManager.closeScene(it) }
        }
    }
}
