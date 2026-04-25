package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.commands.CreateGameObjectCommand
import com.pafoid.skate.editor.commands.DeleteGameObjectCommand
import com.pafoid.skate.editor.commands.LockToggleCommand
import com.pafoid.skate.editor.commands.RenameGameObjectCommand
import com.pafoid.skate.editor.commands.RenameSceneCommand
import com.pafoid.skate.editor.commands.ReparentGameObjectCommand
import com.pafoid.skate.editor.commands.VisibilityToggleCommand
import com.pafoid.skate.editor.imgui.IWindowWithScene
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.editor.systems.EditorInputHandler
import com.pafoid.skate.editor.systems.LoggerService
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.editor.systems.UndoRedoManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.ecs.scene.getSelectedGameObject
import com.pafoid.skate.engine.ecs.scene.setSelectedGameObject
import com.pafoid.skate.engine.events.GameObjectSelected
import com.pafoid.skate.engine.events.SceneRenamed
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

class SceneHierarchyWindow : IWindowWithScene, KoinComponent {

    private val sceneManager: SceneManager by inject()
    private val stringManager: StringManager by inject()
    private val undoRedoManager: UndoRedoManager by inject()
    private val clipboardService: ClipboardService by inject()
    private val logger: LoggerService by inject()
    private val eventSystem: EventSystem by inject()
    private val editorInputHandler: EditorInputHandler by inject()

    private val searchQuery = ImString(256)
    private var isLinked = false

    private var editingObjUid: Int? = null
    private val editNameStr = ImString(128)
    private var focusEditInput = false

    private var navigationIndex = -1
    private val expandedNodes = mutableSetOf<Int>()
    private var flatObjectList: List<GameObject> = emptyList()

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
            val newObj = GameObject(stringManager.getString("lbl.gameobject.new"))
            scene.gameObjectManager.addGameObject(newObj)
            scene.setSelectedGameObject(newObj)
            eventSystem.publish(GameObjectSelected(newObj))
            rebuildFlatList(scene)
            navigationIndex = flatObjectList.indexOf(newObj).coerceAtLeast(0)
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

        // Check for pending rename from global shortcut (F2 in EditorInputHandler)
        val pendingRenameUid = editorInputHandler.consumePendingRename()
        if (pendingRenameUid != null && editingObjUid == null) {
            val go = scene.gameObjectManager.getGameObject(pendingRenameUid)
            if (go != null) {
                startRename(go)
            }
        }

        // Navigation-only shortcuts (window-focused)
        handleWindowNavigation(scene)

        val filter = searchQuery.get()

        rebuildFlatList(scene)

        if (ImGui.beginTable("HierarchyTable", 3, ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.Resizable or ImGuiTableFlags.ScrollY)) {
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.name"), ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.visibility"), ImGuiTableColumnFlags.WidthFixed, 24f)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.lock"), ImGuiTableColumnFlags.WidthFixed, 24f)

            scene.gameObjectManager.gameObjects.toList().forEach { obj ->
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
    private fun handleWindowNavigation(scene: Scene) {
        if (!ImGui.isWindowFocused()) return

        val ctrlDown = ImGui.isKeyDown(GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW_KEY_RIGHT_CONTROL)

        // Navigate Up (only if not in Ctrl combination)
        if (ImGui.isKeyPressed(GLFW_KEY_UP) && !ctrlDown) {
            navigationIndex = (navigationIndex - 1).coerceAtLeast(0)
            selectObjectAtIndex(navigationIndex, scene)
        }

        // Navigate Down (only if not in Ctrl combination)
        if (ImGui.isKeyPressed(GLFW_KEY_DOWN) && !ctrlDown) {
            val maxIndex = flatObjectList.size - 1
            navigationIndex = (navigationIndex + 1).coerceAtMost(maxIndex)
            selectObjectAtIndex(navigationIndex, scene)
        }

        // Select First (Home)
        if (ImGui.isKeyPressed(GLFW_KEY_HOME)) {
            navigationIndex = 0
            selectObjectAtIndex(0, scene)
        }

        // Select Last (End)
        if (ImGui.isKeyPressed(GLFW_KEY_END)) {
            val maxIndex = flatObjectList.size - 1
            navigationIndex = maxIndex.coerceAtLeast(0)
            selectObjectAtIndex(navigationIndex, scene)
        }
    }

    private fun rebuildFlatList(scene: Scene) {
        flatObjectList = buildFlatList(scene)
    }

    private fun buildFlatList(scene: Scene): List<GameObject> {
        val result = mutableListOf<GameObject>()
        scene.gameObjectManager.gameObjects.toList().forEach { obj ->
            if (obj.parent == null) {
                flattenTreeNode(obj, result)
            }
        }
        return result
    }

    private fun flattenTreeNode(obj: GameObject, result: MutableList<GameObject>) {
        result.add(obj)
        if (expandedNodes.contains(obj.getUid()) || obj.children.isEmpty()) {
            obj.children.toList().forEach { child ->
                flattenTreeNode(child, result)
            }
        }
    }

    private fun selectObjectAtIndex(index: Int, scene: Scene) {
        if (index in flatObjectList.indices) {
            val go = flatObjectList[index]
            scene.setSelectedGameObject(go)
            eventSystem.publish(GameObjectSelected(go))
        }
    }

    private fun startRename(go: GameObject) {
        editingObjUid = go.getUid()
        editNameStr.set(go.name)
        focusEditInput = true
    }

    private fun cloneGameObject(go: GameObject): GameObject {
        val cloned = GameObject("${go.name}_clone")
        val originalTransform = go.getComponent<Transform>()
        val newTransform = Transform()
        originalTransform?.let { orig ->
            newTransform.copyFrom(orig)
        }
        newTransform.translation.x += 0.5f
        newTransform.translation.z += 0.5f
        cloned.addComponent(newTransform)
        return cloned
    }

    private fun doTreeNode(obj: GameObject, filter: String) {
        val matchesFilter = filter.isEmpty() || obj.name.contains(filter, ignoreCase = true)
        val hasMatchingChild = hasMatchingChild(obj, filter)

        if (!matchesFilter && !hasMatchingChild) return

        val currentIndex = flatObjectList.indexOf(obj)

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
                val newName = editNameStr.get()
                val oldName = obj.name
                if (newName != oldName) {
                    undoRedoManager.executeCommand(RenameGameObjectCommand(obj, newName, oldName))
                }
                editingObjUid = null
            } else if (ImGui.isItemDeactivated()) {
                if (ImGui.isKeyPressed(GLFW_KEY_ESCAPE)) {
                    editingObjUid = null
                } else {
                    val newName = editNameStr.get()
                    val oldName = obj.name
                    if (newName != oldName) {
                        undoRedoManager.executeCommand(RenameGameObjectCommand(obj, newName, oldName))
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
                sceneManager.currentScene?.setSelectedGameObject(obj)
                eventSystem.publish(GameObjectSelected(obj))
            } else if (ImGui.isItemClicked()) {
                sceneManager.currentScene?.setSelectedGameObject(obj)
                eventSystem.publish(GameObjectSelected(obj))
                navigationIndex = flatObjectList.indexOf(obj)
            }

            if (ImGui.beginDragDropSource()) {
                ImGui.setDragDropPayload("GAMEOBJECT_UID", obj.getUid() as Any)
                ImGui.text(obj.name)
                ImGui.endDragDropSource()
            }

            if (ImGui.beginDragDropTarget()) {
                val payload = ImGui.acceptDragDropPayload<Int>("GAMEOBJECT_UID")
                if (payload != null) {
                    val draggedUid = payload
                    val scene = sceneManager.currentScene
                    if (scene != null) {
                        val draggedObject = scene.gameObjectManager.getGameObject(draggedUid)
                        if (draggedObject != null && draggedObject != obj && !isChildOf(obj, draggedObject)) {
                            reparentGameObject(draggedObject, obj)
                        }
                    }
                }
                ImGui.endDragDropTarget()
            }
        }

        if (ImGui.beginPopupContextItem()) {
            if (ImGui.beginMenu("${Icons.PLUS} ${stringManager.getString("context.hierarchy.create_empty_child")}")) {
                if (ImGui.menuItem(stringManager.getString("lbl.gameobject.empty"))) {
                    val childObj = GameObject(stringManager.getString("lbl.gameobject.default"))
                    childObj.addComponent(Transform())
                    childObj.parent = obj
                    sceneManager.currentScene?.let { scn ->
                        undoRedoManager.executeCommand(CreateGameObjectCommand(childObj, scn))
                        rebuildFlatList(scn)
                    }
                }
                ImGui.endMenu()
            }

            ImGui.separator()

            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.hierarchy.duplicate")}")) {
                duplicateGameObject(obj)
                rebuildFlatList(sceneManager.currentScene ?: return)
            }

            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.hierarchy.copy")}")) {
                clipboardService.copy(obj)
            }
            if (ImGui.menuItem("${Icons.CUT} ${stringManager.getString("context.hierarchy.cut")}")) {
                clipboardService.copy(obj)
                sceneManager.currentScene?.let { scn ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(obj, scn))
                    rebuildFlatList(scn)
                }
            }
            if (ImGui.menuItem("${Icons.PASTE} ${stringManager.getString("context.hierarchy.paste_as_child")}")) {
                pasteAsChild(obj)
                rebuildFlatList(sceneManager.currentScene ?: return)
            }

            ImGui.separator()

            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.hierarchy.delete")}")) {
                sceneManager.currentScene?.let { scn ->
                    undoRedoManager.executeCommand(DeleteGameObjectCommand(obj, scn))
                    rebuildFlatList(scn)
                }
            }

            if (ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.hierarchy.rename")}")) {
                startRename(obj)
            }

            ImGui.separator()

            if (ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.hierarchy.focus_in_viewport")}")) {
                focusOnGameObject(obj)
            }

            ImGui.separator()

            if (ImGui.menuItem("${stringManager.getString("context.hierarchy.expand_all")}")) {
                expandAll(sceneManager.currentScene ?: return)
            }
            if (ImGui.menuItem("${stringManager.getString("context.hierarchy.collapse_all")}")) {
                collapseAll(sceneManager.currentScene ?: return)
            }

            ImGui.separator()

            val lockLabel = if (obj.isLocked)
                "${Icons.UNLOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}"
            else
                "${Icons.LOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}"
            if (ImGui.menuItem(lockLabel)) {
                val newLock = !obj.isLocked
                undoRedoManager.executeCommand(LockToggleCommand(obj, newLock))
            }

            val visLabel = if (obj.isVisible)
                "${Icons.EYE_SLASH} ${stringManager.getString("context.hierarchy.visible_hidden")}"
            else
                "${Icons.EYE} ${stringManager.getString("context.hierarchy.visible_hidden")}"
            if (ImGui.menuItem(visLabel)) {
                val newVis = !obj.isVisible
                undoRedoManager.executeCommand(VisibilityToggleCommand(obj, newVis))
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

        if (ImGui.button("$visIcon##vis_${obj.getUid()}")) {
            val newVis = !obj.isVisible
            undoRedoManager.executeCommand(VisibilityToggleCommand(obj, newVis))
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

        if (ImGui.button("$lockIcon##lock_${obj.getUid()}")) {
            val newLock = !obj.isLocked
            undoRedoManager.executeCommand(LockToggleCommand(obj, newLock))
        }
        if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.hierarchy.toggle_lock"))
        ImGui.popStyleColor(4)

        if (nodeOpen) {
            expandedNodes.add(obj.getUid())
            obj.children.toList().forEach { child ->
                doTreeNode(child, filter)
            }
            ImGui.treePop()
        } else if (!isEditing) {
            expandedNodes.remove(obj.getUid())
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

    private fun expandAll(scene: Scene?) {
        scene ?: return
        scene.gameObjectManager.gameObjects.toList().forEach { obj ->
            if (obj.children.isNotEmpty()) {
                expandedNodes.add(obj.getUid())
            }
        }
        rebuildFlatList(scene)
    }

    private fun collapseAll(scene: Scene?) {
        scene ?: return
        expandedNodes.clear()
        rebuildFlatList(scene)
    }

    private fun duplicateGameObject(gameObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
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
        scene.setSelectedGameObject(duplicated)
        eventSystem.publish(GameObjectSelected(duplicated))
        rebuildFlatList(scene)
        navigationIndex = flatObjectList.indexOf(duplicated).coerceAtLeast(0)
    }

    private fun pasteAsChild(parentObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val cloned = clipboardService.paste() ?: return
        cloned.name = "${cloned.name}_child"
        cloned.parent = parentObject
        cloned.getComponent<Transform>()?.translation?.set(0f, 0f, 0f)
        undoRedoManager.executeCommand(CreateGameObjectCommand(cloned, scene))
        rebuildFlatList(scene)
    }

    private fun focusOnGameObject(gameObject: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val transform = gameObject.getComponent<Transform>() ?: return
        val pos = transform.translation

        val offset = Vector3f(5f, 5f, 5f)
        scene.camera.position.set(Vector3f(pos).add(offset))
        scene.camera.lookAt(pos)
    }

    private fun reparentGameObject(child: GameObject, newParent: GameObject) {
        val oldParent = child.parent
        undoRedoManager.executeCommand(ReparentGameObjectCommand(child, newParent))

        val childTransform = child.getComponent<Transform>()
        val parentTransform = newParent.getComponent<Transform>()
        if (childTransform != null && parentTransform != null) {
            childTransform.translation.sub(parentTransform.translation)
        }

        logger.logEditor("Reparented ${child.name} to ${newParent.name}")
        sceneManager.currentScene?.let { rebuildFlatList(it) }
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
            val oldName = scene.name
            val command = RenameSceneCommand(scene, newName, oldName, sceneManager, eventSystem)
            undoRedoManager.executeCommand(command)
        }
        editingObjUid = null
    }

    init {
        // Subscribe to SceneRenamed to update UI if needed
        eventSystem.subscribe<SceneRenamed> { event ->
            logger.logEditor("Scene renamed: '${event.oldName}' -> '${event.newName}'")
        }
    }
}
