package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ViewportAction.AddComponent
import com.pafoid.skate.editor.events.ViewportAction.RemoveComponent
import com.pafoid.skate.editor.events.ViewportAction.RenameComponent
import com.pafoid.skate.editor.events.ViewportAction.RenameGameObject
import com.pafoid.skate.editor.events.ViewportAction.SetComponentEnabled
import com.pafoid.skate.editor.events.ViewportAction.SetGameObjectEnabled
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.components.imgui
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.engine.core.Engine
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.core.LoggerService
import com.pafoid.skate.engine.core.StringManager
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImBoolean
import imgui.type.ImString

class PropertiesWindow(
    private val stringManager: StringManager,
    private val engine: Engine,
    private val eventSystem: EventSystem,
    private val logger: LoggerService
) : IWindow {

    private val searchString = ImString(128)
    private var selectedGameObject: GameObject? = null

    override fun imgui(pOpen: ImBoolean?) {
        selectedGameObject = engine.sceneManager.currentScene?.selectedGameObject ?: engine.sceneManager.currentScene
        
        ImGui.begin(stringManager.getString("window.properties"), pOpen)
        selectedGameObject?.let { go ->
            enabledCheckbox(go)
            ImGui.spacing()
            objectName(go)
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            searchBar()
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()

            val components = go.getAllComponents().filter {
                it.name.contains(
                    searchString.get(),
                    true
                ) || it.javaClass.simpleName.contains(searchString.get())
            }
            components.sortedBy { it.javaClass.simpleName }.forEachIndexed { index, component ->
                renderComponentWithContextMenu(go, component, index)
            }

            contextualMenu(go)
        }
        ImGui.end()
    }
    
    private fun renderComponentWithContextMenu(go: GameObject, component: Component, index: Int) {
        val headerLabel = component.javaClass.simpleName
        
        if (ImGui.collapsingHeader(headerLabel)) {
            enabledCheckbox(component)
            ImGui.spacing()
            componentName(component)
            ImGui.spacing()
            ImGui.separator()
            ImGui.spacing()
            component.imgui(stringManager, logger)
        }

        if (ImGui.beginPopupContextItem("${component.javaClass.simpleName}_context")) {
            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.properties.copy_component")}")) {
                // Future enhancement: Copy component to clipboard for pasting to other objects
            }
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.properties.remove_component")}")) {
                // Don't allow removing Transform component (core component)
                if (component !is Transform) {
                    component.type?.let { componentType ->
                        eventSystem.publish(RemoveComponent(go, componentType))
                    }
                }
            }
            ImGui.separator()
            if (ImGui.menuItem("${Icons.ARROW_ROTATE} ${stringManager.getString("context.properties.reset_to_default")}")) {
                // Future enhancement: Reset component properties to default values
            }
            ImGui.endPopup()
        }
    }
    
    private fun enabledCheckbox(go: GameObject) {
        val isEnabled = ImBoolean(go.isEnabled)
        if (ImGui.checkbox("##enabled_checkbox", isEnabled)) {
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
        if (ImGui.inputText("##name_input", name, flags)) {
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
        if (ImGui.isItemHovered()) ImGui.setTooltip(stringManager.getString("tooltip.properties.add_component"))
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
        if (ImGui.checkbox("##cmp_enabled_checkbox", isEnabled)) {
            eventSystem.publish(SetComponentEnabled(cmp, isEnabled.get()))
        }

        ImGui.sameLine()
        ImGui.text(stringManager.getString("lbl.systems.enabled"))
    }

    fun componentName(cmp: Component) {
        ImGui.text(stringManager.getString("lbl.name"))
        ImGui.sameLine()
        ImGui.pushItemWidth(ImGui.getContentRegionAvailX())

        val name = ImString(cmp.name, 128)
        val flags = ImGuiInputTextFlags.EnterReturnsTrue or ImGuiInputTextFlags.AutoSelectAll
        if (ImGui.inputText("##name_input", name, flags)) {
            eventSystem.publish(RenameComponent(cmp, name.get()))
        }
        ImGui.popItemWidth()
    }
}
