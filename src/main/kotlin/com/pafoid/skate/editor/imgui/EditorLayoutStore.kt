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
    /**
     * True when the ini holds a layout in the current format. Window titles carry stable `###` IDs (EDT-38); an ini
     * written before that names windows by translated text only, so nothing in it matches and the default layout is
     * built instead of leaving every window floating.
     */
    fun hasSavedDockLayout(): Boolean =
        iniFile.isFile && iniFile.length() > 0 && isCurrentFormat(runCatching { iniFile.readText() }.getOrDefault(""))

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
     * [windowIds] are the stable IDs after `###` in the window titles ([EditorWindow.name]); the ini header keeps
     * whatever translated text the title had when it was saved. Call before ImGui loads the ini.
     */
    fun removeDockIds(windowIds: Collection<String>) {
        if (!iniFile.isFile) return
        val text = runCatching { iniFile.readText() }.getOrNull() ?: return
        val cleaned = stripDockIds(text, windowIds)
        if (cleaned != text) runCatching { iniFile.writeText(cleaned) }
    }

    fun clear() {
        iniFile.delete()
        openWindowsFile.delete()
    }

    companion object {
        fun stripDockIds(ini: String, windowIds: Collection<String>): String {
            val suffixes = windowIds.map { "${ImGuiIds.STABLE}$it]" }
            var inDialog = false
            val out = ArrayList<String>()
            for (line in ini.split("\n")) {
                val trimmed = line.trimEnd('\r')
                if (trimmed.startsWith("[")) inDialog = trimmed.startsWith("[Window][") && suffixes.any { trimmed.endsWith(it) }
                if (inDialog && trimmed.startsWith("DockId=")) continue
                out.add(line)
            }
            return out.joinToString("\n")
        }

        /** Whether [ini] has at least one window saved under a stable `###window.` ID. */
        fun isCurrentFormat(ini: String): Boolean = ini.contains("[Window][") && ini.contains("${ImGuiIds.STABLE}window.")

        const val OPEN_WINDOWS_FILE = "imgui.windows"
    }
}
