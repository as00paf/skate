package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.ecs.Scene
import imgui.type.ImBoolean

/**
 * Interface for editor windows that don't require a Scene parameter.
 */
interface IWindow {
    fun imgui(pOpen: ImBoolean? = null)
}

/**
 * Interface for editor windows that require a Scene parameter.
 */
interface IWindowWithScene {
    fun imgui(scene: Scene)
}
