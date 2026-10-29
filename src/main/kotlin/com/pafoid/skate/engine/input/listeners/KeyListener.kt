package com.pafoid.skate.engine.input.listeners

import com.pafoid.skate.engine.input.TextInputBuffer
import imgui.ImGui
import org.lwjgl.glfw.GLFW.GLFW_REPEAT
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

class KeyListener {
    val text = TextInputBuffer()
    private var keyPressed = BooleanArray(350)
    private var keyBeginPressed = BooleanArray(350)

    fun keyCallback(window: Long, key: Int, scanCode: Int, action: Int, mods: Int) {
        if(key in keyPressed.indices){
            if(action == GLFW_PRESS) {
                keyPressed[key] = true
                keyBeginPressed[key] = true
            } else if(action == GLFW_REPEAT) {
                text.keyRepeated(key)
            } else if(action == GLFW_RELEASE){
                keyPressed[key] = false
                keyBeginPressed[key] = false
            }
        }
    }

    fun charCallback(window: Long, codePoint: Int) = text.appendCodePoint(codePoint)

    fun endFrame() {
        keyBeginPressed.fill(false)
        text.endFrame()
    }

    fun isKeyPressed(key: Int): Boolean = if (key in keyPressed.indices) keyPressed[key] && !ImGui.getIO().wantCaptureKeyboard else false

    fun keyBeginPress(key: Int): Boolean {
        return if (key in keyBeginPressed.indices) keyBeginPressed[key] && !ImGui.getIO().wantCaptureKeyboard else false
    }
}