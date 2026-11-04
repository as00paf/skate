package com.pafoid.skate.engine.render.shader

import java.io.File

/**
 * Polls a shader source directory and reports changes. GL-free: the caller recompiles on its own (GL) thread.
 * Only active when the directory exists, i.e. in editor/dev runs from the repository.
 */
class ShaderHotReloader(
    private val root: File,
    private val intervalMs: Long = 500,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onChanged: (changed: List<String>) -> Unit,
) {
    private val stamps = HashMap<String, Long>()
    private var lastPoll = Long.MIN_VALUE
    val isActive: Boolean get() = root.isDirectory

    init {
        if (isActive) scan()
    }

    /** Call once per frame; does the file system scan at most every [intervalMs]. */
    fun poll() {
        if (!isActive) return
        val now = clock()
        if (lastPoll != Long.MIN_VALUE && now - lastPoll < intervalMs) return
        lastPoll = now
        val changed = scan()
        if (changed.isNotEmpty()) onChanged(changed)
    }

    private fun scan(): List<String> {
        val changed = mutableListOf<String>()
        root.walkTopDown().filter { it.isFile && it.extension == "glsl" }.forEach { f ->
            val name = f.relativeTo(root).invariantSeparatorsPath
            val stamp = f.lastModified()
            val old = stamps.put(name, stamp)
            if (old != null && old != stamp) changed += name
        }
        return changed
    }
}
