package com.pafoid.skate.engine.utils

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import org.joml.Vector2f
import org.joml.Vector3f
import org.joml.Vector4f

object MImGui {

    private const val DEFAULT_COLUMN_WIDTH = 220f
    private const val sensibility = 0.01f
    const val sensibilityScale = 0.005f
    const val sensibilityRotation = 0.1f

    fun drawVec2Control(label: String, values: Vector2f, resetValue: Float = 0f, columnWidth: Float = DEFAULT_COLUMN_WIDTH, sens: Float = sensibility) {
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

    fun drawVec3Control(label: String, values: Vector3f, resetValue: Float = 0f, columnWidth: Float = DEFAULT_COLUMN_WIDTH, sens: Float = sensibility) {
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

    private var uniformScaling = true
    
    fun drawVec3TransformControl(label: String, values: Vector3f, resetValue: Float = 0f, sens: Float = sensibility) {
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COLUMN_WIDTH)
        ImGui.text(label)
        
        if (label == "Scale") {
            ImGui.sameLine()
            val imBool = imgui.type.ImBoolean(uniformScaling)
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

    