package com.pafoid.skate.editor.data

import com.pafoid.skate.engine.input.InputBinding
import org.lwjgl.glfw.GLFW

/**
 * EDT-84: the tool windows that have a side-bar button and a shortcut (every window of View > Windows except the Game
 * Viewport, design Q1), and their default keys, pressed with Alt as in IntelliJ (Alt+1 Project … Alt+9 Git,
 * Alt+F12 Terminal). Windows without a default are unbound until the user picks a key in Key Bindings.
 */
object ToolWindowShortcuts {
    val WINDOWS: List<String> = listOf(
        "window.project", "window.hierarchy", "window.properties", "window.audio_inspector",
        "window.asset_browser", "window.asset_store", "window.video_to_animation", "window.animation_editor",
        "window.animator", "window.script_editor",
        "window.console", "window.profiler", "window.command_history", "window.render_graph", "window.shell",
        "window.version_control", "window.help",
    )

    private val DEFAULT_KEYS: Map<String, Int> = mapOf(
        "window.project" to GLFW.GLFW_KEY_1,
        "window.hierarchy" to GLFW.GLFW_KEY_2,
        "window.properties" to GLFW.GLFW_KEY_3,
        "window.console" to GLFW.GLFW_KEY_4,
        "window.asset_browser" to GLFW.GLFW_KEY_5,
        "window.script_editor" to GLFW.GLFW_KEY_6,
        "window.animation_editor" to GLFW.GLFW_KEY_7,
        "window.profiler" to GLFW.GLFW_KEY_8,
        "window.version_control" to GLFW.GLFW_KEY_9,
        "window.shell" to GLFW.GLFW_KEY_F12,
    )

    /** The default key of [window] (with Alt), or -1 (unbound). */
    fun defaultKey(window: String): Int = DEFAULT_KEYS[window] ?: -1

    fun defaults(): Map<String, InputBinding> = WINDOWS.associateWith { InputBinding(keyboardKey = defaultKey(it)) }
}
