package com.pafoid.skate.engine.input.listeners

import imgui.ImGui
import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

class KeyListener {
    private var keyPressed = BooleanArray(350)
    private var keyBeginPressed = BooleanArray(350)

    fun keyCallback(window: Long, key: Int, scanCode: Int, action: Int, mods: Int) {
        if(key > -1){
            if(action == GLFW_PRESS) {
                keyPressed[key] = true
                keyBeginPressed[key] = true
            } else if(action == GLFW_RELEASE){
                keyPressed[key] = false
                keyBeginPressed[key] = false
            }
        }
    }

    fun endFrame() {
        keyBeginPressed.fill(false)
    }

    fun isKeyPressed(key: Int): Boolean = if (key < keyPressed.size) keyPressed[key] && !ImGui.getIO().wantCaptureKeyboard else false

    fun keyBeginPress(key: Int): Boolean {
        return if (key < keyBeginPressed.size) keyBeginPressed[key] && !ImGui.getIO().wantCaptureKeyboard else false
    }
}