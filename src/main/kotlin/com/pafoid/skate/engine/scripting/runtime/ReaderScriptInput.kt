package com.pafoid.skate.engine.scripting.runtime

import com.pafoid.skate.engine.input.InputReader
import com.pafoid.skate.engine.scripting.api.ScriptInput

/** Exposes the engine [InputReader] to scripts. */
class ReaderScriptInput(private val reader: InputReader) : ScriptInput {
    private val lastDown = HashMap<Int, Boolean>()

    override fun isKeyDown(key: Int): Boolean = reader.isKeyDown(key).also { lastDown[key] = it }

    override fun isKeyPressed(key: Int): Boolean = reader.wasKeyPressed(key)

    /** The reader has no release edge, so this compares with the state seen on the previous query of the key. */
    override fun isKeyReleased(key: Int): Boolean {
        val was = lastDown[key] ?: false
        val now = reader.isKeyDown(key)
        lastDown[key] = now
        return was && !now
    }

    override fun isMouseButtonDown(button: Int): Boolean = reader.isMouseButtonDown(button)

    override fun action(name: String): Float = reader.actionValue(name)
}
