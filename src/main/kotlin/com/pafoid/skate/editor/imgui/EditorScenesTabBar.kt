package com.pafoid.skate.editor.imgui

import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.ecs.SceneManager
import imgui.ImGui
import imgui.flag.ImGuiTabBarFlags
import imgui.flag.ImGuiTabItemFlags
import imgui.type.ImBoolean

class EditorScenesTabBar {
    fun render(sceneManager: SceneManager) {
        if (ImGui.beginTabBar("##EditorScenesTabBar", ImGuiTabBarFlags.Reorderable or ImGuiTabBarFlags.AutoSelectNewTabs)) {

            var tabToClose: Int? = null
            
            sceneManager.openScenes.forEachIndexed { index, scene ->
                val open = ImBoolean(true)
                var flags = 0
                if (scene.isDirty) flags = flags or ImGuiTabItemFlags.UnsavedDocument
                if (sceneManager.activeSceneIndex == index) flags = flags or ImGuiTabItemFlags.SetSelected

                if (ImGui.beginTabItem("${scene.name}###${scene.hashCode()}", open, flags)) {
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
                com.pafoid.skate.engine.utils.JobSystem.runOnMain {
                    val initializer = com.pafoid.skate.editor.LevelEditorSceneInitializer()
                    val newScene = com.pafoid.skate.engine.ecs.Scene("New Scene", initializer)
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
