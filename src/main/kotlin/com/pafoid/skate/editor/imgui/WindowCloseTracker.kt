package com.pafoid.skate.editor.imgui

import java.util.IdentityHashMap

/** Calls [EditorWindow.onClosed] once for each window that was open at the previous [update] and is closed now. */
class WindowCloseTracker {
    private val wasOpen = IdentityHashMap<EditorWindow, Boolean>()

    fun update(windows: List<EditorWindow>) {
        windows.forEach { window ->
            val open = window.isOpen.get()
            if (wasOpen[window] == true && !open) window.onClosed()
            wasOpen[window] = open
        }
    }
}
