package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.LevelEditorSceneInitializer.Companion.EDITOR_TOOLS
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.DeleteGameObjectCommand
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import imgui.ImGui
import imgui.flag.ImGuiTreeNodeFlags
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE

class SceneHierarchyWindow: KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()

    fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.hierarchy"))

        val gameObjects = scene.gameObjectManager.gameObjects

        gameObjects.forEachIndexed { index, obj ->
            if (obj.parent == null && obj.name != EDITOR_TOOLS) { // Only draw root objects
                doTreeNode(obj, index)
            }
        }
        
        // Handle global deletion input
        if (ImGui.isWindowFocused() && ImGui.isKeyPressed(GLFW_KEY_DELETE)) {
            sceneManager.currentScene?.let { scene ->
                scene.getSelectedGameObject()?.let { go ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(go, scene))
                }
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
                sceneManager.currentScene?.let { scene ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(obj, scene))
                }
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