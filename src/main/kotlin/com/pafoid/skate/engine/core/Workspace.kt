package com.pafoid.skate.engine.core

interface Workspace {
    fun init(glfwWindow: Long)
    fun update(dt: Float)
    fun handleInputs()
}