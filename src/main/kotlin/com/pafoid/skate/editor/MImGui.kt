package com.pafoid.skate.editor

import imgui.ImGui
import imgui.flag.ImGuiCol
import imgui.flag.ImGuiInputTextFlags
import imgui.flag.ImGuiStyleVar
import imgui.type.ImString
import org.joml.Vector2f
import org.joml.Vector4f

object MImGui {

    private const val DEFAULT_COMLUMN_WIDTH = 220f
    private const val sensibility = 0.01f

    fun drawVec2Control(label:String, values: Vector2f, resetValue: Float = 0f, columnWidth:Float = DEFAULT_COMLUMN_WIDTH){
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, columnWidth)
        ImGui.text(label)
        ImGui.nextColumn()

        val lineHeight = ImGui.getFontSize() + ImGui.getStyle().framePaddingY * 2f
        val buttonSize = Vector2f(lineHeight + 3, lineHeight)

        // X value
        ImGui.pushStyleVar(ImGuiStyleVar.ItemSpacing,0f, 0f)

        val widthEach = (ImGui.calcItemWidth() - (buttonSize.x * 2f)) / 2f
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.8f, 0.1f, 0.15f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.9f, 0.2f, 0.2f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.8f, 0.1f, 0.15f, 1.0f)
        if(ImGui.button("X", buttonSize.x, buttonSize.y)) {
            values.x = resetValue
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()
        val vecValuesX = floatArrayOf(values.x)
        ImGui.dragFloat("##x", vecValuesX, sensibility)
        ImGui.popItemWidth()
        ImGui.sameLine()

        // Y value
        ImGui.pushItemWidth(widthEach)
        ImGui.pushStyleColor(ImGuiCol.Button, 0.2f, 0.7f, 0.2f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonHovered, 0.3f, 0.8f, 0.3f, 1.0f)
        ImGui.pushStyleColor(ImGuiCol.ButtonActive, 0.2f, 0.7f, 0.2f, 1.0f)
        if(ImGui.button("Y", buttonSize.x, buttonSize.y)) {
            values.y = resetValue
        }
        ImGui.popStyleColor(3)

        ImGui.sameLine()
        val vecValuesY = floatArrayOf(values.y)
        ImGui.dragFloat("##y", vecValuesY, sensibility)
        ImGui.popItemWidth()
        ImGui.sameLine()

        ImGui.nextColumn()

        values.x = vecValuesX[0]
        values.y = vecValuesY[0]

        ImGui.popStyleVar()
        ImGui.columns(1)
        ImGui.popID()
    }

    fun dragFloat(label:String, value:Float): Float{
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COMLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val valArray = floatArrayOf(value)
        ImGui.dragFloat("##dragFloat", valArray, 0.1f)

        ImGui.columns(1)
        ImGui.popID()

        return valArray[0]
    }

    fun dragInt(label:String, value:Int): Int{
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COMLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val valArray = intArrayOf(value)
        ImGui.dragInt("##dragFloat", valArray, 0.1f)

        ImGui.columns(1)
        ImGui.popID()

        return valArray[0]
    }

    fun colorPicker4(label:String, color: Vector4f): Boolean {
        var res = false
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COMLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val imColor = floatArrayOf(color.x, color.y, color.z, color.w)
        if(ImGui.colorEdit4("##colorPicker",  imColor)) {
            color.set(imColor[0], imColor[1], imColor[2], imColor[3])
            res = true
        }

        ImGui.columns(1)
        ImGui.popID()

        return res
    }

    fun inputText(label: String, initialValue: String): String {
        ImGui.pushID(label)

        ImGui.columns(2)
        ImGui.setColumnWidth(0, DEFAULT_COMLUMN_WIDTH)
        ImGui.text(label)
        ImGui.nextColumn()

        val maxLength = 256
        val outString = ImString(initialValue, maxLength)
        if(ImGui.inputText("##$label", outString, ImGuiInputTextFlags.CallbackCompletion or ImGuiInputTextFlags.EnterReturnsTrue)) {
            ImGui.columns(1)
            ImGui.popID()
            return outString.get()
        }

        ImGui.columns(1)
        ImGui.popID()

        return initialValue
    }
}