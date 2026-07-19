package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ViewportAction.CreateEmpty
import com.pafoid.skate.editor.events.ViewportAction.CreateEmptyChild
import com.pafoid.skate.editor.events.ViewportAction.Delete
import com.pafoid.skate.editor.events.ViewportAction.Duplicate
import com.pafoid.skate.editor.events.ViewportAction.FocusSelected
import com.pafoid.skate.editor.events.ViewportAction.GameObjectSelected
import com.pafoid.skate.editor.events.ViewportAction.PasteClipboard
import com.pafoid.skate.editor.events.ViewportAction.RenameGameObject
import com.pafoid.skate.editor.events.ViewportAction.Reparent
import com.pafoid.skate.editor.events.ViewportAction.ToggleLock
import com.pafoid.skate.editor.events.ViewportAction.ToggleVisibility
import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.core.logEditor
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.SceneAction
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiTableColumnFlags
import imgui.flag.ImGuiTableFlags
import imgui.flag.ImGuiTreeNodeFlags
import imgui.type.ImString
import org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN
import org.lwjgl.glfw.GLFW.GLFW_KEY_END
import org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
import org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE
import org.lwjgl.glfw.GLFW.GLFW_KEY_HOME
import org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL
import org.lwjgl.glfw.GLFW.GLFW_KEY_UP

/**
 * Special UID constant for scene rename editing (negative to avoid collision with real GameObject UIDs).
 */
private const val SPECIAL_UID_SCENE_RENAME = -999

class SceneHierarchyWindow(
    private val engine: Engine,
    private val stringManager: StringManager,
    private val clipboardService: ClipboardService,
    private val logger: LoggerService,
    private val eventSystem: EventSystem,
) : IWindowWithScene {
    private val sceneManager = engine.sceneManager

    private val searchQuery = ImString(256)
    private var isLinked = false

    private var editingObjUid: Int? = null
    private val editNameStr = ImString(128)
    private var focusEditInput = false

    private var navigationIndex = -1
    private val expandedNodes = mutableSetOf<Int>()
    private var flatObjectList: List<GameObject> = emptyList()

    init {
        // Subscribe to SceneRenamed to update UI if needed
        eventSystem.subscribe<SceneAction.Renamed> { event ->
            logger.logEditor("Scene renamed: '${event.oldName}' -> '${event.newName}'")
        }
    }

    override fun imgui(scene: Scene) {
        ImGui.begin(stringManager.getString("window.hierarchy"))

        // Scene name header with rename button
        val sceneName = scene.name
        val headerLabel = "${Icons.FOLDER_OPEN} ${stringManager.getString("lbl.hierarchy.scene")}: $sceneName"
        ImGui.text(headerLabel)
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 30f)
        if (ImGui.button("${Icons.EDIT}##RenameScene")) {
            startSceneRename(scene)
        }
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.rename_scene"))
        }

        // Inline scene rename input
        if (editingObjUid == SPECIAL_UID_SCENE_RENAME) {
            ImGui.setKeyboardFocusHere()
            ImGui.inputText("##RenameSceneInput", editNameStr, ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll)
            if (ImGui.isItemFocused() || focusEditInput) {
                ImGui.setKeyboardFocusHere()
                focusEditInput = false
            }
            if (ImGui.isKeyPressed(GLFW_KEY_ESCAPE)) {
                editingObjUid = null
            }
            if (ImGui.isKeyPressed(GLFW_KEY_ENTER) || ImGui.isItemActivated()) {
                finishSceneRename(scene)
            }
        }

        ImGui.separator()

        // Toolbar
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - 60f)
        ImGui.inputTextWithHint("##Search", "${Icons.SEARCH} ${stringManager.getString("lbl.hierarchy.search")}", searchQuery)
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button(Icons.PLUS)) {
            eventSystem.publish(CreateEmpty(scene))
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

        // TODO: fix
        // Check for pending rename from global shortcut (F2 in EditorInputHandler)
        /*val pendingRenameUid = editorInputHandler.consumePendingRename()
        if (pendingRenameUid != null && editingObjUid == null) {
            val go = scene.gameObjectManager.getGameObject(pendingRenameUid)
            if (go != null) {
                startRename(go)
            }
        }*/

        // Navigation-only shortcuts (window-focused)
        handleWindowNavigation()

        val filter = searchQuery.get()

        rebuildFlatList(scene)

        if (ImGui.beginTable("HierarchyTable", 3, ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.Resizable or ImGuiTableFlags.ScrollY)) {
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.name"), ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.visibility"), ImGuiTableColumnFlags.WidthFixed, 24f)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.lock"), ImGuiTableColumnFlags.WidthFixed, 24f)

            scene.gameObjects.toList().forEach { obj ->
                if (obj.parent == null) {
                    doTreeNode(obj, filter)
                }
            }
            ImGui.endTable()
        }

        ImGui.end()
    }

    /**
     * Handle navigation-only keyboard shortcuts (window-focused).
     * These only work when the hierarchy window is focused and are for tree navigation.
     */
    private fun handleWindowNavigation() {
        if (!ImGui.isWindowFocused()) return

        val ctrlDown = ImGui.isKeyDown(GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW_KEY_RIGHT_CONTROL)

        // Navigate Up (only if not in Ctrl combination)
        if (ImGui.isKeyPressed(GLFW_KEY_UP) && !ctrlDown) {
            navigationIndex = (navigationIndex - 1).coerceAtLeast(0)
            selectObjectAtIndex(navigationIndex)
        }

        // Navigate Down (only if not in Ctrl combination)
        if (ImGui.isKeyPressed(GLFW_KEY_DOWN) && !ctrlDown) {
            val maxIndex = flatObjectList.size - 1
            navigationIndex = (navigationIndex + 1).coerceAtMost(maxIndex)
            selectObjectAtIndex(navigationIndex)
        }

        // Select First (Home)
        if (ImGui.isKeyPressed(GLFW_KEY_HOME)) {
            navigationIndex = 0
            selectObjectAtIndex(0)
        }

        // Select Last (End)
        if (ImGui.isKeyPressed(GLFW_KEY_END)) {
            val maxIndex = flatObjectList.size - 1
            navigationIndex = maxIndex.coerceAtLeast(0)
            selectObjectAtIndex(navigationIndex)
        }
    }

    private fun rebuildFlatList(scene: Scene) {
        flatObjectList = buildFlatList(scene)
    }

    private fun buildFlatList(scene: Scene): List<GameObject> {
        val result = mutableListOf<GameObject>()
        scene.gameObjects.toList().forEach { obj ->
            if (obj.parent == null) {
                flattenTreeNode(obj, result)
            }
        }
        return result
    }

    private fun flattenTreeNode(obj: GameObject, result: MutableList<GameObject>) {
        result.add(obj)
        if (expandedNodes.contains(obj.uId) || obj.children.isEmpty()) {
            obj.children.toList().forEach { child ->
                flattenTreeNode(child, result)
            }
        }
    }

    private fun selectObjectAtIndex(index: Int) {
        if (index in flatObjectList.indices) {
            val go = flatObjectList[index]
            eventSystem.publish(GameObjectSelected(go))
        }
    }

    private fun startRename(go: GameObject) {
        editingObjUid = go.uId
        editNameStr.set(go.name)
        focusEditInput = true
    }

    private fun doTreeNode(obj: GameObject, filter: String) {
        val matchesFilter = filter.isEmpty() || obj.name.contains(filter, ignoreCase = true)
        val hasMatchingChild = hasMatchingChild(obj, filter)

        if (!matchesFilter && !hasMatchingChild) return

        ImGui.pushID(obj.uId)
        ImGui.tableNextRow()
        ImGui.tableNextColumn()

        var flags = ImGuiTreeNodeFlags.FramePadding or ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.SpanAvailWidth
        if (obj.children.isEmpty()) {
            flags = flags or ImGuiTreeNodeFlags.Leaf
        }
        if (obj == sceneManager.currentScene?.selectedGameObject) {
            flags = flags or ImGuiTreeNodeFlags.Selected
        }

        if (filter.isNotEmpty() && hasMatchingChild) {
            ImGui.setNextItemOpen(true, ImGuiCond.Always)
        }

        val isEditing = editingObjUid == obj.uId
        val nodeOpen = if (isEditing) {
            val isOpen = ImGui.treeNodeEx("##${obj.uId}", flags)
            ImGui.sameLine()
            ImGui.pushItemWidth(-1f)

            if (focusEditInput) {
                ImGui.setKeyboardFocusHere()
                focusEditInput = false
            }

            val enterPressed = ImGui.inputText(
                "##rename_${obj.uId}",
                editNameStr,
                ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
            )

            if (enterPressed) {
                val newName = editNameStr.get()
                val oldName = obj.name
                if (newName != oldName) {
                    eventSystem.publish(RenameGameObject(obj, newName))
                }
                editingObjUid = null
            } else if (ImGui.isItemDeactivated()) {
                if (ImGui.isKeyPressed(GLFW_KEY_ESCAPE)) {
                    editingObjUid = null
                } else {
                    val newName = editNameStr.get()
                    val oldName = obj.name
                    if (newName != oldName) {
                        eventSystem.publish(RenameGameObject(obj, newName))
                    }
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
                startRename(obj)
                eventSystem.publish(GameObjectSelected(obj))
            } else if (ImGui.isItemClicked()) {
                eventSystem.publish(GameObjectSelected(obj))
                navigationIndex = flatObjectList.indexOf(obj)
            }

            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("GAMEOBJECT_UID", obj.uId as Any)
                ImGui.text(stringManager.getString("ctx.hierarchy.drag_tooltip", obj.name))
                ImGui.endDragDropSource()
            }

            if (ImGui.beginDragDropTarget()) {
                // Visual feedback: colored separator indicates the valid drop zone
                ImGui.pushStyleColor(ImGuiCol.Separator, ImGui.getColorU32(ImGuiCol.DragDropTarget))
                ImGui.separator()
                ImGui.popStyleColor()
                val payload = ImGui.acceptDragDropPayload<Int>("GAMEOBJECT_UID")
                if (payload != null) {
                    val draggedObject = engine.gameObjectManager.getGameObject(payload)
                    if (draggedObject != null && draggedObject != obj && !isChildOf(obj, draggedObject)) {
                        eventSystem.publish(Reparent(draggedObject, obj))
                    }
                }
                ImGui.endDragDropTarget()
            }
        }

        if (ImGui.beginPopupContextItem()) {
            if (ImGui.beginMenu("${Icons.PLUS} ${stringManager.getString("context.hierarchy.create_empty_child")}")) {
                if (ImGui.menuItem(stringManager.getString("lbl.gameobject.empty"))) {
                    eventSystem.publish(CreateEmptyChild(obj))
                }
                ImGui.endMenu()
            }

            ImGui.separator()

            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.hierarchy.duplicate")}")) {
                eventSystem.publish(Duplicate(obj))
            }

            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.hierarchy.copy")}")) {
                clipboardService.copy(obj)
            }
            if (ImGui.menuItem("${Icons.CUT} ${stringManager.getString("context.hierarchy.cut")}")) {
                clipboardService.copy(obj)
                sceneManager.currentScene?.let { scn ->
                    eventSystem.publish(Delete(obj, scn))
                }
            }
            if (ImGui.menuItem("${Icons.PASTE} ${stringManager.getString("context.hierarchy.paste_as_child")}")) {
                eventSystem.publish(PasteClipboard(obj))
            }

            ImGui.separator()

            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.hierarchy.delete")}")) {
                sceneManager.currentScene?.let { scn ->
                    eventSystem.publish(Delete(obj, scn))
                }
            }

            if (ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.hierarchy.rename")}")) {
                startRename(obj)
            }

            ImGui.separator()

            if (ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.hierarchy.focus_in_viewport")}")) {
                eventSystem.publish(GameObjectSelected(obj))
                eventSystem.publish(FocusSelected)
            }

            ImGui.separator()

            if (ImGui.menuItem(stringManager.getString("context.hierarchy.expand_all"))) {
                expandAll(sceneManager.currentScene ?: return)
            }
            if (ImGui.menuItem(stringManager.getString("context.hierarchy.collapse_all"))) {
                collapseAll(sceneManager.currentScene ?: return)
            }

            ImGui.separator()

            val lockLabel = if (obj.isLocked)
                "${Icons.UNLOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}"
            else
                "${Icons.LOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}"
            if (ImGui.menuItem(lockLabel)) {
                val newLock = !obj.isLocked
                eventSystem.publish(ToggleLock(obj, newLock))
            }

            val visLabel = if (obj.isVisible)
                "${Icons.EYE_SLASH} ${stringManager.getString("context.hierarchy.visible_hidden")}"
            else
                "${Icons.EYE} ${stringManager.getString("context.hierarchy.visible_hidden")}"
            if (ImGui.menuItem(visLabel)) {
                val newVis = !obj.isVisible
                eventSystem.publish(ToggleVisibility(obj, newVis))
            }

            ImGui.endPopup()
        }

        ImGui.tableNextColumn()

        val visIcon = if (obj.isVisible) Icons.EYE else Icons.EYE_SLASH
        val visColor = if (obj.isVisible) ImGui.getColorU32(ImGuiCol.Text) else ImGui.getColorU32(ImGuiCol.TextDisabled)
        ImGui.pushStyleColor(ImGuiCol.Text, visColor)

        ImGui.pushStyleColor(ImGuiCol.Button, 0)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(ImGuiCol.FrameBgHovered))
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getColorU32(ImGuiCol.FrameBgActive))

        if (ImGui.button("$visIcon##vis_${obj.uId}")) {
            val newVis = !obj.isVisible
            eventSystem.publish(ToggleVisibility(obj, newVis))
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.toggle_visibility"))
        ImGui.popStyleColor(4)

        ImGui.tableNextColumn()

        val lockIcon = if (obj.isLocked) Icons.LOCK else Icons.UNLOCK
        val lockColor = if (obj.isLocked) ImGui.getColorU32(ImGuiCol.Text) else ImGui.getColorU32(ImGuiCol.TextDisabled)
        ImGui.pushStyleColor(ImGuiCol.Text, lockColor)
        ImGui.pushStyleColor(ImGuiCol.Button, 0)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, ImGui.getColorU32(ImGuiCol.FrameBgHovered))
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, ImGui.getColorU32(ImGuiCol.FrameBgActive))

        if (ImGui.button("$lockIcon##lock_${obj.uId}")) {
            val newLock = !obj.isLocked
            eventSystem.publish(ToggleLock(obj, newLock))
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.toggle_lock"))
        ImGui.popStyleColor(4)

        if (nodeOpen) {
            expandedNodes.add(obj.uId)
            obj.children.toList().forEach { child ->
                doTreeNode(child, filter)
            }
            ImGui.treePop()
        } else if (!isEditing) {
            expandedNodes.remove(obj.uId)
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

    private fun expandAll(scene: Scene) {
        scene.gameObjects.toList().forEach { obj ->
            if (obj.children.isNotEmpty()) {
                expandedNodes.add(obj.uId)
            }
        }
        rebuildFlatList(scene)
    }

    private fun collapseAll(scene: Scene?) {
        scene ?: return
        expandedNodes.clear()
        rebuildFlatList(scene)
    }

    private fun isChildOf(potentialParent: GameObject, child: GameObject): Boolean {
        var current = child.parent
        while (current != null) {
            if (current == potentialParent) return true
            current = current.parent
        }
        return false
    }

    // Scene rename helpers
    private fun startSceneRename(scene: Scene) {
        editingObjUid = SPECIAL_UID_SCENE_RENAME
        editNameStr.set(scene.name)
        focusEditInput = true
    }

    private fun finishSceneRename(scene: Scene) {
        val newName = editNameStr.get().trim()
        if (newName.isNotBlank() && newName != scene.name) {
            eventSystem.publish(SceneAction.RenameRequested(scene, newName))
        }
        editingObjUid = null
    }
}
