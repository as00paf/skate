package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags

class SceneHierarchyWindow(private val propertiesWindow: PropertiesWindow) {

    fun imgui(scene: Scene) {
        ImGui.begin("Scene Hierarchy")

        val gameObjects = scene.gameObjects

        gameObjects.forEachIndexed { index, obj ->
            val treeNodeOpen = doTreeNode(obj, index)
            if (treeNodeOpen) {
                ImGui.treePop()
            }
        }

        ImGui.end()
    }

    private fun doTreeNode(obj: GameObject, index: Int): Boolean {
        ImGui.pushID(index)

        var flags = ImGuiTreeNodeFlags.FramePadding or ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.SpanAvailWidth
        if (obj == propertiesWindow.getActiveObject()) {
            flags = flags or ImGuiTreeNodeFlags.Selected
        }
        
        val result = ImGui.treeNodeEx(obj.name + "##" + index, flags, obj.name)
        if (ImGui.isItemClicked()) {
            propertiesWindow.setActiveObject(obj)
        }
        ImGui.popID()

        return result
    }
}