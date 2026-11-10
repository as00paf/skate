package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.imgui.Tooltips
import com.pafoid.skate.editor.events.ComponentFieldAction
import com.pafoid.skate.editor.events.FileSystemEvent
import com.pafoid.skate.editor.imgui.EmptyState
import com.pafoid.skate.editor.imgui.tippedWith
import com.pafoid.skate.editor.imgui.tipped
import com.pafoid.skate.editor.imgui.label
import com.pafoid.skate.editor.events.ViewportAction.AddComponent
import com.pafoid.skate.editor.events.ViewportAction.RemoveComponent
import com.pafoid.skate.editor.events.ViewportAction.RenameComponent
import com.pafoid.skate.editor.events.ViewportAction.RenameGameObject
import com.pafoid.skate.editor.events.ViewportAction.SetComponentEnabled
import com.pafoid.skate.editor.events.ViewportAction.SetGameObjectEnabled
import com.pafoid.skate.editor.imgui.EditorWindow
import com.pafoid.skate.editor.imgui.EditorModal
import com.pafoid.skate.editor.imgui.FooterButton
import com.pafoid.skate.editor.imgui.ImGuiIds
import com.pafoid.skate.editor.imgui.MImGui
import com.pafoid.skate.editor.imgui.components.MultiEdit
import com.pafoid.skate.editor.imgui.components.imgui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.Scene
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiPopupFlags
import imgui.type.ImBoolean
import imgui.type.ImString

class PropertiesWindow(
    private val engine: Engine,
    private val hasProject: () -> Boolean = { true },
    /** Import settings of project files selected in the Project window (EDT-66). */
    private val fileInspectors: List<FileInspector> = emptyList(),
) : EditorWindow("window.properties", true) {
    private val stringManager = engine.stringManager
    private val eventSystem = engine.eventSystem
    private val sceneManager = engine.sceneManager

    private val searchString = ImString(128)

    private val removal = ComponentRemovalConfirmation()
    private val removalModal = RemoveComponentModal()

    /** Set by the empty-space menu; the Add Component popup opens in the window's own ID scope. */
    private var openAddComponent = false

    override val icon: String = Icons.SLIDERS
    override val relatedWindows = listOf("window.hierarchy", "window.game_viewport", "window.project")

    private val inspectedFile = InspectedFile()

    init {
        eventSystem.subscribe<FileSystemEvent.FileSelected> { event ->
            val file = java.io.File(event.path)
            if (fileInspectors.any { it.handles(file) }) inspectedFile.select(file, sceneManager.currentScene?.selectedGameObject)
            else inspectedFile.clear()
        }
    }

    override fun imgui() {
        val go = sceneManager.currentScene?.selectedGameObject ?: sceneManager.currentScene

        beginWindow(stringManager)
        header(stringManager)
        val file = inspectedFile.current(sceneManager.currentScene?.selectedGameObject)
        val inspector = file?.let { f -> fileInspectors.firstOrNull { it.handles(f) } }
        if (file != null && inspector != null) {
            inspector.render(file)
            ImGui.end()
            return
        }
        val selection = sceneManager.currentScene?.selectedGameObjects.orEmpty()
        if (selection.size > 1) {
            multiSelection(selection)
            ImGui.end()
            return
        }
        MultiEdit.clear()
        when {
            go != null -> {}
            hasProject() -> EmptyState.noScene(stringManager)
            else -> EmptyState.noProject(stringManager, eventSystem)
        }
        go?.let { go ->
            if (go !is Scene) {
                enabledCheckbox(go)
                ImGui.spacing()
            }
            objectName(go)
            // No selection: the inspector shows the scene itself; say so (EDT-10).
            if (go is Scene) MImGui.textDisabled("${Icons.INFO} ${stringManager.getString("empty.no_selection")}")
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            searchBar()
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            val components = go.components.filter {
                it.name.contains(
                    searchString.get(),
                    true
                ) || it.javaClass.simpleName.contains(searchString.get())
            }
            components.sortedBy { it.javaClass.simpleName }.forEach { component ->
                renderComponentWithContextMenu(go, component)
            }

            emptySpaceMenu()
            contextualMenu(go)
        }
        removeComponentModal(go)

        ImGui.spacing()
        ImGui.end()
    }

    /**
     * EDT-74: several objects selected. Only the components every one of them has are shown, drawn from the primary
     * object; an edit is copied to the same component of every selected object ([MultiEdit]), as one undo step.
     */
    private fun multiSelection(selection: List<GameObject>) {
        MultiEdit.inspect(selection)
        ImGui.text(stringManager.getString("properties.multi.count", selection.size))
        MImGui.textDisabled("${Icons.INFO} ${stringManager.getString("properties.multi.hint")}")
        ImGui.spacing()
        ImGui.separator()
        ImGui.spacing()
        val shared = MultiEdit.shared(selection)
        if (shared.isEmpty()) {
            EmptyState.nothingYet(stringManager, Icons.SLIDERS, "properties.multi.none")
            return
        }
        shared.forEach { component ->
            if (ImGui.collapsingHeader(component.name)) {
                component.imgui(engine)
                ImGui.spacing()
            }
        }
    }

    private fun renderComponentWithContextMenu(go: GameObject, component: Component) {
        val headerLabel = component.name
        
        if (ImGui.collapsingHeader(headerLabel)) {
            enabledCheckbox(component)
            ImGui.spacing()
            componentName(component)
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            component.imgui(engine)
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
        }

        // Component menu (EDT-51 §2.3): main actions, then the destructive Remove last, behind the same confirmation
        // as the trash button. Copy is not implemented yet, so it is grayed out instead of doing nothing.
        if (ImGui.beginPopupContextItem("${component.javaClass.simpleName}_context")) {
            if (stringManager.tipped(ImGui.menuItem("${Icons.ARROW_ROTATE} ${stringManager.getString("context.properties.reset_to_default")}"), "context.properties.reset_to_default")) {
                eventSystem.publish(ComponentFieldAction.Reset(component))
            }
            stringManager.tipped(ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.properties.copy_component")}", null, false, false), "context.properties.copy_component")
            ImGui.separator()
            if (stringManager.tipped(ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.properties.remove_component")}"), "context.properties.remove_component")) {
                removal.request(component)
            }
            ImGui.endPopup()
        }
    }
    
    private fun enabledCheckbox(go: GameObject) {
        val isEnabled = ImBoolean(go.isEnabled)
        if (ImGui.checkbox("##enabled_checkbox" + go.uId, isEnabled)) {
            eventSystem.publish(SetGameObjectEnabled(go, isEnabled.get()))
        }

        ImGui.sameLine()
        ImGui.text(stringManager.getString("lbl.systems.enabled"))
    }

    private fun objectName(go: GameObject) {
        ImGui.text(stringManager.getString("lbl.name"))
        ImGui.sameLine()
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())

        val name = ImString(go.name, 128)
        val flags = ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
        if (ImGui.inputText("##name_input" + go.uId, name, flags)) {
            eventSystem.publish(RenameGameObject(go, name.get()))
        }
        ImGui.popItemWidth()
    }

    private fun searchBar() {
        ImGui.text(stringManager.getString("lbl.components"))
        ImGui.spacing()

        // Search bar
        val buttonSize = ImGui.getFrameHeight()
        val spacing = ImGui.getStyle().itemSpacingX
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX() - buttonSize - spacing)
        val flags = ImGuiInputTextFlags.AutoSelectAll
        ImGui.inputTextWithHint("##search_input","${Icons.SEARCH} ${stringManager.getString("lbl.search")}...", searchString, flags)
        ImGui.popItemWidth()
        ImGui.sameLine()
        if(ImGui.button(Icons.PLUS, buttonSize, buttonSize)) {
            ImGui.openPopup("add_component_popup")
        }
        Tooltips.text(stringManager.getString("tooltip.properties.add_component"))
    }

    /** Right-click on empty space (EDT-51 §2.3): add a component. */
    private fun emptySpaceMenu() {
        if (ImGui.beginPopupContextWindow("##properties_empty_space", ImGuiPopupFlags.MouseButtonRight or ImGuiPopupFlags.NoOpenOverItems)) {
            if (stringManager.tipped(ImGui.menuItem("${Icons.PLUS} ${stringManager.getString("context.properties.add_component")}"), "context.properties.add_component")) {
                openAddComponent = true
            }
            ImGui.endPopup()
        }
        if (openAddComponent) {
            openAddComponent = false
            ImGui.openPopup("add_component_popup")
        }
    }

    private fun contextualMenu(go: GameObject) {
        if (ImGui.beginPopup("add_component_popup")) {
            ComponentType.entries.forEach { type ->
                if (searchString.get().isEmpty() || type.toString().contains(searchString.get(), ignoreCase = true)) {
                    if (go.getComponent(type) == null) {
                        if (ImGui.menuItem(type.toString())) {
                            eventSystem.publish(AddComponent(go, type))
                            ImGui.closeCurrentPopup()
                        }
                    }
                }
            }
            ImGui.endPopup()
        }
    }

    fun enabledCheckbox(cmp: Component) {
        val isEnabled = ImBoolean(cmp.enabled)
        if (ImGui.checkbox("##cmp_enabled_checkbox" + cmp.uId, isEnabled)) {
            eventSystem.publish(SetComponentEnabled(cmp, isEnabled.get()))
        }

        ImGui.sameLine()
        ImGui.text(stringManager.getString("lbl.systems.enabled"))
        ImGui.sameLine()
        val buttonSize = ImGui.getFrameHeight()
        if (stringManager.tippedWith(ImGui.button(Icons.TRASH, buttonSize, buttonSize), "tooltip.properties.remove_component")) {
            removal.request(cmp)
        }
    }

    /**
     * The one Remove-component confirmation (EDT-49), on the standard modal (EDT-52B). It used to be submitted inside
     * every expanded component with a shared ID, so it was drawn once per component (duplicated content) and could
     * confirm the wrong component. [ComponentRemovalConfirmation] keeps the request; the modal only shows it.
     */
    private fun removeComponentModal(inspected: GameObject?) {
        if (removal.consumeOpenRequest()) removalModal.open()
        removalModal.inspected = inspected
        removalModal.render()
        removal.sync(inspected, ImGui.isPopupOpen(ImGuiIds.stable("", REMOVE_TITLE_KEY)))
    }

    private inner class RemoveComponentModal : EditorModal(stringManager, Icons.TRASH) {
        var inspected: GameObject? = null

        override val titleKey = REMOVE_TITLE_KEY

        override fun content() {
            val target = removal.pending
            val on = inspected
            if (target == null || on == null) {
                // The request was dropped (undo, selection change): nothing left to confirm.
                ImGui.closeCurrentPopup()
                return
            }
            ImGui.pushTextWrapPos(ImGui.getFontSize() * 28f)
            ImGui.textWrapped(stringManager.getString("lbl.hierarchy.remove_component_named", target.name, on.name))
            ImGui.popTextWrapPos()
        }

        override fun buttons() = listOf(
            FooterButton("btn.cancel", FooterButton.Role.CANCEL) { removal.cancel() },
            FooterButton("context.properties.remove_component", FooterButton.Role.DANGER) {
                removal.confirm(inspected)?.let { eventSystem.publish(RemoveComponent(it)) }
            },
        )
    }

    private companion object {
        const val REMOVE_TITLE_KEY = "lbl.hierarchy.remove_component_title"
    }

    fun componentName(cmp: Component) {
        ImGui.text(stringManager.getString("lbl.name"))
        ImGui.sameLine()
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())

        val name = ImString(cmp.name, 128)
        val flags = ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
        if (ImGui.inputText("##name_input" + cmp.uId, name, flags)) {
            eventSystem.publish(RenameComponent(cmp, name.get()))
        }

        ImGui.spacing()
        ImGui.text("${stringManager.getString("lbl.type")}: ${cmp.javaClass.simpleName}")
        ImGui.spacing()

        ImGui.popItemWidth()
    }
}
