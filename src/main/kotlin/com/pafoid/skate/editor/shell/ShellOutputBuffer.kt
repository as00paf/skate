package com.pafoid.skate.editor.shell

/** Scrollback of the shell panel: keeps the newest [maxLines] lines (Gradle can print thousands). */
class ShellOutputBuffer(private val maxLines: Int = 5000) {
    private val lines = ArrayDeque<ShellLine>()

    val size: Int get() = lines.size

    operator fun get(index: Int): ShellLine = lines[index]

    fun addAll(newLines: List<ShellLine>) {
        newLines.forEach { lines.addLast(it) }
        while (lines.size > maxLines) lines.removeFirst()
    }

    fun clear() = lines.clear()

    fun text(): String = lines.joinToString("\n") { it.text }
}
