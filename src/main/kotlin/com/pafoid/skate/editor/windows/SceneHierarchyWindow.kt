package com.pafoid.skate.editor.windows

import com.pafoid.skate.editor.imgui.IWindowWithScene
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
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE

class SceneHierarchyWindow : IWindowWithScene, KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    
    private val searchQuery = ImString(256)
    private var isLinked = false

    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.hierarchy"))
        
        // Toolbar
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 60f)
        ImGui.inputTextWithHint("##Search", "${Icons.SEARCH} Search...", searchQuery)
        ImGui.popItemWidth()
        
        ImGui.sameLine()
        if (ImGui.button(Icons.PLUS)) {
            val newObj = GameObject("New GameObject")
            scene.gameObjectManager.addGameObject(newObj)
            scene.setSelectedGameObject(newObj)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Add new GameObject")
        }
        
        ImGui.sameLine()
        if (isLinked) {
            ImGui.pushStyleColor(ImGuiCol.Button, ImGui.getColorU32(ImGuiCol.ButtonActive))
        }
        if (ImGui.button(Icons.LINK)) {
            isLinked = !isLinked
        }
        if (isLinked) {
            ImGui.popStyleColor()
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip("Link selection to viewport")
        }
        
        ImGui.separator()

        val gameObjects = scene.gameObjectManager.gameObjects
        val filter = searchQuery.get()

        if (ImGui.beginTable("HierarchyTable", 3, ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.Resizable or ImGuiTableFlags.ScrollY)) {
            ImGui.tableSetupColumn("Name", ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn("Vis", ImGuiTableColumnFlags.WidthFixed, 24f)
            ImGui.tableSetupColumn("Lock", ImGuiTableColumnFlags.WidthFixed, 24f)
            // ImGui.tableHeadersRow() // Optional, skipping for cleaner look

            gameObjects.forEach { obj ->
                if (obj.parent == null) { // Only draw root objects
                    doTreeNode(obj, filter)
                }
            }
            ImGui.endTable()
        }
        
        // Handle global deletion input
        if (ImGui.isWindowFocused() && ImGui.isKeyPressed(GLFW_KEY_DELETE)) {
            sceneManager.currentScene?.let { scn ->
                scn.getSelectedGameObject()?.let { go ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(go, scn))
                }
            }
        }

        ImGui.end()
    }

    private fun doTreeNode(obj: GameObject, filter: String) {
        val matchesFilter = filter.isEmpty() || obj.name.contains(filter, ignoreCase = true)
        val hasMatchingChild = hasMatchingChild(obj, filter)

        if (!matchesFilter && !hasMatchingChild) return

        ImGui.pushID(obj.getUid())
        ImGui.tableNextRow()
        ImGui.tableNextColumn()

        var flags = ImGuiTreeNodeFlags.FramePadding or ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.SpanAvailWidth
        if (obj.children.isEmpty()) {
            flags = flags or ImGuiTreeNodeFlags.Leaf
        }
        if (obj == sceneManager.currentScene?.getSelectedGameObject()) {
            flags = flags or ImGuiTreeNodeFlags.Selected
        }
        
        if (filter.isNotEmpty() && hasMatchingChild) {
            ImGui.setNextItemOpen(true, imgui.flag.ImGuiCond.Always)
        }

        val nodeOpen = ImGui.treeNodeEx(obj.name, flags)

        if (ImGui.isItemClicked()) {
            sceneManager.currentScene?.setSelectedGameObject(obj)
        }
        
        if (ImGui.beginPopupContextItem()) {
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("lbl.delete")}")) {
                sceneManager.currentScene?.let { scn ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(obj, scn))
                }
            }
            ImGui.endPopup()
        }

        ImGui.tableNextColumn()
        
        // Visibility toggle
        val visIcon = if (obj.isVisible) Icons.EYE else Icons.EYE_SLASH
        val visColor = if (obj.isVisible) ImGui.getColorU32(ImGuiCol.Text) else ImGui.getColorU32(ImGuiCol.TextDisabled)
        ImGui.pushStyleColor(ImGuiCol.Text, visColor)
        
        // Transparent button background
        ImGui.pushStyleColor(ImGuiCol.Button, 0)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(ImGuiCol.FrameBgHovered))
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getColorU32(ImGuiCol.FrameBgActive))
        
        if (ImGui.button("$visIcon##vis_${obj.getUid()}")) {
            obj.isVisible = !obj.isVisible
        }
        ImGui.popStyleColor(4)
        
        ImGui.tableNextColumn()
        
        // Lock toggle
        val lockIcon = if (obj.isLocked) Icons.LOCK else Icons.UNLOCK
        val lockColor = if (obj.isLocked) ImGui.getColorU32(ImGuiCol.Text) else ImGui.getColorU32(ImGuiCol.TextDisabled)
        ImGui.pushStyleColor(ImGuiCol.Text, lockColor)
        ImGui.pushStyleColor(ImGuiCol.Button, 0)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(ImGuiCol.FrameBgHovered))
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getColorU32(ImGuiCol.FrameBgActive))
        
        if (ImGui.button("$lockIcon##lock_${obj.getUid()}")) {
            obj.isLocked = !obj.isLocked
        }
        ImGui.popStyleColor(4)

        if (nodeOpen) {
            obj.children.forEach { child ->
                doTreeNode(child, filter)
            }
            ImGui.treePop()
        }
        
        ImGui.popID()
    }
    
    private fun hasMatchingChild(obj: GameObject, filter: String): Boolean {
        if (filter.isEmpty()) return true
        for (child in obj.children) {
            if (child.name.contains(filter, ignoreCase = true)) return true
            if (hasMatchingChild(child, filter)) return true
        }
        return false
    }
}