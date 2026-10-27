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

    /**
     * Drops stale `DockId` entries of dialogs from the ini (older layouts docked them), keeping size and position.
     * Call before ImGui loads the ini.
     */
    fun removeDockIds(windowTitles: Collection<String>) {
        if (!iniFile.isFile) return
        val text = runCatching { iniFile.readText() }.getOrNull() ?: return
        val cleaned = stripDockIds(text, windowTitles)
        if (cleaned != text) runCatching { iniFile.writeText(cleaned) }
    }

    fun clear() {
        iniFile.delete()
        openWindowsFile.delete()
    }

    companion object {
        fun stripDockIds(ini: String, windowTitles: Collection<String>): String {
            val headers = windowTitles.map { "[Window][$it]" }.toSet()
            var inDialog = false
            val out = ArrayList<String>()
            for (line in ini.split("\n")) {
                val trimmed = line.trimEnd('\r')
                if (trimmed.startsWith("[")) inDialog = trimmed in headers
                if (inDialog && trimmed.startsWith("DockId=")) continue
                out.add(line)
            }
            return out.joinToString("\n")
        }

        const val OPEN_WINDOWS_FILE = "imgui.windows"
    }
}
