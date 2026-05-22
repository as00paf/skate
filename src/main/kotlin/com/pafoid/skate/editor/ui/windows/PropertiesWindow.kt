package com.pafoid.skate.editor.ui.windows

import com.pafoid.skate.editor.events.ViewportAction.AddComponent
import com.pafoid.skate.editor.events.ViewportAction.RemoveComponent
import com.pafoid.skate.editor.events.ViewportAction.RenameGameObject
import com.pafoid.skate.editor.events.ViewportAction.SetGameObjectEnabled
import com.pafoid.skate.editor.imgui.IWindow
import com.pafoid.skate.editor.imgui.data.Icons
import com.pafoid.skate.editor.systems.StringManager
import com.pafoid.skate.engine.core.EventSystem
import com.pafoid.skate.engine.ecs.GameObject
import com.pafoid.skate.engine.ecs.SceneManager
import com.pafoid.skate.engine.ecs.components.AudioComponent
import com.pafoid.skate.engine.ecs.components.Component
import com.pafoid.skate.engine.ecs.components.ComponentType
import com.pafoid.skate.engine.ecs.components.RenderComponent
import com.pafoid.skate.engine.ecs.components.Transform
import com.pafoid.skate.engine.getComponent
import com.pafoid.skate.engine.physics3d.components.BoxCollider3D
import com.pafoid.skate.engine.physics3d.components.CylinderCollider3D
import com.pafoid.skate.engine.physics3d.components.RigidBody3D
import imgui.ImGui
import imgui.flag.ImGuiInputTextFlags
import imgui.type.ImBoolean
import imgui.type.ImString

class PropertiesWindow(
    private val stringManager: StringManager,
    private val sceneManager: SceneManager,
    private val eventSystem: EventSystem,
) : IWindow {
    private val searchString = ImString(128)
    private var selectedGameObject: GameObject? = null

    override fun imgui(pOpen: ImBoolean?) {
        // Get selected object from ViewModel instead of direct scene query
        selectedGameObject = sceneManager.currentScene?.selectedGameObject
        
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

            val components = go.getAllComponents().filter { it.getName().contains(searchString.get(), true) }
            components.forEachIndexed { index, component ->
                renderComponentWithContextMenu(go, component, index)
            }

            contextualMenu(go)
        }
        ImGui.end()
    }
    
    private fun renderComponentWithContextMenu(go: GameObject, component: Component, index: Int) {
        val headerLabel = component.getName()
        
        if (ImGui.collapsingHeader(headerLabel)) {
            component.imgui()
        }

        if (ImGui.beginPopupContextItem("${component.javaClass.simpleName}_context")) {
            if (ImGui.menuItem("${Icons.COPY} ${stringManager.getString("context.properties.copy_component")}")) {
                // Future enhancement: Copy component to clipboard for pasting to other objects
            }
            if (ImGui.menuItem("${Icons.TRASH} ${stringManager.getString("context.properties.remove_component")}")) {
                // Don't allow removing Transform component (core component)
                if (component !is Transform) {
                    componentTypeOf(component)?.let { componentType ->
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
            val componentsToAdd = listOf(
                AudioComponent::class,
                BoxCollider3D::class,
                CylinderCollider3D::class,
                RenderComponent::class,
                RigidBody3D::class,
                Transform::class,
            )
            componentsToAdd.forEach { type ->
                val name = type.simpleName ?: ""
                if (searchString.get().isEmpty() || name.contains(searchString.get(), ignoreCase = true)) {
                    if (go.getComponent(type) == null) {
                        if (ImGui.menuItem(name)) {
                            componentTypeOf(type)?.let { componentType ->
                                eventSystem.publish(AddComponent(go, componentType))
                            }
                            ImGui.closeCurrentPopup()
                        }
                    }
                }
            }
            ImGui.endPopup()
        }
    }

    private fun componentTypeOf(component: Component): ComponentType? = when (component) {
        is AudioComponent -> ComponentType.AUDIO
        is BoxCollider3D -> ComponentType.BOX_COLLIDER_3D
        is CylinderCollider3D -> ComponentType.CYLINDER_COLLIDER_3D
        is RenderComponent -> ComponentType.RENDER
        is RigidBody3D -> ComponentType.RIGID_BODY_3D
        is Transform -> ComponentType.TRANSFORM
        else -> null
    }

    private fun componentTypeOf(type: kotlin.reflect.KClass<out Component>): ComponentType? = when (type) {
        AudioComponent::class -> ComponentType.AUDIO
        BoxCollider3D::class -> ComponentType.BOX_COLLIDER_3D
        CylinderCollider3D::class -> ComponentType.CYLINDER_COLLIDER_3D
        RenderComponent::class -> ComponentType.RENDER
        RigidBody3D::class -> ComponentType.RIGID_BODY_3D
        Transform::class -> ComponentType.TRANSFORM
        else -> null
    }
}
