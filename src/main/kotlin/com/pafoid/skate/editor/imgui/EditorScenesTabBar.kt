package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.ecs.SceneManager
import imgui.ImGui
import imgui.flag.ImGuiTabBarFlags
import imgui.flag.ImGuiTabItemFlags
import imgui.type.ImBoolean

class EditorScenesTabBar {
    fun render(sceneManager: SceneManager) {
        if (sceneManager.openScenes.isNotEmpty()) {
            if (ImGui.beginTabBar("ScenesTabBar", ImGuiTabBarFlags.Reorderable or ImGuiTabBarFlags.AutoSelectNewTabs)) {
                
                // Track tabs that need to be closed to avoid concurrent modification during iteration
                var tabToClose: Int? = null
                
                sceneManager.openScenes.forEachIndexed { index, scene ->
                    val open = ImBoolean(true)
                    var flags = 0
                    if (scene.isDirty) flags = flags or ImGuiTabItemFlags.UnsavedDocument
                    if (sceneManager.activeSceneIndex == index) flags = flags or ImGuiTabItemFlags.SetSelected

                    // Render the tab item
                    if (ImGui.beginTabItem(scene.name, open, flags)) {
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
                
                ImGui.endTabBar()
                
                // Close the tab outside the iteration
                tabToClose?.let { sceneManager.closeScene(it) }
            }
        }
    }
}
