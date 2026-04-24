package com.pafoid.skate.app

interface Workspace {
    fun init(glfwWindow: Long)
    fun update(dt: Float)
    fun handleInputs()
}