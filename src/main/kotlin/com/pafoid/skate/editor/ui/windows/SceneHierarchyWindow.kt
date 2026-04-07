package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.DeleteGameObjectCommand
import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.events.EventSystem
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SelectionCleared
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import org.joml.Vector3f
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_F2

class SceneHierarchyWindow : IWindowWithScene, KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val clipboardService: ClipboardService by inject()
    private val logger: LoggerService by inject()
    private val eventSystem: EventSystem by inject()

    private val searchQuery = ImString(256)
    private var isLinked = false
    
    private var editingObjUid: Int? = null
    private val editNameStr = ImString(128)
    private var focusEditInput = false

    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.hierarchy"))
        
        // Toolbar
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 60f)
        ImGui.inputTextWithHint("##Search", "${Icons.SEARCH} ${stringManager.getString("lbl.hierarchy.search")}", searchQuery)
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button(Icons.PLUS)) {
            val newObj = GameObject(stringManager.getString("lbl.gameobject.new"))
            scene.gameObjectManager.addGameObject(newObj)
            scene.setSelectedGameObject(newObj)
            eventSystem.publish(GameObjectSelected(newObj))
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.add_gameobject"))
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
            ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.link_selection"))
        }
        
        ImGui.separator()

        val filter = searchQuery.get()

        if (ImGui.beginTable("HierarchyTable", 3, ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.Resizable or ImGuiTableFlags.ScrollY)) {
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.name"), ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.visibility"), ImGuiTableColumnFlags.WidthFixed, 24f)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.lock"), ImGuiTableColumnFlags.WidthFixed, 24f)

            // Use toList() to snapshot the list — deleting objects during iteration causes ConcurrentModificationException
            scene.gameObjectManager.gameObjects.toList().forEach { obj ->
                if (obj.parent == null) { // Only draw root objects
                    doTreeNode(obj, filter)
                }
            }
            ImGui.endTable()
        }

        if (ImGui.isWindowFocused() && ImGui.isKeyPressed(GLFW_KEY_DELETE)) {
            sceneManager.currentScene?.let { scn ->
                scn.getSelectedGameObject()?.let { go ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(go, scn))
                    eventSystem.publish(SelectionCleared)
                }
            }
        }

        if (ImGui.isWindowFocused() && ImGui.isKeyPressed(GLFW_KEY_F2)) {
            sceneManager.currentScene?.getSelectedGameObject()?.let { go ->
                editingObjUid = go.getUid()
                editNameStr.set(go.name)
                focusEditInput = true
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
            ImGui.setNextItemOpen(true, ImGuiCond.Always)
        }

        val isEditing = editingObjUid == obj.getUid()
        val nodeOpen = if (isEditing) {
            val isOpen = ImGui.treeNodeEx("##${obj.getUid()}", flags)
            ImGui.sameLine()
            ImGui.pushItemWidth(-1f)
            
            if (focusEditInput) {
                ImGui.setKeyboardFocusHere()
                focusEditInput = false
            }

            val enterPressed = ImGui.inputText(
                "##rename_${obj.getUid()}",
                editNameStr,
                ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
            )
            
            if (enterPressed) {
                obj.name = editNameStr.get()
                editingObjUid = null
            } else if (ImGui.isItemDeactivated()) {
                if (ImGui.isKeyPressed(GLFW_KEY_ESCAPE)) {
                    editingObjUid = null
                } else {
                    obj.name = editNameStr.get()
                    editingObjUid = null
                }
            }
            
            ImGui.popItemWidth()
            isOpen
        } else {
            ImGui.treeNodeEx(obj.name, flags)
        }

        if (!isEditing) {
            if (ImGui.isItemHovered() && ImGui.isMouseDoubleClicked(0)) {
                editingObjUid = obj.getUid()
                editNameStr.set(obj.name)
                focusEditInput = true
                sceneManager.currentScene?.setSelectedGameObject(obj)
                eventSystem.publish(GameObjectSelected(obj))
            } else if (ImGui.isItemClicked()) {
                sceneManager.currentScene?.setSelectedGameObject(obj)
                eventSystem.publish(GameObjectSelected(obj))
            }
            
            // Drag and Drop Source - allow dragging GameObject to reparent
            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("GAMEOBJECT_UID", obj.getUid() as Any)
                ImGui.text(obj.name)
                ImGui.endDragDropSource()
            }
            
            // Drag and Drop Target - allow dropping GameObjects to reparent
            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<Int>("GAMEOBJECT_UID")
                if (payload != null) {
                    val draggedUid = payload
                    val scene = sceneManager.currentScene
                    if (scene != null) {
                        val draggedObject = scene.gameObjectManager.getGameObject(draggedUid)
                        // Don't allow dropping on self or children
                        if (draggedObject != null && draggedObject != obj && !isChildOf(obj, draggedObject)) {
                            reparentGameObject(draggedObject, obj)
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }
        }
        
        if (ImGui.beginPopupContextItem()) {
            // Create Empty Child
            if (ImGui.beginMenu("${Icons.PLUS} ${stringManager.getString("context.hierarchy.create_empty_child")}")) {
                if (ImGui.menuItem(stringManager.getString("lbl.gameobject.empty"))) {
                    val childObj = GameObject(stringManager.getString("lbl.gameobject.default"))
                    val parentTransform = obj.getComponent<Transform>()
                    childObj.addComponent(Transform())
                    childObj.parent = obj
                    sceneManager.currentScene?.let { scn ->
                        undoRedoManager.executeCommand(CreateGameObjectCommand(childObj, scn))
                    }
                }
                ImGui.endMenu()
            }
            
            ImGui.separator()
            
            // Duplicate
            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.hierarchy.duplicate")}")) {
                duplicateGameObject(obj)
            }
            
            // Copy/Cut/Paste
            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.hierarchy.copy")}")) {
                clipboardService.copy(obj)
            }
            if (ImGui.menuItem("${Icons.CUT} ${stringManager.getString("context.hierarchy.cut")}")) {
                clipboardService.copy(obj)
                sceneManager.currentScene?.let { scn ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(obj, scn))
                }
            }
            if (ImGui.menuItem("${Icons.PASTE} ${stringManager.getString("context.hierarchy.paste_as_child")}")) {
                pasteAsChild(obj)
            }
            
            ImGui.separator()
            
            // Delete
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.hierarchy.delete")}")) {
                sceneManager.currentScene?.let { scn ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(obj, scn))
                }
            }
            
            // Rename
            if (ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.hierarchy.rename")}")) {
                editingObjUid = obj.getUid()
                editNameStr.set(obj.name)
                focusEditInput = true
            }
            
            ImGui.separator()
            
            // Focus in Viewport
            if (ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.hierarchy.focus_in_viewport")}")) {
                focusOnGameObject(obj)
            }
            
            ImGui.separator()
            
            // Lock/Unlock toggle
            val lockLabel = if (obj.isLocked) 
                "${Icons.UNLOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}" 
            else 
                "${Icons.LOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}"
            if (ImGui.menuItem(lockLabel)) {
                obj.isLocked = !obj.isLocked
            }
            
            // Visible/Hidden toggle
            val visLabel = if (obj.isVisible) 
                "${Icons.EYE_SLASH} ${stringManager.getString("context.hierarchy.visible_hidden")}" 
            else 
                "${Icons.EYE} ${stringManager.getString("context.hierarchy.visible_hidden")}"
            if (ImGui.menuItem(visLabel)) {
                obj.isVisible = !obj.isVisible
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
        if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.toggle_visibility"))
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
        if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.toggle_lock"))
        ImGui.popStyleColor(4)

        if (nodeOpen) {
            // Use toList() to snapshot the list — deleting objects during iteration causes ConcurrentModificationException
            obj.children.toList().forEach { child ->
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
    
    private fun duplicateGameObject(gameObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
        // Create a new GameObject with copied transform
        val duplicated = GameObject("${gameObject.name}_clone")
        val originalTransform = gameObject.getComponent<Transform>()
        val newTransform = Transform()
        originalTransform?.let { orig ->
            newTransform.copyFrom(orig)
        }
        newTransform.translation.x += 0.5f
        newTransform.translation.z += 0.5f

        duplicated.addComponent(newTransform)
        undoRedoManager.executeCommand(CreateGameObjectCommand(duplicated, scene))
    }

    private fun pasteAsChild(parentObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val cloned = clipboardService.paste() ?: return
        cloned.name = "${cloned.name}_child"
        cloned.parent = parentObject
        cloned.getComponent<Transform>()?.translation?.set(0f, 0f, 0f)
        undoRedoManager.executeCommand(CreateGameObjectCommand(cloned, scene))
    }
    
    private fun focusOnGameObject(gameObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val transform = gameObject.getComponent<Transform>() ?: return
        val pos = transform.translation
        
        // Move camera to look at the object from a reasonable distance
        val offset = Vector3f(5f, 5f, 5f)
        scene.camera.position.set(Vector3f(pos).add(offset))
        scene.camera.lookAt(pos)
    }
    
    private fun reparentGameObject(child: GameObject, newParent: GameObject) {
        val oldParent = child.parent
        child.parent = newParent
        
        // Update transform to maintain world space
        val childTransform = child.getComponent<Transform>()
        val parentTransform = newParent.getComponent<Transform>()
        if (childTransform != null && parentTransform != null) {
            // Adjust child's local transform to maintain world position
            childTransform.translation.sub(parentTransform.translation)
        }
        
        logger.logEditor("Reparented ${child.name} to ${newParent.name}")
    }
    
    private fun isChildOf(potentialParent: GameObject, child: GameObject): Boolean {
        var current = child.parent
        while (current != null) {
            if (current == potentialParent) return true
            current = current.parent
        }
        return false
    }
}