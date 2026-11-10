package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.WindowHeader
import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.events.ViewportAction
import com.pafoid.skate.editor.systems.SelectionLogic
import com.pafoid.skate.editor.data.EditorShortcuts
import com.pafoid.skate.editor.data.EditorFocus
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.imgui.contextPopupId
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
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.ui.menus.ViewportContextMenu
import com.pafoid.skate.editor.systems.ClipboardService
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.events.SceneAction
import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiCond
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiPopupFlags
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

private const val SPECIAL_UID_SCENE_RENAME = -999

class SceneHierarchyWindow(
    val engine: Engine,
    private val clipboardService: ClipboardService,
    private val hasProject: () -> Boolean = { true },
) : EditorWindow("window.hierarchy", true) {
    private val sceneManager = engine.sceneManager
    private val eventSystem = engine.eventSystem
    private val stringManager = engine.stringManager

    private val searchQuery = ImString(256)
    private var isLinked = false

    private var editingObjUid: Int? = null
    private val editNameStr = ImString(128)
    private var focusEditInput = false

    private var navigationIndex = -1
    private val expandedNodes = mutableSetOf<Int>()
    private var flatObjectList: List<GameObject> = emptyList()

    /** Rows in the order they were drawn (open nodes only); last frame's order is the Shift-click range (EDT-74). */
    private var drawnRows = mutableListOf<GameObject>()
    private var lastDrawnRows: List<GameObject> = emptyList()

    /** Where the last plain or Ctrl click landed: one end of a Shift range. */
    private var selectionAnchor: GameObject? = null

    /**
     * A plain click on an object already in a multi-selection selects only it when the mouse is released without a
     * drag, so the whole selection can still be dragged onto a new parent.
     */
    private var pendingSingleClick: GameObject? = null

    /** The viewport's create items, reused by the empty-space menu (EDT-52B). */
    private val createMenu = ViewportContextMenu(stringManager, eventSystem)

    override val icon: String = Icons.SITEMAP
    override val relatedWindows = listOf("window.properties", "window.game_viewport")

    init {
        // F2 (EDT-10): rename the selected object inline, wherever the shortcut was pressed.
        eventSystem.subscribe<ViewportAction.StartRename> { event ->
            isOpen.set(true)
            startRename(event.gameObject)
        }
    }

    override fun imgui() {
        val scene = sceneManager.currentScene
        beginWindow(stringManager, closable = false)
        header(stringManager) { if (scene != null) toolbar(scene) }
        if (scene == null) {
            if (hasProject()) EmptyState.noScene(stringManager)
            else EmptyState.noProject(stringManager, eventSystem)
            ImGui.end()
            return
        }

        // Scene name header with rename button
        val sceneName = scene.name
        val headerLabel = "${Icons.FOLDER_OPEN} ${stringManager.getString("lbl.hierarchy.scene")}: $sceneName"
        ImGui.text(headerLabel)
        // Dropping an object on the scene header makes it top-level.
        if (ImGui.beginDragDropTarget()) {
            ImGui.acceptDragDropPayload<Int>("GAMEOBJECT_UID")?.let { uid ->
                engine.gameObjectManager.getGameObject(uid)?.let { eventSystem.publish(Reparent(it, scene)) }
            }
            ImGui.endDragDropTarget()
        }
        ImGui.sameLine(ImGui.getContentRegionAvailX() - 30f)
        if (ImGui.button("${Icons.EDIT}##RenameScene")) {
            startSceneRename(scene)
        }
        Tooltips.text(stringManager.getString("tooltip.hierarchy.rename_scene"))

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


        // Navigation-only shortcuts (window-focused)
        handleWindowNavigation()

        val filter = searchQuery.get()

        rebuildFlatList(scene)

        if (scene.children.none { it.isTopLevel }) {
            EmptyState.render(stringManager, Icons.CUBE, "empty.hierarchy", "empty.hierarchy.hint",
                listOf(EmptyState.Action("empty.hierarchy.create", Icons.PLUS) { eventSystem.publish(CreateEmpty(scene)) }))
        } else if (filter.isNotEmpty() && flatObjectList.none { it.name.contains(filter, ignoreCase = true) }) {
            EmptyState.noResults(stringManager) { searchQuery.set("") }
        } else if (ImGui.beginTable("HierarchyTable", 3, ImGuiTableFlags.BordersInnerH or ImGuiTableFlags.Resizable or ImGuiTableFlags.ScrollY)) {
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.name"), ImGuiTableColumnFlags.WidthStretch)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.visibility"), ImGuiTableColumnFlags.WidthFixed, 24f)
            ImGui.tableSetupColumn(stringManager.getString("tbl.hierarchy.lock"), ImGuiTableColumnFlags.WidthFixed, 24f)
            lastDrawnRows = drawnRows.toList()
            drawnRows.clear()

            scene.children.toList().forEach { obj ->
                if (obj.isTopLevel) {
                    doTreeNode(obj, filter)
                }
            }
            ImGui.endTable()
        }
        emptySpaceMenu(scene)

        ImGui.end()
    }

    /** Search, add and link-selection: the header strip's tools (EDT-51 §2.2). */
    private fun toolbar(scene: Scene) {
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - WindowHeader.trailingWidth(Icons.PLUS, Icons.LINK))
        ImGui.inputTextWithHint("##Search", "${Icons.SEARCH} ${stringManager.getString("lbl.hierarchy.search")}", searchQuery)
        ImGui.popItemWidth()

        ImGui.sameLine()
        if (ImGui.button(Icons.PLUS)) {
            eventSystem.publish(CreateEmpty(scene))
        }
        Tooltips.text(stringManager.getString("tooltip.hierarchy.add_gameobject"))

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
        Tooltips.text(stringManager.getString("tooltip.hierarchy.link_selection"))
    }

    /**
     * Right-click on empty space (EDT-51 §2.3): create at the top level, paste, expand/collapse, rename the scene.
     * Objects have their own menu, so this one does not open over them.
     */
    private fun emptySpaceMenu(scene: Scene) {
        if (!ImGui.beginPopupContextWindow("##hierarchy_empty_space", ImGuiPopupFlags.MouseButtonRight or ImGuiPopupFlags.NoOpenOverItems)) return
        if (ImGui.beginMenu(stringManager.label("context.hierarchy.create", "${Icons.PLUS} "))) {
            createMenu.renderCreateItems(scene)
            ImGui.endMenu()
        }
        if (stringManager.tipped(ImGui.menuItem("${Icons.PASTE} ${stringManager.getString("context.viewport.paste")}", EditorShortcuts.PASTE.display()), "context.viewport.paste", EditorShortcuts.PASTE)) {
            eventSystem.publish(PasteClipboard())
        }
        ImGui.separator()
        if (stringManager.tipped(ImGui.menuItem("${Icons.EXPAND} ${stringManager.getString("context.hierarchy.expand_all")}"), "context.hierarchy.expand_all")) {
            expandAll(scene)
        }
        if (stringManager.tipped(ImGui.menuItem("${Icons.ARROW_UP} ${stringManager.getString("context.hierarchy.collapse_all")}"), "context.hierarchy.collapse_all")) {
            collapseAll(scene)
        }
        ImGui.separator()
        if (stringManager.tipped(ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.hierarchy.rename_scene")}"), "context.hierarchy.rename_scene")) {
            startSceneRename(scene)
        }
        ImGui.endPopup()
    }

    /**
     * Handle navigation-only keyboard shortcuts (window-focused).
     * These only work when the hierarchy window is focused and are for tree navigation.
     */
    private fun handleWindowNavigation() {
        if (!ImGui.isWindowFocused()) return

        // Keys from the Key Bindings window; while focused, the hierarchy owns them (Home does not also reset the
        // camera, EDT-10).
        val up = EditorShortcuts.NAVIGATE_UP.combo()
        val down = EditorShortcuts.NAVIGATE_DOWN.combo()
        val first = EditorShortcuts.SELECT_FIRST.combo()
        val last = EditorShortcuts.SELECT_LAST.combo()
        EditorFocus.claim(up, down, first, last)

        val ctrlDown = ImGui.isKeyDown(GLFW_KEY_LEFT_CONTROL) || ImGui.isKeyDown(GLFW_KEY_RIGHT_CONTROL)

        // Navigate Up (only if not in Ctrl combination)
        if (ImGui.isKeyPressed(up.key) && !ctrlDown) {
            navigationIndex = (navigationIndex - 1).coerceAtLeast(0)
            selectObjectAtIndex(navigationIndex)
        }

        // Navigate Down (only if not in Ctrl combination)
        if (ImGui.isKeyPressed(down.key) && !ctrlDown) {
            val maxIndex = flatObjectList.size - 1
            navigationIndex = (navigationIndex + 1).coerceAtMost(maxIndex)
            selectObjectAtIndex(navigationIndex)
        }

        // Select First (Home)
        if (ImGui.isKeyPressed(first.key) && !ctrlDown) {
            navigationIndex = 0
            selectObjectAtIndex(0)
        }

        // Select Last (End)
        if (ImGui.isKeyPressed(last.key) && !ctrlDown) {
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
        scene.children.toList().forEach { obj ->
            if (obj.isTopLevel) {
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

    /** Ctrl toggles, Shift selects the range from the anchor, a plain click selects only [obj] (EDT-74). */
    private fun onRowClicked(obj: GameObject) {
        val scene = sceneManager.currentScene ?: return
        val io = ImGui.getIO()
        val ctrl = io.keyCtrl
        val shift = io.keyShift
        val current = scene.selectedGameObjects
        if (!ctrl && !shift && current.size > 1 && scene.isSelected(obj)) {
            pendingSingleClick = obj
        } else if (!ctrl && !shift) {
            eventSystem.publish(GameObjectSelected(obj))
        } else {
            eventSystem.publish(ViewportAction.MultiSelectionChanged(SelectionLogic.hierarchyClick(current, obj, lastDrawnRows, selectionAnchor, ctrl, shift)))
        }
        selectionAnchor = SelectionLogic.anchorAfter(selectionAnchor, obj, shift)
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

        drawnRows += obj
        ImGui.pushID(obj.uId)
        ImGui.tableNextRow()
        ImGui.tableNextColumn()

        var flags = ImGuiTreeNodeFlags.FramePadding or ImGuiTreeNodeFlags.OpenOnArrow or ImGuiTreeNodeFlags.SpanAvailWidth
        if (obj.children.isEmpty()) {
            flags = flags or ImGuiTreeNodeFlags.Leaf
        }
        if (sceneManager.currentScene?.isSelected(obj) == true) {
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
                onRowClicked(obj)
                navigationIndex = flatObjectList.indexOf(obj)
            }
            if (pendingSingleClick === obj && ImGui.isMouseReleased(0)) {
                pendingSingleClick = null
                if (ImGui.isItemHovered()) eventSystem.publish(GameObjectSelected(obj))
            }

            if (ImGui.beginDragDropSource()) {
                // Dragging a selected row moves the whole selection (the Reparent handler expands it).
                pendingSingleClick = null
                ImGui.setDragDropPayload("GAMEOBJECT_UID", obj.uId as Any)
                val dragged = SelectionLogic.targets(sceneManager.currentScene, obj)
                if (dragged.size > 1) ImGui.text(stringManager.getString("ctx.hierarchy.drag_tooltip_many", dragged.size))
                else ImGui.text(stringManager.getString("ctx.hierarchy.drag_tooltip", obj.name))
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
                    // Cycles and no-op moves are rejected by the Reparent handler (ReparentGameObjectCommand.isValid).
                    if (draggedObject != null && draggedObject !== obj) {
                        eventSystem.publish(Reparent(draggedObject, obj))
                    }
                }
                ImGui.endDragDropTarget()
            }
        }

        // Item menu (EDT-51 §2.3): create, main actions, clipboard, tree, then the destructive Delete last.
        if (ImGui.beginPopupContextItem(contextPopupId("hierarchy", obj.uId))) {
            if (ImGui.beginMenu(stringManager.label("context.hierarchy.create_empty_child", "${Icons.PLUS} "))) {
                if (stringManager.tipped(ImGui.menuItem(stringManager.getString("lbl.gameobject.empty")), "lbl.gameobject.empty")) {
                    eventSystem.publish(CreateEmptyChild(obj))
                }
                ImGui.endMenu()
            }
            com.pafoid.skate.editor.ui.menus.UiCreateMenu.render(stringManager, eventSystem, obj)

            ImGui.separator()

            if (stringManager.tipped(ImGui.menuItem("${Icons.EDIT} ${stringManager.getString("context.hierarchy.rename")}", EditorShortcuts.RENAME.display()), "context.hierarchy.rename", EditorShortcuts.RENAME)) {
                startRename(obj)
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.CLONE} ${stringManager.getString("context.hierarchy.duplicate")}", EditorShortcuts.DUPLICATE.display()), "context.hierarchy.duplicate", EditorShortcuts.DUPLICATE)) {
                eventSystem.publish(Duplicate(obj))
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.EYE} ${stringManager.getString("context.hierarchy.focus_in_viewport")}", EditorShortcuts.FOCUS.display()), "context.hierarchy.focus_in_viewport", EditorShortcuts.FOCUS)) {
                eventSystem.publish(GameObjectSelected(obj))
                eventSystem.publish(FocusSelected)
            }
            val lockLabel = if (obj.isLocked)
                "${Icons.UNLOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}"
            else
                "${Icons.LOCK} ${stringManager.getString("context.hierarchy.lock_unlock")}"
            if (stringManager.tipped(ImGui.menuItem(lockLabel, EditorShortcuts.TOGGLE_LOCK.display()), "context.hierarchy.lock_unlock", EditorShortcuts.TOGGLE_LOCK)) {
                val newLock = !obj.isLocked
                eventSystem.publish(ToggleLock(obj, newLock))
            }
            val visLabel = if (obj.isVisible)
                "${Icons.EYE_SLASH} ${stringManager.getString("context.hierarchy.visible_hidden")}"
            else
                "${Icons.EYE} ${stringManager.getString("context.hierarchy.visible_hidden")}"
            if (stringManager.tipped(ImGui.menuItem(visLabel, EditorShortcuts.TOGGLE_VISIBILITY.display()), "context.hierarchy.visible_hidden", EditorShortcuts.TOGGLE_VISIBILITY)) {
                val newVis = !obj.isVisible
                eventSystem.publish(ToggleVisibility(obj, newVis))
            }

            ImGui.separator()

            if (stringManager.tipped(ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.hierarchy.copy")}", EditorShortcuts.COPY.display()), "context.hierarchy.copy", EditorShortcuts.COPY)) {
                eventSystem.publish(ViewportAction.CopyClipboard(obj))
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.CUT} ${stringManager.getString("context.hierarchy.cut")}", EditorShortcuts.CUT.display()), "context.hierarchy.cut", EditorShortcuts.CUT)) {
                eventSystem.publish(ViewportAction.CutClipboard(obj))
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.PASTE} ${stringManager.getString("context.hierarchy.paste_as_child")}"), "context.hierarchy.paste_as_child")) {
                eventSystem.publish(PasteClipboard(obj))
            }

            ImGui.separator()

            if (stringManager.tipped(ImGui.menuItem("${Icons.EXPAND} ${stringManager.getString("context.hierarchy.expand_all")}"), "context.hierarchy.expand_all")) {
                expandAll(sceneManager.currentScene ?: return)
            }
            if (stringManager.tipped(ImGui.menuItem("${Icons.ARROW_UP} ${stringManager.getString("context.hierarchy.collapse_all")}"), "context.hierarchy.collapse_all")) {
                collapseAll(sceneManager.currentScene ?: return)
            }

            ImGui.separator()

            if (stringManager.tipped(ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.hierarchy.delete")}", EditorShortcuts.DELETE.display()), "context.hierarchy.delete", EditorShortcuts.DELETE)) {
                sceneManager.currentScene?.let { scn ->
                    eventSystem.publish(Delete(obj, scn))
                }
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
        Tooltips.text(stringManager.getString("tooltip.hierarchy.toggle_visibility"))
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
        Tooltips.text(stringManager.getString("tooltip.hierarchy.toggle_lock"))
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
        scene.children.toList().forEach { obj ->
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
