package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.controls.KeyListener
import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.utils.Icons
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE

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
        
        // Handle global deletion input
        if (ImGui.isKeyPressed(GLFW_KEY_DELETE)) {
            propertiesWindow.getActiveObject()?.let {
                it.destroy()
                propertiesWindow.setActiveObject(null)
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
        
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.TRASH} Delete")) {
                obj.destroy()
            }
            ImGui.endPopup()
        }
        
        ImGui.popID()

        return result
    }
}