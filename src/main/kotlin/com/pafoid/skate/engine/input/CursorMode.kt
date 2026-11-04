package com.pafoid.skate.engine.input

import org.lwjgl.glfw.GLFW

/** How the OS cursor behaves over the game window. [CAPTURED] is the mouse-look mode (hidden, locked, unbounded deltas). */
enum class CursorMode(val glfwValue: Int) {
    NORMAL(GLFW.GLFW_CURSOR_NORMAL),
    HIDDEN(GLFW.GLFW_CURSOR_HIDDEN),
    CAPTURED(GLFW.GLFW_CURSOR_DISABLED);

    companion object {
        fun fromGlfw(value: Int): CursorMode = entries.firstOrNull { it.glfwValue == value } ?: NORMAL
    }
}
