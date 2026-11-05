package com.pafoid.skate.editor.shell

import java.io.File

/**
 * Command history of the shell panel, newest last: consecutive duplicates collapse, at most [capacity] entries.
 * Up/Down recall works like a terminal ([previous]/[next]). Persisted one command per line in [file] (UTF-8).
 */
class ShellHistory(private val file: File? = null, private val capacity: Int = 200) {

    private val entries = ArrayList<String>()

    /** Recall cursor: entries.size means "editing a new line". */
    private var cursor = 0

    val items: List<String> get() = entries

    fun load(): ShellHistory {
        val f = file ?: return this
        entries.clear()
        if (f.isFile) runCatching { f.readLines(Charsets.UTF_8) }.getOrDefault(emptyList())
            .map { it.trim() }.filter { it.isNotEmpty() }.forEach { addInternal(it) }
        cursor = entries.size
        return this
    }

    fun save() {
        val f = file ?: return
        runCatching { f.parentFile?.mkdirs(); f.writeText(entries.joinToString("\n", postfix = if (entries.isEmpty()) "" else "\n"), Charsets.UTF_8) }
    }

    /** Records a run command (and saves); resets recall to a new line. */
    fun add(command: String) {
        val c = command.trim()
        if (c.isEmpty()) return
        addInternal(c)
        cursor = entries.size
        save()
    }

    private fun addInternal(c: String) {
        if (entries.lastOrNull() == c) return
        entries.add(c)
        while (entries.size > capacity) entries.removeAt(0)
    }

    /** Older entry (Up), or null when there is none. */
    fun previous(): String? {
        if (cursor == 0 || entries.isEmpty()) return null
        cursor--
        return entries[cursor]
    }

    /** Newer entry (Down); "" when moving past the newest back to a new line, null when already there. */
    fun next(): String? {
        if (cursor >= entries.size) return null
        cursor++
        return if (cursor == entries.size) "" else entries[cursor]
    }

    fun clear() {
        entries.clear()
        cursor = 0
        save()
    }
}
