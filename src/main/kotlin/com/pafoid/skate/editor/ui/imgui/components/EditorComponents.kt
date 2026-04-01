package com.pafoid.skate.editor.ui.imgui.components

import imgui.ImGui
import imgui.ImVec2
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString

/**
 * Reusable ImGui components for the editor UI.
 * 
 * This object provides commonly-used UI components that can be shared
 * across all editor windows, ensuring consistent styling and behavior.
 * 
 * ## Usage
 * 
 * ```kotlin
 * class MyWindow : IWindowLifecycle {
 *     override fun onRender() {
 *         EditorComponents.iconButton(
 *             icon = Icons.PLUS,
 *             tooltip = "Add New",
 *             onClick = { addItem() }
 *         )
 *         
 *         EditorComponents.labeledSeparator("Settings")
 *         
 *         EditorComponents.propertyField("Name", name)
 *     }
 * }
 * ```
 */
object EditorComponents {
    
    /**
     * Renders a button with an icon and optional tooltip.
     * 
     * @param icon The icon text (e.g., Icons.PLUS, Icons.TRASH)
     * @param size Button size in pixels (default: 30f)
     * @param tooltip Optional tooltip to show on hover
     * @param active Whether the button should appear in active state (green highlight)
     * @param disabled Whether the button should appear disabled
     * @param onClick Callback invoked when button is clicked
     * @return true if the button was clicked this frame
     */
    fun iconButton(
        icon: String,
        size: Float = 30f,
        tooltip: String? = null,
        active: Boolean = false,
        disabled: Boolean = false,
        onClick: () -> Unit
    ): Boolean {
        // Push disabled style if needed
        if (disabled) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.4f, 0.4f, 0.5f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.5f, 0.5f, 0.5f)
        } else if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
        }
        
        val clicked = ImGui.button(icon, size, size)
        
        // Pop style colors
        when {
            disabled -> ImGui.popStyleColor(3)
            active -> ImGui.popStyleColor(1)
        }
        
        // Show tooltip if provided
        if (tooltip != null && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip)
        }
        
        // Invoke callback if clicked
        if (clicked && !disabled) {
            onClick()
        }
        
        return clicked
    }
    
    /**
     * Renders a labeled separator using ImGui's separatorText.
     * 
     * @param label The label text to display
     */
    fun labeledSeparator(label: String) {
        ImGui.separatorText(label)
    }
    
    /**
     * Renders a simple separator with optional spacing.
     * 
     * @param spacing Pixels of spacing before the separator (default: 4f)
     */
    fun separator(spacing: Float = 4f) {
        if (spacing > 0) {
            ImGui.dummy(0f, spacing)
        }
        ImGui.separator()
    }
    
    /**
     * Renders a property field with label and value.
     * 
     * @param label The property label
     * @param value The current value (string)
     * @param onChange Callback when value changes
     */
    fun propertyField(
        label: String,
        value: String,
        onChange: (String) -> Unit
    ) {
        ImGui.pushID(label)
        
        ImGui.columns(2, "##${label}_cols", false)
        
        ImGui.setColumnWidth(0, 120f)
        ImGui.text(label)
        
        ImGui.nextColumn()
        
        ImGui.pushItemWidth(-1f)
        val imValue = ImString(value)
        if (ImGui.inputText("##${label}_input", imValue)) {
            onChange(imValue.get())
        }
        ImGui.popItemWidth()
        
        ImGui.columns(1)
        
        ImGui.popID()
    }
    
    /**
     * Renders a property field with a float value.
     * 
     * @param label The property label
     * @param value The current value
     * @param format Display format (default: "%.2f")
     * @param speed Change speed for dragging (default: 1.0f)
     * @param onChange Callback when value changes
     */
    fun propertyField(
        label: String,
        value: Float,
        format: String = "%.2f",
        speed: Float = 1.0f,
        onChange: (Float) -> Unit
    ) {
        ImGui.pushID(label)
        
        ImGui.columns(2, "##${label}_cols", false)
        
        ImGui.setColumnWidth(0, 120f)
        ImGui.text(label)
        
        ImGui.nextColumn()
        
        ImGui.pushItemWidth(-1f)
        val floatValue = floatArrayOf(value)
        if (ImGui.sliderFloat("##${label}_input", floatValue, -Float.MAX_VALUE, Float.MAX_VALUE, format)) {
            onChange(floatValue[0])
        }
        ImGui.popItemWidth()
        
        ImGui.columns(1)
        
        ImGui.popID()
    }
    
    /**
     * Renders a property field with a boolean checkbox.
     * 
     * @param label The property label
     * @param value The current value
     * @param onChange Callback when value changes
     */
    fun propertyField(
        label: String,
        value: Boolean,
        onChange: (Boolean) -> Unit
    ) {
        ImGui.pushID(label)
        
        val boolValue = imgui.type.ImBoolean(value)
        if (ImGui.checkbox(label, boolValue)) {
            onChange(boolValue.get())
        }
        
        ImGui.popID()
    }
    
    /**
     * Renders a header section with optional background.
     * 
     * @param title The header title
     * @param onSelect Callback when header is clicked (optional)
     */
    fun header(
        title: String,
        onSelect: (() -> Unit)? = null
    ) {
        ImGui.pushStyleColor(ImGuiCol.Header, 0.2f, 0.2f, 0.2f, 1f)
        ImGui.pushStyleColor(ImGuiCol.HeaderHovered, 0.3f, 0.3f, 0.3f, 1f)
        ImGui.pushStyleColor(ImGuiCol.HeaderActive, 0.4f, 0.4f, 0.4f, 1f)
        
        val clicked = ImGui.selectable(title, false, imgui.flag.ImGuiSelectableFlags.SpanAllColumns, 0f, 0f)
        
        ImGui.popStyleColor(3)
        
        if (clicked && onSelect != null) {
            onSelect()
        }
    }
    
    /**
     * Renders a small help icon with tooltip.
     * 
     * @param tooltip The help text to display
     */
    fun helpIcon(tooltip: String) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
        ImGui.text("(?)")
        ImGui.popStyleColor()
        
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip)
        }
    }
    
    /**
     * Renders a section with collapsible header.
     * 
     * @param title The section title
     * @param defaultOpen Whether the section is open by default
     * @param content The content to render inside the section
     */
    fun section(
        title: String,
        defaultOpen: Boolean = true,
        content: () -> Unit
    ) {
        val flags = imgui.flag.ImGuiTreeNodeFlags.FramePadding or 
                    imgui.flag.ImGuiTreeNodeFlags.SpanAvailWidth
        
        if (defaultOpen) {
            if (ImGui.treeNodeEx(title, flags)) {
                content()
                ImGui.treePop()
            }
        } else {
            if (ImGui.treeNodeEx(title, flags or imgui.flag.ImGuiTreeNodeFlags.DefaultOpen)) {
                content()
                ImGui.treePop()
            }
        }
    }
    
    /**
     * Renders a toolbar button group container.
     * 
     * @param content The buttons to render inside the group
     */
    fun toolbarGroup(content: () -> Unit) {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 2f, 2f)
        content()
        ImGui.popStyleVar()
        
        ImGui.sameLine()
        ImGui.separator()
        ImGui.sameLine()
    }
    
    /**
     * Renders text with a custom color.
     * 
     * @param text The text to display
     * @param r Red component (0-1)
     * @param g Green component (0-1)
     * @param b Blue component (0-1)
     * @param a Alpha component (0-1, default: 1f)
     */
    fun coloredText(
        text: String,
        r: Float,
        g: Float,
        b: Float,
        a: Float = 1f
    ) {
        ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
        ImGui.text(text)
        ImGui.popStyleColor()
    }
    
    /**
     * Renders a warning message with yellow/orange color.
     * 
     * @param text The warning text
     */
    fun warningText(text: String) {
        coloredText(text, 1f, 0.8f, 0f)
    }
    
    /**
     * Renders an error message with red color.
     * 
     * @param text The error text
     */
    fun errorText(text: String) {
        coloredText(text, 1f, 0.3f, 0.3f)
    }
    
    /**
     * Renders a success message with green color.
     * 
     * @param text The success text
     */
    fun successText(text: String) {
        coloredText(text, 0.3f, 1f, 0.3f)
    }
}
