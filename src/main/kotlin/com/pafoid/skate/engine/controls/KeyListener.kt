package com.pafoid.skate.engine.controls

import org.lwjgl.glfw.GLFW.GLFW_PRESS
import org.lwjgl.glfw.GLFW.GLFW_RELEASE

object KeyListener {
    fun get(): KeyListener = this

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

    fun isKeyPressed(key: Int):Boolean = keyPressed[key]

    fun keyBeginPress(key: Int): Boolean {
        return  keyBeginPressed[key]
    }
}