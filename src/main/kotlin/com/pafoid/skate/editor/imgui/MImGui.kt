package com.pafoid.skate.editor.imgui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiSelectableFlags
import imgui.flag.ImGuiStyleVar
import imgui.flag.ImGuiTableFlags
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * Centralized custom ImGui widgets for the SkateSim editor.
 *
 * Provides:
 * - **Data controls**: vector editors (X/Y/Z with colored reset buttons), drag floats/ints, color pickers
 * - **Semantic text**: warning, error, success, disabled-colored text helpers
 * - **Layout helpers**: icon buttons, collapsible sections, separators, toolbar groups, help icons
 *
 * All two-column layouts use the modern `ImGui.beginTable` API (not the deprecated `ImGui.columns`).
 */
object MImGui {

    private const val DEFAULT_COLUMN_WIDTH = 220f
    private const val SENSIBILITY = 0.01f
    const val SENSIBILITY_SCALE = 0.005f
    const val SENSIBILITY_ROTATION = 0.1f

    // ──────────────────────────────────────────────
    //  DATA CONTROLS (vector, drag, color pickers)
    // ──────────────────────────────────────────────

    /** Draws a two-column table with label and vector2 editor + reset buttons. */
    fun drawVec2Control(label: String, values: Vector2f, resetValue: Float = 0f, columnWidth: Float = DEFAULT_COLUMN_WIDTH, sens: Float = SENSIBILITY) {
        ImGui.pushID(label)

        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, columnWidth)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)

            drawVec2Axis(values, resetValue, sens)

            ImGui.endTable()
        }
        ImGui.popID()
    }

    /** Draws a two-column table with label and vector3 editor + reset buttons. */
    fun drawVec3Control(label: String, values: Vector3f, resetValue: Float = 0f, columnWidth: Float = DEFAULT_COLUMN_WIDTH, sens: Float = SENSIBILITY) {
        ImGui.pushID(label)

        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, columnWidth)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)

            drawVec3Axis(values, resetValue, sens)

            ImGui.endTable()
        }
        ImGui.popID()
    }

    /** Draws a two-column table with label and vector3 transform editor (uniform scaling). */
    fun drawVec3TransformControl(label: String, values: Vector3f, resetValue: Float = 0f, sens: Float = SENSIBILITY, uniformScaling: Boolean = true) {
        ImGui.pushID(label)

        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, DEFAULT_COLUMN_WIDTH)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)

            drawVec3Axis(values, resetValue, sens, uniformScaling, label)

            ImGui.endTable()
        }
        ImGui.popID()
    }

    /** Draws a two-column table with label and drag-float control. */
    fun dragFloat(label: String, value: Float, speed: Float = 0.1f): Float {
        val valArray = floatArrayOf(value)
        ImGui.pushID(label)

        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, DEFAULT_COLUMN_WIDTH)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)

            ImGui.pushItemWidth(-1f)
            ImGui.dragFloat("##dragFloat", valArray, speed)
            ImGui.popItemWidth()

            ImGui.endTable()
        }
        ImGui.popID()

        return valArray[0]
    }

    /** Draws a two-column table with label and drag-int control. */
    fun dragInt(label: String, value: Int): Int {
        val valArray = intArrayOf(value)
        ImGui.pushID(label)

        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, DEFAULT_COLUMN_WIDTH)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)

            ImGui.pushItemWidth(-1f)
            ImGui.dragInt("##dragInt", valArray, 1f)
            ImGui.popItemWidth()

            ImGui.endTable()
        }
        ImGui.popID()

        return valArray[0]
    }

    /** Draws a two-column table with label and RGBA color picker. */
    fun colorPicker4(label: String, color: Vector4f): Boolean {
        var res = false
        ImGui.pushID(label)

        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, DEFAULT_COLUMN_WIDTH)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)

            val imColor = floatArrayOf(color.x, color.y, color.z, color.w)
            if (ImGui.colorEdit4("##colorPicker", imColor)) {
                color.set(imColor[0], imColor[1], imColor[2], imColor[3])
                res = true
            }

            ImGui.endTable()
        }
        ImGui.popID()
        return res
    }

    /** Draws a two-column table with label and RGB color picker. */
    fun colorPicker3(label: String, color: Vector3f): Boolean {
        var res = false
        ImGui.pushID(label)

        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, DEFAULT_COLUMN_WIDTH)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)

            val imColor = floatArrayOf(color.x, color.y, color.z)
            if (ImGui.colorEdit3("##colorPicker3", imColor)) {
                color.set(imColor[0], imColor[1], imColor[2])
                res = true
            }

            ImGui.endTable()
        }
        ImGui.popID()
        return res
    }

    // ──────────────────────────────────────────────
    //  SEMANTIC TEXT helpers
    // ──────────────────────────────────────────────

    /** Renders text with a custom RGBA color. */
    fun coloredText(text: String, r: Float, g: Float, b: Float, a: Float = 1f) {
        ImGui.pushStyleColor(ImGuiCol.Text, r, g, b, a)
        ImGui.text(text)
        ImGui.popStyleColor()
    }

    /** Renders a warning message in yellow/orange. */
    fun warningText(text: String) {
        coloredText(text, 1f, 0.8f, 0f)
    }

    /** Renders an error message in red. */
    fun errorText(text: String) {
        coloredText(text, 1f, 0.3f, 0.3f)
    }

    /** Renders a success message in green. */
    fun successText(text: String) {
        coloredText(text, 0.3f, 0.9f, 0.3f)
    }

    /** Renders disabled/placeholder text in gray. */
    fun textDisabled(text: String) {
        coloredText(text, 0.5f, 0.5f, 0.5f)
    }

    // ──────────────────────────────────────────────
    //  LAYOUT HELPERS
    // ──────────────────────────────────────────────

    /**
     * Renders a square icon button with optional tooltip and active/disabled states.
     *
     * @param icon Icon string (e.g. Icons.PLUS)
     * @param size Button width and height
     * @param tooltip Tooltip on hover
     * @param active Highlight green
     * @param disabled Gray out and prevent click
     * @return true if clicked
     */
    fun iconButton(
        icon: String,
        size: Float = 30f,
        tooltip: String? = null,
        active: Boolean = false,
        disabled: Boolean = false
    ): Boolean {
        if (disabled) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.3f, 0.3f, 0.3f, 0.5f)
            ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.4f, 0.4f, 0.4f, 0.5f)
            ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.5f, 0.5f, 0.5f, 0.5f)
        } else if (active) {
            ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.6f, 0.2f, 1f)
        }

        val clicked = ImGui.button(icon, size, size)

        when {
            disabled -> ImGui.popStyleColor(3)
            active -> ImGui.popStyleColor(1)
        }

        if (tooltip != null && ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip)
        }

        return clicked && !disabled
    }

    /** Renders a labeled separator via ImGui.separatorText. */
    fun labeledSeparator(label: String) {
        ImGui.separatorText(label)
    }

    /** Renders a simple separator with optional leading spacing. */
    fun separator(spacing: Float = 4f) {
        if (spacing > 0) ImGui.dummy(0f, spacing)
        ImGui.separator()
    }

    /**
     * Renders a collapsible tree-node section.
     *
     * @param title Section header
     * @param defaultOpen Whether open by default
     * @param content Lambda rendered when section is open
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

    /** Renders a small "(?)" help icon with a tooltip. */
    fun helpIcon(tooltip: String) {
        ImGui.pushStyleColor(ImGuiCol.Text, 0.5f, 0.5f, 0.5f, 1f)
        ImGui.text("(?)")
        ImGui.popStyleColor()
        if (ImGui.isItemHovered()) {
            ImGui.setTooltip(tooltip)
        }
    }

    /** Renders a toolbar group — tight spacing followed by a separator. */
    fun toolbarGroup(content: () -> Unit) {
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 2f, 2f)
        content()
        ImGui.popStyleVar()
        ImGui.sameLine()
        ImGui.separator()
        ImGui.sameLine()
    }

    // ──────────────────────────────────────────────
    //  ARRAY-BASED helpers (for direct ImGui patterns)
    // ──────────────────────────────────────────────

    /** Two-column labeled drag-float3 for Vector3 data (e.g. gravity). */
    fun dragFloat3(label: String, values: FloatArray, speed: Float = 0.01f): Boolean {
        var changed = false
        ImGui.pushID(label)
        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, DEFAULT_COLUMN_WIDTH)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)
            ImGui.pushItemWidth(-1f)
            changed = ImGui.dragFloat3("##dragFloat3", values, speed)
            ImGui.popItemWidth()
            ImGui.endTable()
        }
        ImGui.popID()
        return changed
    }

    /** Two-column labeled color-edit-3 for RGB colors. */
    fun colorEdit3(label: String, color: FloatArray): Boolean {
        ImGui.pushID(label)
        var changed = false
        if (ImGui.beginTable("##${label}_table", 2, 0, 2f)) {
            ImGui.tableSetupColumn("Label", 0, DEFAULT_COLUMN_WIDTH)
            ImGui.tableSetupColumn("Control")
            ImGui.tableNextRow()
            ImGui.tableSetColumnIndex(0)
            ImGui.text(label)
            ImGui.tableSetColumnIndex(1)
            ImGui.pushItemWidth(-1f)
            changed = ImGui.colorEdit3("##colorEdit3", color)
            ImGui.popItemWidth()
            ImGui.endTable()
        }
        ImGui.popID()
        return changed
    }

    // ──────────────────────────────────────────────
    //  PRIVATE internal helpers
    // ──────────────────────────────────────────────

    private fun drawVec2Axis(values: Vector2f, resetValue: Float, sens: Float) {
        val lineHeight = ImGui.getFontSize() + ImGui.getStyle().framePaddingY * 2f
        val buttonSize = lineHeight + 3f

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)
        val widthEach = (ImGui.calcItemWidth() - (buttonSize * 2f)) / 2f

        ImGui.pushItemWidth(widthEach)
        pushAxisColors(0)
        if (ImGui.button("X", buttonSize, lineHeight)) values.x = resetValue
        ImGui.popStyleColor(3)
        ImGui.sameLine()
        val vx = floatArrayOf(values.x)
        ImGui.dragFloat("##x", vx, sens)
        values.x = vx[0]
        ImGui.popItemWidth()
        ImGui.sameLine()

        ImGui.pushItemWidth(widthEach)
        pushAxisColors(1)
        if (ImGui.button("Y", buttonSize, lineHeight)) values.y = resetValue
        ImGui.popStyleColor(3)
        ImGui.sameLine()
        val vy = floatArrayOf(values.y)
        ImGui.dragFloat("##y", vy, sens)
        values.y = vy[0]
        ImGui.popItemWidth()

        ImGui.popStyleVar()
    }

    private fun drawVec3Axis(values: Vector3f, resetValue: Float, sens: Float, uniformScaling: Boolean = false, label: String = "") {
        val lineHeight = ImGui.getFontSize() + ImGui.getStyle().framePaddingY * 2f
        val buttonSize = lineHeight + 3f

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)
        val widthEach = (ImGui.calcItemWidth() - (buttonSize * 3f)) / 3f

        // X
        ImGui.pushItemWidth(widthEach)
        pushAxisColors(0)
        if (ImGui.button("X", buttonSize, lineHeight)) {
            values.x = resetValue
            if (uniformScaling && label == "Scale") { values.y = resetValue; values.z = resetValue }
        }
        ImGui.popStyleColor(1)
        ImGui.sameLine()
        val vx = floatArrayOf(values.x)
        if (ImGui.dragFloat("##x", vx, sens)) {
            if (uniformScaling && label == "Scale") {
                val diff = vx[0] - values.x
                values.x = vx[0]; values.y += diff; values.z += diff
            } else values.x = vx[0]
        }
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Y
        ImGui.pushItemWidth(widthEach)
        pushAxisColors(1)
        if (ImGui.button("Y", buttonSize, lineHeight)) {
            values.y = resetValue
            if (uniformScaling && label == "Scale") { values.x = resetValue; values.z = resetValue }
        }
        ImGui.popStyleColor(1)
        ImGui.sameLine()
        val vy = floatArrayOf(values.y)
        if (ImGui.dragFloat("##y", vy, sens)) {
            if (uniformScaling && label == "Scale") {
                val diff = vy[0] - values.y
                values.y = vy[0]; values.x += diff; values.z += diff
            } else values.y = vy[0]
        }
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Z
        ImGui.pushItemWidth(widthEach)
        pushAxisColors(2)
        if (ImGui.button("Z", buttonSize, lineHeight)) {
            values.z = resetValue
            if (uniformScaling && label == "Scale") { values.x = resetValue; values.y = resetValue }
        }
        ImGui.popStyleColor(1)
        ImGui.sameLine()
        val vz = floatArrayOf(values.z)
        if (ImGui.dragFloat("##z", vz, sens)) {
            if (uniformScaling && label == "Scale") {
                val diff = vz[0] - values.z
                values.z = vz[0]; values.x += diff; values.y += diff
            } else values.z = vz[0]
        }
        ImGui.popItemWidth()

        ImGui.popStyleVar()
    }

    /** Push 3 style colors for a given axis index: 0=X red, 1=Y green, 2=Z blue. */
    private fun pushAxisColors(axis: Int) {
        when (axis) {
            0 -> {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.1f, 0.15f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.2f, 0.2f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.1f, 0.15f, 1f)
            }
            1 -> {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.7f, 0.2f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.8f, 0.3f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.2f, 1f)
            }
            2 -> {
                ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.25f, 0.8f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.35f, 0.9f, 1f)
                ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.1f, 0.25f, 0.8f, 1f)
            }
        }
    }
}
