package com.pafoid.skate.editor.imgui

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiStyleVar
import imgui.type.ImBoolean
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

/**
 * A utility object providing customized ImGui widgets for the editor, such as vector controls and color pickers.
 * This helps maintain a consistent look and feel across the editor's UI.
 */
object MImGui {

    private var uniformScaling = true

    private const val DEFAULT_COLUMN_WIDTH = 220f
    private const val SENSIBILITY = 0.01f
    const val SENSIBILITY_SCALE = 0.005f
    const val SENSIBILITY_ROTATION = 0.1f

    /**
     * Draws a customized control for editing a [org.joml.Vector2f].
     *
     * @param label The label to display for the control.
     * @param values The vector to be edited.
     * @param resetValue The value to set when a component's reset button is clicked.
     * @param columnWidth The width of the label column.
     * @param sens The sensitivity of the drag float controls.
     */
    fun drawVec2Control(label: String, values: Vector2f, resetValue: Float = 0f, columnWidth: Float = DEFAULT_COLUMN_WIDTH, sens: Float = SENSIBILITY) {
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, columnWidth)
        ImGui.text(label)
        ImGui.nextColumn()

        val lineHeight = ImGui.getFontSize() + ImGui.getStyle().framePaddingY * 2f
        val buttonSizeX = lineHeight + 3
        val buttonSizeY = lineHeight

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)

        val widthEach = (ImGui.calcItemWidth() - (buttonSizeX * 2f)) / 2f

        // X
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.1f, 0.15f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.2f, 0.2f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.1f, 0.15f, 1.0f)
        if (ImGui.button("X", buttonSizeX, buttonSizeY)) {
            values.x = resetValue
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()
        val vecValuesX = floatArrayOf(values.x)
        ImGui.dragFloat("##x", vecValuesX, sens)
        values.x = vecValuesX[0]
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Y
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.7f, 0.2f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.8f, 0.3f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.2f, 1.0f)
        if (ImGui.button("Y", buttonSizeX, buttonSizeY)) {
            values.y = resetValue
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()
        val vecValuesY = floatArrayOf(values.y)
        ImGui.dragFloat("##y", vecValuesY, sens)
        values.y = vecValuesY[0]
        ImGui.popItemWidth()

        ImGui.nextColumn()

        ImGui.popStyleVar()
        ImGui.columns(1)
        ImGui.popID()
    }

    /**
     * Draws a labeled drag float control.
     *
     * @param label The label for the control.
     * @param value The initial float value.
     * @return The modified float value.
     */
    fun dragFloat(label: String, value: Float): Float {
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val valArray = floatArrayOf(value)
        ImGui.dragFloat("##dragFloat", valArray, 0.1f)

        ImGui.columns(1)
        ImGui.popID()

        return valArray[0]
    }

    /**
     * Draws a labeled drag int control.
     *
     * @param label The label for the control.
     * @param value The initial integer value.
     * @return The modified integer value.
     */
    fun dragInt(label: String, value: Int): Int {
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val valArray = intArrayOf(value)
        ImGui.dragInt("##dragInt", valArray, 1f)

        ImGui.columns(1)
        ImGui.popID()

        return valArray[0]
    }

    /**
     * Draws a customized control for editing a [org.joml.Vector3f].
     *
     * @param label The label to display for the control.
     * @param values The vector to be edited.
     * @param resetValue The value to set when a component's reset button is clicked.
     * @param columnWidth The width of the label column.
     * @param sens The sensitivity of the drag float controls.
     */
    fun drawVec3Control(label: String, values: Vector3f, resetValue: Float = 0f, columnWidth: Float = DEFAULT_COLUMN_WIDTH, sens: Float = SENSIBILITY) {
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, columnWidth)
        ImGui.text(label)
        ImGui.nextColumn()

        val lineHeight = ImGui.getFontSize() + ImGui.getStyle().framePaddingY * 2f
        val buttonSizeX = lineHeight + 3
        val buttonSizeY = lineHeight

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)

        val widthEach = (ImGui.calcItemWidth() - (buttonSizeX * 3f)) / 3f

        // X
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.1f, 0.15f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.2f, 0.2f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.1f, 0.15f, 1.0f)
        if (ImGui.button("X", buttonSizeX, buttonSizeY)) {
            values.x = resetValue
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()
        val vecValuesX = floatArrayOf(values.x)
        ImGui.dragFloat("##x", vecValuesX, sens)
        values.x = vecValuesX[0]
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Y
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.7f, 0.2f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.8f, 0.3f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.2f, 1.0f)
        if (ImGui.button("Y", buttonSizeX, buttonSizeY)) {
            values.y = resetValue
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()
        val vecValuesY = floatArrayOf(values.y)
        ImGui.dragFloat("##y", vecValuesY, sens)
        values.y = vecValuesY[0]
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Z
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.25f, 0.8f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.2f, 0.35f, 0.9f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.1f, 0.25f, 0.8f, 1.0f)
        if (ImGui.button("Z", buttonSizeX, buttonSizeY)) {
            values.z = resetValue
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()
        val vecValuesZ = floatArrayOf(values.z)
        ImGui.dragFloat("##z", vecValuesZ, sens)
        values.z = vecValuesZ[0]
        ImGui.popItemWidth()

        ImGui.nextColumn()

        ImGui.popStyleVar()
        ImGui.columns(1)
        ImGui.popID()
    }

    /**
     * Draws a customized control for editing a [Vector3f] representing a transform (e.g., scale).
     * Includes an option for uniform scaling.
     *
     * @param label The label for the control.
     * @param values The vector to be edited.
     * @param resetValue The value to set when a component's reset button is clicked.
     * @param sens The sensitivity of the drag float controls.
     */
    fun drawVec3TransformControl(label: String, values: Vector3f, resetValue: Float = 0f, sens: Float = SENSIBILITY) {
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COLUMN_WIDTH)
        ImGui.text(label)

        if (label == "Scale") {
            ImGui.sameLine()
            val imBool = ImBoolean(uniformScaling)
            if (ImGui.checkbox("Uniform", imBool)) {
                uniformScaling = imBool.get()
            }
        }

        ImGui.nextColumn()

        val lineHeight = ImGui.getFontSize() + ImGui.getStyle().framePaddingY * 2f
        val buttonSizeX = lineHeight + 3
        val buttonSizeY = lineHeight

        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing, 0f, 0f)

        val widthEach = (ImGui.calcItemWidth() - (buttonSizeX * 3f)) / 3f

        // X
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.1f, 0.15f, 1.0f)
        if (ImGui.button("X", buttonSizeX, buttonSizeY)) {
            values.x = resetValue
            if (uniformScaling && label == "Scale") {
                values.y = resetValue
                values.z = resetValue
            }
        }
        ImGui.popStyleColor(1)

        ImGui.sameLine()
        val vecValuesX = floatArrayOf(values.x)
        if (ImGui.dragFloat("##x", vecValuesX, sens)) {
            if (uniformScaling && label == "Scale") {
                val diff = vecValuesX[0] - values.x
                values.x = vecValuesX[0]
                values.y += diff
                values.z += diff
            } else {
                values.x = vecValuesX[0]
            }
        }
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Y
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.7f, 0.2f, 1.0f)
        if (ImGui.button("Y", buttonSizeX, buttonSizeY)) {
            values.y = resetValue
            if (uniformScaling && label == "Scale") {
                values.x = resetValue
                values.z = resetValue
            }
        }
        ImGui.popStyleColor(1)

        ImGui.sameLine()
        val vecValuesY = floatArrayOf(values.y)
        if (ImGui.dragFloat("##y", vecValuesY, sens)) {
            if (uniformScaling && label == "Scale") {
                val diff = vecValuesY[0] - values.y
                values.y = vecValuesY[0]
                values.x += diff
                values.z += diff
            } else {
                values.y = vecValuesY[0]
            }
        }
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Z
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.1f, 0.25f, 0.8f, 1.0f)
        if (ImGui.button("Z", buttonSizeX, buttonSizeY)) {
            values.z = resetValue
            if (uniformScaling && label == "Scale") {
                values.x = resetValue
                values.y = resetValue
            }
        }
        ImGui.popStyleColor(1)

        ImGui.sameLine()
        val vecValuesZ = floatArrayOf(values.z)
        if (ImGui.dragFloat("##z", vecValuesZ, sens)) {
            if (uniformScaling && label == "Scale") {
                val diff = vecValuesZ[0] - values.z
                values.z = vecValuesZ[0]
                values.x += diff
                values.y += diff
            } else {
                values.z = vecValuesZ[0]
            }
        }
        ImGui.popItemWidth()

        ImGui.nextColumn()

        ImGui.popStyleVar()
        ImGui.columns(1)
        ImGui.popID()
    }

    /**
     * Draws a color picker for a [org.joml.Vector4f] (RGBA).
     *
     * @param label The label for the color picker.
     * @param color The color vector to be edited.
     * @return True if the color was changed, false otherwise.
     */
    fun colorPicker4(label: String, color: Vector4f): Boolean {
        var res = false
        ImGui.pushID(label)
        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val imColor = floatArrayOf(color.x, color.y, color.z, color.w)
        if (ImGui.colorEdit4("##colorPicker", imColor)) {
            color.set(imColor[0], imColor[1], imColor[2], imColor[3])
            res = true
        }

        ImGui.columns(1)
        ImGui.popID()

        return res
    }

    /**
     * Draws a color picker for a [Vector3f] (RGB).
     *
     * @param label The label for the color picker.
     * @param color The color vector to be edited.
     * @return True if the color was changed, false otherwise.
     */
    fun colorPicker3(label: String, color: Vector3f): Boolean {
        var res = false
        ImGui.pushID(label)
        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val imColor = floatArrayOf(color.x, color.y, color.z)

        if (ImGui.colorEdit3("##colorPicker3", imColor)) {
            color.set(imColor[0], imColor[1], imColor[2])
            res = true
        }

        ImGui.columns(1)
        ImGui.popID()

        return res
    }
}