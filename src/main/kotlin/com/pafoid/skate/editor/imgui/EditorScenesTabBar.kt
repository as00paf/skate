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
            
            // Track tabs that need to be closed to avoid concurrent modification during iteration
            var tabToClose: Int? = null
            
            sceneManager.openScenes.forEachIndexed { index, scene ->
                val open = ImBoolean(true)
                var flags = 0
                if (scene.isDirty) flags = flags or ImGuiTabItemFlags.UnsavedDocument
                if (sceneManager.activeSceneIndex == index) flags = flags or ImGuiTabItemFlags.SetSelected

                // Render the tab item
                if (ImGui.beginTabItem("${scene.name}###${scene.hashCode()}", open, flags)) {
                    if (sceneManager.activeSceneIndex != index) {
                        sceneManager.switchScene(index)
                    }
                    ImGui.endTabItem()
                }
                
                // Handle closing the tab via the 'X' button
                if (!open.get()) {
                    tabToClose = index
                }
            }
            
            // Add a '+' button as a tab to create a new scene
            val addTabFlags = ImGuiTabItemFlags.Trailing or ImGuiTabItemFlags.NoTooltip
            if (ImGui.beginTabItem(Icons.PLUS, addTabFlags)) {
                // This tab was clicked, create a new scene
                com.pafoid.skate.engine.utils.JobSystem.runOnMain {
                    val initializer = com.pafoid.skate.editor.LevelEditorSceneInitializer()
                    val newScene = com.pafoid.skate.engine.ecs.Scene("New Scene", initializer)
                    newScene.init()
                    sceneManager.openScene(newScene)
                }
                ImGui.endTabItem()
            }
            
            ImGui.endTabBar()
            
            // Close the tab outside the iteration
            tabToClose?.let { sceneManager.closeScene(it) }
        }
    }
}
