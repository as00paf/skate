package com.pafoid.skate.engine.editor

import com.pafoid.skate.engine.scenes.GameObject
import com.pafoid.skate.engine.scenes.Scene
import com.pafoid.skate.engine.scenes.SceneManager
import com.pafoid.skate.engine.scenes.editor.LevelEditorSceneInitializer.Companion.EDITOR_TOOLS
import com.pafoid.skate.engine.utils.Icons
import com.pafoid.skate.engine.utils.StringManager
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE

class SceneHierarchyWindow: KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()

    fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.hierarchy"))

        val gameObjects = scene.gameObjects

        gameObjects.forEachIndexed { index, obj ->
            if (obj.parent == null && obj.name != EDITOR_TOOLS) { // Only draw root objects
                doTreeNode(obj, index)
            }
        }
        
        // Handle global deletion input
        if (ImGui.isWindowFocused() && ImGui.isKeyPressed(GLFW_KEY_DELETE)) {
            sceneManager.currentScene?.getSelectedGameObject()?.let {
                sceneManager.deleteGameObject(it)
            }
        }

        ImGui.end()
    }

    private fun doTreeNode(obj: GameObject, index: Int) {
        ImGui.pushID(index)

        var flags = ImGuiTreeNodeFlags.FramePadding or ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.SpanAvailWidth
        if (obj.children.isEmpty()) {
            flags = flags or ImGuiTreeNodeFlags.Leaf
        }
        if (obj == sceneManager.currentScene?.getSelectedGameObject()) {
            flags = flags or ImGuiTreeNodeFlags.Selected
        }
        
        val nodeOpen = ImGui.treeNodeEx(obj.name + "##" + index, flags)

        if (ImGui.isItemClicked()) {
            sceneManager.currentScene?.setSelectedGameObject(obj)
        }
        
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("lbl.delete")}")) {
                sceneManager.deleteGameObject(obj)
            }
            ImGui.endPopup()
        }

        if (nodeOpen) {
            obj.children.forEachIndexed { childIndex, child ->
                doTreeNode(child, childIndex)
            }
            ImGui.treePop()
        }
        
        ImGui.popID()
    }
}