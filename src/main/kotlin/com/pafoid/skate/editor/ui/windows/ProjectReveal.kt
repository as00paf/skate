package com.pafoid.skate.editor.ui.windows

import java.io.File

/**
 * Project window selection and "reveal this file" requests (EDT-61), as pure state the window consults while it draws
 * its tree: open the folders above the target, select it, scroll to it once, then stop.
 */
class ProjectReveal {
    /** File to reveal on the next tree draw, normalized; null when there is nothing to do. */
    var target: String? = null
        private set

    /** File shown as selected in the tree (set by a reveal or a click). */
    var selected: String? = null
        private set

    /** True until the window has taken focus for the latest request. */
    var wantsFocus = false
        private set

    fun request(path: String) {
        val normalized = normalize(path)
        target = normalized
        selected = normalized
        wantsFocus = true
    }

    fun select(path: String) {
        selected = normalize(path)
    }

    fun focusTaken() {
        wantsFocus = false
    }

    /** Folders above the target must be forced open this frame. */
    fun shouldOpen(folderPath: String): Boolean = target?.let { isAncestor(folderPath, it) } == true

    fun isTarget(path: String): Boolean = target != null && target == normalize(path)

    fun isSelected(path: String): Boolean = selected != null && selected == normalize(path)

    /** A filter that does not match the target's name would hide it: the window clears it. */
    fun filterHidesTarget(filter: String): Boolean {
        val t = target ?: return false
        return filter.isNotBlank() && !File(t).name.contains(filter.trim(), ignoreCase = true)
    }

    /** Called after one tree draw: the target was revealed, or it is not in the tree; either way stop trying. */
    fun done() {
        target = null
    }

    companion object {
        fun normalize(path: String): String = File(path).absoluteFile.toPath().normalize().toString()

        /** True when [folder] strictly contains [file]. */
        fun isAncestor(folder: String, file: String): Boolean {
            val f = File(normalize(folder)).toPath()
            val p = File(normalize(file)).toPath()
            return p != f && p.startsWith(f)
        }
    }
}
