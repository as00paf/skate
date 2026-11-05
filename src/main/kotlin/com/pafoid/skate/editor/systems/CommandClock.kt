package com.pafoid.skate.editor.systems

/**
 * Counts command executions, undos and redos (EDT-10). The inspector edit tracker reads it to tell a value changed by
 * a command (already undoable) from one an inspector wrote directly.
 */
object CommandClock {
    @Volatile
    var value: Long = 0L
        private set

    fun tick() {
        value++
    }
}
