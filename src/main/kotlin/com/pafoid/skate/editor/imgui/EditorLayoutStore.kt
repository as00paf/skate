package com.pafoid.skate.editor.imgui

import java.io.File

/**
 * Persists the editor layout: the ImGui ini (dock nodes, window geometry, viewports)
 * and a sidecar file listing which windows were open.
 */
class EditorLayoutStore(
    val iniFile: File,
    private val openWindowsFile: File,
) {
    fun hasSavedDockLayout(): Boolean = iniFile.isFile && iniFile.length() > 0

    fun saveOpenWindows(names: Collection<String>) {
        runCatching { openWindowsFile.writeText(names.joinToString("\n")) }
    }

    /** Returns the saved open window names, or null when nothing has been saved. */
    fun loadOpenWindows(): Set<String>? {
        if (!openWindowsFile.isFile) return null
        val lines = runCatching { openWindowsFile.readLines() }.getOrNull() ?: return null
        return lines.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun clear() {
        iniFile.delete()
        openWindowsFile.delete()
    }

    companion object {
        const val OPEN_WINDOWS_FILE = "imgui.windows"
    }
}
