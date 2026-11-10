package com.pafoid.skate.editor.imgui

import com.pafoid.skate.engine.input.InputProvider

/**
 * Installs and removes the hooks through which engine input asks ImGui whether it owns the keyboard or mouse (GUI-14).
 * The hooks read ImGui IO, which ends the JVM once the context is gone, so [reset] must run before the context is
 * destroyed (GUI-16).
 */
internal object ImGuiCaptureHooks {
    fun install(input: InputProvider, keyboard: () -> Boolean, mouse: () -> Boolean) {
        input.keyListener.captureKeyboard = keyboard
        input.mouseListener.captureMouse = mouse
    }

    fun reset(input: InputProvider) {
        input.keyListener.captureKeyboard = { false }
        input.mouseListener.captureMouse = { false }
    }
}
